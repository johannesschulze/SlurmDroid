package org.slurmdroid.core.feature

import org.slurmdroid.features.slurm.SlurmFeature
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureRegistry @Inject constructor(
    slurmFeature: SlurmFeature,
) {
    val features: List<ServerFeature> = listOf(slurmFeature)
}
