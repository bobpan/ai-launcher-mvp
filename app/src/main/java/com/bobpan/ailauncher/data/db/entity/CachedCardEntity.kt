package com.bobpan.ailauncher.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Seed catalog materialized into DB on first boot (FR-16).
 */
@Entity(
    tableName = "cached_cards",
    indices = [Index(value = ["intent"])]
)
data class CachedCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")           val id: String,
    @ColumnInfo(name = "title")        val title: String,
    @ColumnInfo(name = "description")  val description: String,
    @ColumnInfo(name = "intent")       val intent: String,
    @ColumnInfo(name = "type")         val type: String,
    @ColumnInfo(name = "icon")         val icon: String,
    @ColumnInfo(name = "action_label") val actionLabel: String,
    @ColumnInfo(name = "why_label")    val whyLabel: String,
    @ColumnInfo(name = "tags_json")    val tagsJson: String,
    @ColumnInfo(name = "seed_order")   val seedOrder: Int
)
