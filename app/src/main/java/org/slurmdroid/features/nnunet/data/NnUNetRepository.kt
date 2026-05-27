package org.slurmdroid.features.nnunet.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slurmdroid.core.AppPreferences
import org.slurmdroid.core.Result
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.features.nnunet.domain.FoldProgress
import org.slurmdroid.features.nnunet.domain.NnUNetDataset
import org.slurmdroid.features.nnunet.domain.NnUNetStageStatus
import org.slurmdroid.features.nnunet.domain.NnUNetTrainingConfig
import org.slurmdroid.features.nnunet.domain.NnUNetWorkflow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NnUNetRepository @Inject constructor(
    private val commandExecutor: CommandExecutor,
    private val logParser: NnUNetLogParser,
    private val appPreferences: AppPreferences,
) {
    private val _datasets = MutableStateFlow<List<NnUNetDataset>>(emptyList())
    val datasets: StateFlow<List<NnUNetDataset>> = _datasets.asStateFlow()

    /** Null = env var not resolved yet; blank = resolved but not set and no override configured. */
    private val _resolvedResultsDir = MutableStateFlow<String?>(null)
    val resolvedResultsDir: StateFlow<String?> = _resolvedResultsDir.asStateFlow()

    /** True after the first poll that successfully resolved at least one nnUNet directory. */
    private val _anyDirConfigured = MutableStateFlow<Boolean?>(null)
    val anyDirConfigured: StateFlow<Boolean?> = _anyDirConfigured.asStateFlow()

    suspend fun poll() {
        val resultsDir = resolveResultsDir()
        val rawDir = resolveRawDir()
        val prepDir = resolvePreprocessedDir()

        if (resultsDir != null) _resolvedResultsDir.value = resultsDir

        // Scan all three directories in one command and collect unique dataset names.
        val parts = buildList {
            if (resultsDir != null) add("find \"$resultsDir\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null")
            if (rawDir != null)     add("find \"$rawDir\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null")
            if (prepDir != null)    add("find \"$prepDir\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null")
        }
        _anyDirConfigured.value = parts.isNotEmpty()
        if (parts.isEmpty()) return

        val result = commandExecutor.execute("(${parts.joinToString("; ")}) | sort -u")
        if (result !is Result.Success) return

        val effectiveResultsDir = resultsDir ?: ""
        _datasets.value = result.data.lines()
            .filter { it.isNotBlank() }
            .map { it.substringAfterLast('/') }
            .distinct()
            .sorted()
            .map { name ->
                NnUNetDataset(
                    name = name,
                    resultsPath = if (effectiveResultsDir.isNotBlank()) "$effectiveResultsDir/$name" else "",
                )
            }
    }

    suspend fun listConfigs(dataset: NnUNetDataset): List<String> {
        val result = commandExecutor.execute(
            "find \"${dataset.resultsPath}\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort"
        )
        if (result !is Result.Success) return emptyList()
        return result.data.lines()
            .filter { it.isNotBlank() }
            .map { it.substringAfterLast('/') }
    }

    suspend fun loadProgress(dataset: NnUNetDataset, config: String): List<FoldProgress> {
        val configPath = "${dataset.resultsPath}/$config"
        val cmd = (0..4).joinToString("; ") { fold ->
            "echo '---FOLD $fold---'; cat \"$configPath/fold_$fold/training_log_\"*.txt 2>/dev/null"
        }
        val result = commandExecutor.execute(cmd)
        return if (result is Result.Success) logParser.parse(result.data) else emptyList()
    }

    /**
     * Loads the full pipeline workflow for a dataset in two SSH calls:
     * 1. Check raw/preprocessed/planning/postprocessing presence + list config dirs.
     * 2. Read all training logs across all configs.
     */
    suspend fun loadDatasetWorkflow(dataset: NnUNetDataset): NnUNetWorkflow {
        val rawDir = resolveRawDir()
        val prepDir = resolvePreprocessedDir()

        // Phase 1: check directory/file presence and discover configs
        val stageChecks = buildStageCheckCommand(dataset.name, rawDir, prepDir, dataset.resultsPath)
        val stageResult = commandExecutor.execute(stageChecks)
        val stageOutput = (stageResult as? Result.Success)?.data ?: ""

        val parsed = parseStageOutput(stageOutput)

        if (parsed.configPaths.isEmpty()) {
            return NnUNetWorkflow(
                rawDataStatus = parsed.rawStatus,
                planningStatus = parsed.planStatus,
                preprocessingStatus = parsed.prepStatus,
                preprocessingCurrent = parsed.prepCurrent,
                preprocessingTotal = parsed.prepTotal,
                trainingConfigs = emptyList(),
                postprocessingStatus = parsed.postprocStatus,
            )
        }

        // Phase 2: read all training logs in one command
        val logCmd = parsed.configPaths.joinToString("; ") { configPath ->
            val configName = configPath.substringAfterLast('/')
            "echo '---CONFIG $configName---'; " +
                (0..4).joinToString("; ") { fold ->
                    "echo '---FOLD $fold---'; cat \"$configPath/fold_$fold/training_log_\"*.txt 2>/dev/null"
                }
        }
        val logResult = commandExecutor.execute(logCmd)
        val foldsByConfig = if (logResult is Result.Success)
            logParser.parseMultiConfig(logResult.data) else emptyMap()

        val trainingConfigs = parsed.configPaths.map { path ->
            val name = path.substringAfterLast('/')
            NnUNetTrainingConfig(configName = name, folds = foldsByConfig[name] ?: emptyList())
        }

        return NnUNetWorkflow(
            rawDataStatus = parsed.rawStatus,
            planningStatus = parsed.planStatus,
            preprocessingStatus = parsed.prepStatus,
            preprocessingCurrent = parsed.prepCurrent,
            preprocessingTotal = parsed.prepTotal,
            trainingConfigs = trainingConfigs,
            postprocessingStatus = parsed.postprocStatus,
        )
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private fun buildStageCheckCommand(
        datasetName: String,
        rawDir: String?,
        prepDir: String?,
        resultsDatasetPath: String,
    ): String {
        val rawCheck = if (rawDir != null)
            "test -d \"$rawDir/$datasetName\" && echo exists || echo missing"
        else "echo unknown"

        val planCheck = if (prepDir != null)
            "test -f \"$prepDir/$datasetName/nnUNetPlans.json\" && echo done || echo missing"
        else "echo unknown"

        // pkl files exist in both old nnUNet (npz+pkl) and new nnUNet (b2nd+pkl), so counting
        // them is version-agnostic. Each preprocessed case has exactly one .pkl file.
        val prepCheck = if (prepDir != null) """
            _D="$prepDir/$datasetName"
            _NUM=$(grep '"numTraining"' "${'$'}_D/dataset.json" 2>/dev/null | grep -o '[0-9]*' | head -1)
            _COUNT=$(find "${'$'}_D" -mindepth 2 -maxdepth 2 -name "*.pkl" 2>/dev/null | wc -l | tr -d ' ')
            if [ -z "${'$'}_NUM" ] || [ "${'$'}_NUM" = "0" ]; then echo unknown
            elif [ "${'$'}_COUNT" -eq 0 ]; then echo missing
            elif [ "${'$'}_COUNT" -lt "${'$'}_NUM" ]; then echo inprogress; echo "counts ${'$'}_COUNT ${'$'}_NUM"
            else echo complete; fi
        """.trimIndent()
        else "echo unknown"

        val postprocCheck = if (resultsDatasetPath.isNotBlank())
            "test -d \"$resultsDatasetPath/postprocessing\" && echo done || echo missing"
        else "echo unknown"

        val configList = if (resultsDatasetPath.isNotBlank())
            "find \"$resultsDatasetPath\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort"
        else "true"

        return """
            echo '=== RAW ==='; $rawCheck
            echo '=== PLAN ==='; $planCheck
            echo '=== PREP ==='; $prepCheck
            echo '=== CONFIGS ==='; $configList
            echo '=== POSTPROC ==='; $postprocCheck
        """.trimIndent()
    }

    private data class StageCheckResult(
        val rawStatus: NnUNetStageStatus,
        val planStatus: NnUNetStageStatus,
        val prepStatus: NnUNetStageStatus,
        val prepCurrent: Int,
        val prepTotal: Int,
        val configPaths: List<String>,
        val postprocStatus: NnUNetStageStatus,
    )

    private val COUNTS_PATTERN = Regex("""^counts (\d+) (\d+)$""")

    private fun parseStageOutput(output: String): StageCheckResult {
        val sections = mutableMapOf<String, MutableList<String>>()
        var current: String? = null
        for (line in output.lines()) {
            val trimmed = line.trim()
            when {
                trimmed == "=== RAW ===" -> current = "RAW"
                trimmed == "=== PREP ===" -> current = "PREP"
                trimmed == "=== PLAN ===" -> current = "PLAN"
                trimmed == "=== CONFIGS ===" -> current = "CONFIGS"
                trimmed == "=== POSTPROC ===" -> current = "POSTPROC"
                trimmed.isNotBlank() && current != null ->
                    sections.getOrPut(current) { mutableListOf() }.add(trimmed)
            }
        }

        fun sectionStatus(key: String, doneWord: String = "done"): NnUNetStageStatus {
            val lines = sections[key] ?: return NnUNetStageStatus.Unknown
            return when {
                lines.any { it == "unknown" } -> NnUNetStageStatus.Unknown
                lines.any { it == "exists" || it == doneWord || it == "complete" } -> NnUNetStageStatus.Complete
                lines.any { it == "inprogress" } -> NnUNetStageStatus.InProgress
                else -> NnUNetStageStatus.Missing
            }
        }

        val prepLines = sections["PREP"] ?: emptyList()
        val countsMatch = prepLines.firstNotNullOfOrNull { COUNTS_PATTERN.matchEntire(it) }
        val prepCurrent = countsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val prepTotal = countsMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0

        return StageCheckResult(
            rawStatus = sectionStatus("RAW", "exists"),
            planStatus = sectionStatus("PLAN", "done"),
            prepStatus = sectionStatus("PREP", "complete"),
            prepCurrent = prepCurrent,
            prepTotal = prepTotal,
            configPaths = sections["CONFIGS"]?.filter { it.isNotBlank() } ?: emptyList(),
            postprocStatus = sectionStatus("POSTPROC", "done"),
        )
    }

    private suspend fun resolveResultsDir(): String? =
        resolveDir(appPreferences.nnUNetResultsDir, "\${nnUNet_results:-}")

    private suspend fun resolveRawDir(): String? =
        resolveDir(appPreferences.nnUNetRawDir, "\${nnUNet_raw:-}")

    private suspend fun resolvePreprocessedDir(): String? =
        resolveDir(appPreferences.nnUNetPreprocessedDir, "\${nnUNet_preprocessed:-}")

    private suspend fun resolveDir(override: String, envExpr: String): String? {
        val trimmed = override.trim()
        if (trimmed.isNotBlank()) return trimmed
        val result = commandExecutor.execute("echo \"$envExpr\"")
        val dir = (result as? Result.Success)?.data?.trim()
        return if (dir.isNullOrBlank()) null else dir
    }
}
