package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.Progress
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun progressContextIgnoresZeroXpDaysAndLoadsNoCurriculum() {
        val isolated = IsolatedContext(context)
        val date = LocalDate.of(2026, 10, 3)
        val prefs = isolated.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("progress", JSONObject().put("dailyXp",
                JSONObject().put(date.minusDays(1).toString(), 0).put(date.plusDays(1).toString(), 10)).toString()).commit()
            assertEquals(ReminderContext.START, ReminderContent.build(Progress(isolated), date).context)
            prefs.edit().putString("progress", JSONObject().put("dailyXp",
                JSONObject().put(date.minusDays(1).toString(), 3).put(date.toString(), 12)).toString()).commit()
            val content = ReminderContent.build(Progress(isolated), date)
            assertEquals(ReminderContext.IN_PROGRESS, content.context)
            assertEquals(12, content.todayXp)
            assertEquals(2, content.streak)
        } finally { prefs.edit().clear().commit() }
    }

    @Test
    fun everyContextHasDistinctColorAndRenderableNotificationArtwork() {
        val date = LocalDate.of(2026, 10, 3)
        val states = listOf(
            ReminderState(0, 30, emptySet()),
            ReminderState(0, 30, setOf(date.minusDays(1).toString())),
            ReminderState(0, 30, setOf(date.minusDays(3).toString())),
            ReminderState(12, 30, setOf(date.toString())),
            ReminderState(30, 30, setOf(date.toString()))
        )
        val colors = mutableSetOf<Int>()
        states.forEach { state ->
            val content = ReminderContent.build(state, date)
            val image = ReminderArtwork.render(content)
            val avatar = ReminderArtwork.avatar(content)
            try {
                assertEquals(1080, image.width); assertEquals(540, image.height)
                assertEquals(content.background, image.getPixel(0, 0))
                colors += image.getPixel(0, 0)
                assertEquals(192, avatar.width); assertEquals(192, avatar.height)
                // Mascot is genuinely rendered rather than a flat colored notification placeholder.
                assertNotEquals(content.background, image.getPixel(920, 320))
                assertNotEquals(0, avatar.getPixel(96, 110))
            } finally { image.recycle(); avatar.recycle() }
        }
        assertEquals(5, colors.size)
    }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val namespace = "reminder-test-${UUID.randomUUID()}"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("$namespace-$name", mode)
    }
}
