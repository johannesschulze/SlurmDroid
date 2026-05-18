package org.slurmdroid.core.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_profiles")
data class SshProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostname: String,
    val port: Int,
    val username: String,
    val keyAlias: String,
)
