package com.bobpan.ailauncher.data.model

/**
 * All signal categories persisted to `feedback_events.signal`.
 * See ARCH §5.4 and PRD §3.
 */
enum class SignalType {
    TAP, DWELL, THUMB_UP, THUMB_DOWN, DISMISS, ACTION_TAP, INTENT_SWITCH, REVEAL
}

/**
 * Sealed hierarchy of user signals on cards.
 * Weight table (PRD §3 item 2):
 *   THUMB_UP=+3, ACTION_TAP=+2, DWELL=+1, DISMISS=-2, THUMB_DOWN=-3.
 *   TAP / INTENT_SWITCH / REVEAL persist with 0 (instrumentation only).
 */
sealed class Signal {
    abstract val cardId:      String
    abstract val timestampMs: Long

    data class Tap(            override val cardId: String, override val timestampMs: Long) : Signal()
    data class Dwell(          override val cardId: String, override val timestampMs: Long, val durationMs: Long) : Signal()
    data class Like(           override val cardId: String, override val timestampMs: Long) : Signal()
    data class Dislike(        override val cardId: String, override val timestampMs: Long) : Signal()
    data class Dismiss(        override val cardId: String, override val timestampMs: Long) : Signal()
    data class ActionTapped(   override val cardId: String, override val timestampMs: Long) : Signal()
    data class IntentSwitch(   override val cardId: String, override val timestampMs: Long, val to: Intent) : Signal()
    data class RevealAppeared( override val cardId: String, override val timestampMs: Long) : Signal()

    val persistedType: SignalType
        get() = when (this) {
            is Tap            -> SignalType.TAP
            is Dwell          -> SignalType.DWELL
            is Like           -> SignalType.THUMB_UP
            is Dislike        -> SignalType.THUMB_DOWN
            is Dismiss        -> SignalType.DISMISS
            is ActionTapped   -> SignalType.ACTION_TAP
            is IntentSwitch   -> SignalType.INTENT_SWITCH
            is RevealAppeared -> SignalType.REVEAL
        }

    val weight: Float
        get() = when (this) {
            is Like           -> +3f
            is ActionTapped   -> +2f
            is Dwell          -> +1f
            is Tap            ->  0f
            is RevealAppeared ->  0f
            is IntentSwitch   ->  0f
            is Dismiss        -> -2f
            is Dislike        -> -3f
        }
}
