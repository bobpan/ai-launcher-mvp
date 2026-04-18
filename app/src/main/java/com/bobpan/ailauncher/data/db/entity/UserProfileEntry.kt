package com.bobpan.ailauncher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Running tag-weight profile (FR-10).
 */
@Entity(
    tableName = "user_profile",
    indices = [Index(value = ["weight"])]
)
data class UserProfileEntry(
    @PrimaryKey
    @ColumnInfo(name = "tag")        val tag: String,
    @ColumnInfo(name = "weight")     val weight: Float,
    @ColumnInfo(name = "updated_ms") val updatedMs: Long
)
