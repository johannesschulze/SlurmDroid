package org.slurmdroid.nnunet.domain

data class NnUNetDataset(
    val name: String,
    val resultsPath: String,
)

/** Splits "Dataset052_MandibleFracture" into id="052" and humanName="MandibleFracture". */
fun parseDatasetName(raw: String): Pair<String, String> {
    val match = Regex("""^Dataset(\d+)_(.+)$""").matchEntire(raw)
    return if (match != null) match.groupValues[1] to match.groupValues[2]
    else "" to raw
}
