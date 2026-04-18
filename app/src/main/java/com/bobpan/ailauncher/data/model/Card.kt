package com.bobpan.ailauncher.data.model

import androidx.compose.runtime.Immutable

/**
 * Immutable domain card shown on the home screen.
 * `id`, `intent`, `type`, and `tags` are load-bearing (PRD FR-16).
 */
@Immutable
data class Card(
    val id:           String,
    val intent:       Intent,
    val type:         CardType,
    val icon:         String,
    val title:        String,
    val description:  String,
    val actionLabel:  String,
    val whyLabel:     String,
    val tags:         List<String>,
    val seedOrder:    Int = 0
)
