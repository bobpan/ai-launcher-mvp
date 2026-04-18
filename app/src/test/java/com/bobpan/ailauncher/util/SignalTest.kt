package com.bobpan.ailauncher.util

import com.bobpan.ailauncher.data.model.Card
import com.bobpan.ailauncher.data.model.CardType
import com.bobpan.ailauncher.data.model.Intent
import com.bobpan.ailauncher.data.model.Signal
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalTest {

    private val card = Card(
        id = "t1", intent = Intent.WORK, type = CardType.CONTINUE,
        icon = "", title = "", description = "", actionLabel = "", whyLabel = "",
        tags = listOf("x", "y")
    )

    @Test
    fun `weights follow PRD table`() {
        assertEquals(3f,  Signal.Like("t1", 0L).weight,         0.0001f)
        assertEquals(2f,  Signal.ActionTapped("t1", 0L).weight, 0.0001f)
        assertEquals(1f,  Signal.Dwell("t1", 0L, 2500L).weight, 0.0001f)
        assertEquals(0f,  Signal.Tap("t1", 0L).weight,          0.0001f)
        assertEquals(-2f, Signal.Dismiss("t1", 0L).weight,      0.0001f)
        assertEquals(-3f, Signal.Dislike("t1", 0L).weight,      0.0001f)
    }
}
