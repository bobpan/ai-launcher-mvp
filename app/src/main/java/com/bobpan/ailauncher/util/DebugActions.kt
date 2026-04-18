package com.bobpan.ailauncher.util

import android.util.Log
import com.bobpan.ailauncher.data.db.dao.UserProfileDao
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.data.model.Signal
import com.bobpan.ailauncher.data.repo.CardRepository
import com.bobpan.ailauncher.data.repo.FeedbackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-20 dev-panel actions. Only invoked from DEBUG builds.
 */
@Singleton
class DebugActions @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val cardRepository: CardRepository,
    private val clock: Clock
) {
    /** Inserts ~20 synthetic signals biased toward coffee/design/music. */
    suspend fun seedDemoProfile() {
        val catalog = cardRepository.all()
        fun card(id: String) = catalog.firstOrNull { it.id == id } ?: return
        val now = clock.nowMs()

        // Helper — only emit if the target card exists in the live catalog.
        suspend fun emit(id: String, factory: (Long) -> Signal) {
            catalog.firstOrNull { it.id == id }?.let { c ->
                feedbackRepository.record(factory(now), c)
            }
        }

        // Coffee / Luckin bias (LUNCH)
        repeat(4) { emit("lunch_01") { t -> Signal.Like(cardId = "lunch_01", timestampMs = t) } }
        emit("lunch_01") { t -> Signal.ActionTapped(cardId = "lunch_01", timestampMs = t) }

        // Design / Figma bias (WORK)
        repeat(3) { emit("work_01") { t -> Signal.Like(cardId = "work_01", timestampMs = t) } }
        emit("work_01") { t -> Signal.ActionTapped(cardId = "work_01", timestampMs = t) }
        emit("work_02") { t -> Signal.Dwell(cardId = "work_02", timestampMs = t, durationMs = 2400L) }

        // Music / Jay bias (REST)
        repeat(3) { emit("rest_02") { t -> Signal.Like(cardId = "rest_02", timestampMs = t) } }
        emit("rest_02") { t -> Signal.Dwell(cardId = "rest_02", timestampMs = t, durationMs = 3200L) }

        // Negative examples
        emit("work_04") { t -> Signal.Dislike(cardId = "work_04", timestampMs = t) }
        emit("lunch_04") { t -> Signal.Dismiss(cardId = "lunch_04", timestampMs = t) }
        emit("commute_04") { t -> Signal.Dislike(cardId = "commute_04", timestampMs = t) }
    }

    suspend fun dumpProfileToLogcat(profileDao: UserProfileDao) {
        val all = profileDao.snapshot()
        Log.d(
            "FLYWHEEL",
            "profile_dump count=${all.size} " +
                all.joinToString(prefix = "[", postfix = "]") {
                    "${it.tag}=${"%+.2f".format(it.weight)}"
                }
        )
    }
}
