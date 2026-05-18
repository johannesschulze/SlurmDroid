package org.slurmdroid.features.slurm.domain

/** A saved sbatch invocation for re-submission from the History screen. */
data class JobTemplate(
    val name: String,
    val fullCommand: String,
    val partition: String,
)
