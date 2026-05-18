package org.slurmdroid.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.slurmdroid.core.db.entities.JobHistory

@Dao
interface JobHistoryDao {
    @Query("SELECT * FROM job_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<JobHistory>>

    @Insert
    suspend fun insert(job: JobHistory): Long

    @Query("UPDATE job_history SET lastKnownStatus = :status WHERE slurmJobId = :slurmJobId")
    suspend fun updateStatus(slurmJobId: String, status: String)
}
