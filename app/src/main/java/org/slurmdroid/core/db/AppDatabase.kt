package org.slurmdroid.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import org.slurmdroid.core.db.dao.JobHistoryDao
import org.slurmdroid.core.db.entities.JobHistory
import org.slurmdroid.core.db.entities.SshProfile

@Database(entities = [JobHistory::class, SshProfile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobHistoryDao(): JobHistoryDao
}
