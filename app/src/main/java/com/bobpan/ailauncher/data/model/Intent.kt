package com.bobpan.ailauncher.data.model

/**
 * User-declared context mode. Exactly 4 values, order matters for UI chip strip (FR-03).
 * Cold-start default = [WORK] (FR-03, FR-11 cold-start).
 */
enum class Intent { COMMUTE, WORK, LUNCH, REST }
