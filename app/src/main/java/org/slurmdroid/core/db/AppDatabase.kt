package org.slurmdroid.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.slurmdroid.core.db.dao.JobHistoryDao
import org.slurmdroid.core.db.entities.JobHistory
import org.slurmdroid.core.db.entities.SshProfile

@Database(entities = [JobHistory::class, SshProfile::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobHistoryDao(): JobHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE job_history ADD COLUMN scontrolRaw TEXT")
            }
        }
    }
}
