package com.bobpan.ailauncher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per user feedback signal (FR-09).
 */
@Entity(
    tableName = "feedback_events",
    indices = [
        Index(value = ["card_id"]),
        Index(value = ["intent"]),
        Index(value = ["timestamp_ms"])
    ]
)
data class FeedbackEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "card_id")      val cardId: String,
    @ColumnInfo(name = "intent")       val intent: String,
    @ColumnInfo(name = "signal")       val signal: String,
    @ColumnInfo(name = "weight")       val weight: Float,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long
)
