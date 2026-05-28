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

    @Query("SELECT fullCommand FROM job_history WHERE slurmJobId = :slurmJobId LIMIT 1")
    suspend fun getCommandByJobId(slurmJobId: String): String?

    @Query("SELECT slurmJobId FROM job_history WHERE slurmJobId IS NOT NULL")
    suspend fun getAllSlurmJobIds(): List<String>

    @Query("SELECT slurmJobId FROM job_history WHERE slurmJobId IS NOT NULL AND scontrolRaw IS NOT NULL")
    suspend fun getSlurmJobIdsWithScontrolRaw(): List<String>

    @Query("UPDATE job_history SET scontrolRaw = :raw WHERE slurmJobId = :slurmJobId")
    suspend fun updateScontrolRaw(slurmJobId: String, raw: String)
}
