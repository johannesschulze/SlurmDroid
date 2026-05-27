package org.slurmdroid.nnunet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slurmdroid.nnunet.data.NnUNetLogParser
import org.slurmdroid.nnunet.domain.NnUNetDataset
import org.slurmdroid.nnunet.domain.NnUNetStageStatus
import org.slurmdroid.nnunet.domain.NnUNetTrainingConfig
import org.slurmdroid.nnunet.domain.NnUNetWorkflow
import org.slurmdroid.plugin.api.ISlurmDroidPlugin
import org.slurmdroid.plugin.api.PluginSetting

class NnUNetPlugin : ISlurmDroidPlugin {

    override val id = "org.slurmdroid.nnunet"
    override val displayName = "nnU-Net"

    private var resultsDir = ""
    private var rawDir = ""
    private var preprocessedDir = ""

    private val logParser = NnUNetLogParser()

    private val _datasets = MutableStateFlow<List<NnUNetDataset>>(emptyList())
    val datasets: StateFlow<List<NnUNetDataset>> = _datasets.asStateFlow()

    private val _workflows = MutableStateFlow<Map<String, NnUNetWorkflow>>(emptyMap())
    val workflows: StateFlow<Map<String, NnUNetWorkflow>> = _workflows.asStateFlow()

    private val _anyDirConfigured = MutableStateFlow<Boolean?>(null)
    val anyDirConfigured: StateFlow<Boolean?> = _anyDirConfigured.asStateFlow()

    private val _resolvedResultsDir = MutableStateFlow<String?>(null)
    val resolvedResultsDir: StateFlow<String?> = _resolvedResultsDir.asStateFlow()

    override fun getSettings(): List<PluginSetting> = listOf(
        PluginSetting.TextInput(
            key = "nnunet_base_dir",
            label = "nnU-Net base directory",
        ),
        PluginSetting.TextInput(
            key = "nnunet_results_dir",
            label = "nnUNet_results (optional override)",
        ),
        PluginSetting.TextInput(
            key = "nnunet_raw_dir",
            label = "nnUNet_raw (optional override)",
        ),
        PluginSetting.TextInput(
            key = "nnunet_preprocessed_dir",
            label = "nnUNet_preprocessed (optional override)",
        ),
    )

    override fun onSettingsChanged(values: Map<String, String>) {
        val base = values["nnunet_base_dir"]?.trim() ?: ""
        resultsDir = values["nnunet_results_dir"]?.trim()?.ifBlank { null }
            ?: subDir(base, "results")
        rawDir = values["nnunet_raw_dir"]?.trim()?.ifBlank { null }
            ?: subDir(base, "raw")
        preprocessedDir = values["nnunet_preprocessed_dir"]?.trim()?.ifBlank { null }
            ?: subDir(base, "preprocessed")
    }

    private fun subDir(base: String, name: String) =
        if (base.isNotBlank()) "${base.trimEnd('/')}/$name" else ""

    override fun poll(executor: (String) -> String) {
        val resolvedResults = resolveDir(resultsDir, "\${nnUNet_results:-}", executor)
        val resolvedRaw = resolveDir(rawDir, "\${nnUNet_raw:-}", executor)
        val resolvedPrep = resolveDir(preprocessedDir, "\${nnUNet_preprocessed:-}", executor)

        if (resolvedResults != null) _resolvedResultsDir.value = resolvedResults

        val parts = buildList {
            if (resolvedResults != null)
                add("find \"$resolvedResults\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null")
            if (resolvedRaw != null)
                add("find \"$resolvedRaw\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null")
            if (resolvedPrep != null)
                add("find \"$resolvedPrep\" -mindepth 1 -maxdepth 1 -type d 2>/dev/null")
        }
        _anyDirConfigured.value = parts.isNotEmpty()
        if (parts.isEmpty()) return

        val effectiveResultsDir = resolvedResults ?: ""
        val dirOutput = executor("(${parts.joinToString("; ")}) | sort -u")
        val datasets = dirOutput.lines()
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
        _datasets.value = datasets

        _workflows.value = datasets.associate { dataset ->
            dataset.name to loadDatasetWorkflow(dataset, resolvedRaw, resolvedPrep, executor)
        }
    }

    private fun loadDatasetWorkflow(
        dataset: NnUNetDataset,
        rawDir: String?,
        prepDir: String?,
        executor: (String) -> String,
    ): NnUNetWorkflow {
        val stageOutput = executor(buildStageCheckCommand(dataset.name, rawDir, prepDir, dataset.resultsPath))
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

        val logCmd = parsed.configPaths.joinToString("; ") { configPath ->
            val configName = configPath.substringAfterLast('/')
            "echo '---CONFIG $configName---'; " +
                (0..4).joinToString("; ") { fold ->
                    "echo '---FOLD $fold---'; cat \"$configPath/fold_$fold/training_log_\"*.txt 2>/dev/null"
                }
        }
        val foldsByConfig = logParser.parseMultiConfig(executor(logCmd))

        return NnUNetWorkflow(
            rawDataStatus = parsed.rawStatus,
            planningStatus = parsed.planStatus,
            preprocessingStatus = parsed.prepStatus,
            preprocessingCurrent = parsed.prepCurrent,
            preprocessingTotal = parsed.prepTotal,
            trainingConfigs = parsed.configPaths.map { path ->
                val name = path.substringAfterLast('/')
                NnUNetTrainingConfig(configName = name, folds = foldsByConfig[name] ?: emptyList())
            },
            postprocessingStatus = parsed.postprocStatus,
        )
    }

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

        // pkl files exist in both old nnUNet (npz+pkl) and new nnUNet (b2nd+pkl),
        // so counting them is version-agnostic. Each preprocessed case has exactly one .pkl file.
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

        return StageCheckResult(
            rawStatus = sectionStatus("RAW", "exists"),
            planStatus = sectionStatus("PLAN", "done"),
            prepStatus = sectionStatus("PREP", "complete"),
            prepCurrent = countsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            prepTotal = countsMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0,
            configPaths = sections["CONFIGS"]?.filter { it.isNotBlank() } ?: emptyList(),
            postprocStatus = sectionStatus("POSTPROC", "done"),
        )
    }

    private fun resolveDir(override: String, envExpr: String, executor: (String) -> String): String? {
        val trimmed = override.trim()
        if (trimmed.isNotBlank()) return trimmed
        val dir = executor("echo \"$envExpr\"").trim()
        return if (dir.isBlank()) null else dir
    }
}
