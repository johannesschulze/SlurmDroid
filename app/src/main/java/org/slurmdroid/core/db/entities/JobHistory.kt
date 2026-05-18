package org.slurmdroid.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "job_history")
data class JobHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val jobName: String,
    val fullCommand: String,
    val partition: String,
    val lastKnownStatus: String,
    val slurmJobId: String?,
)
