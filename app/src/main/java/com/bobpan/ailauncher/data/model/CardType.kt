package com.bobpan.ailauncher.data.model

/**
 * Card slot-category. Seed catalog only uses [CONTINUE] / [DISCOVER] / [NEW].
 * [HERO] is reserved for runtime slot-typing and is never present in seeds.
 */
enum class CardType { HERO, CONTINUE, DISCOVER, NEW }
