package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import com.malfreyt.alexandre.hamigo.platform.ShareProgress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.concurrent.thread

/** Real ViewModel and UI, with separate preferences and no network authorization. */
@RunWith(AndroidJUnit4::class)
class AppModelStateTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = ViewModelStore()
    private lateinit var isolated: IsolatedContext
    private lateinit var model: AppModel

    @Before fun createLocalModel() {
        isolated = IsolatedContext(context)
        isolated.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed", true).putBoolean("autoSync", false).putString("name", "Témoin").commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppModel() as T
            })[AppModel::class.java]
            model.initialize(isolated)
        }
        ui.setContent { HamigoTheme { HamigoApp(model) } }
        ui.waitUntil(60_000) { model.content != null }
        ui.waitForIdle()
    }

    @After fun clearIsolatedState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        if (::isolated.isInitialized) isolated.cleanup()
        // AppModel initialization cancels the common emulator alarm/job when its fixture is disabled.
        DailyReminder.schedule(context)
        ProgressSyncScheduler.schedule(context)
    }

    @Test fun identicalNamesRemainSeparateAndASameGistReplacesOnlyItsOwnFriend() {
        val first = Friend(ShareProgress("Alex", 120, 2, 4), "https://gist.github.com/alice/abcde01234")
        val second = Friend(ShareProgress("Alex", 340, 5, 9), "https://gist.github.com/bob/fedcb98765")
        ui.runOnIdle {
            model.addFriend(first)
            model.addFriend(second)
        }
        assertEquals(2, model.friends.size)
        assertEquals(setOf(120, 340), model.friends.map { it.progress.xp }.toSet())
        assertEquals(2, JSONArray(model.progress.prefs.getString("friends", "[]")).length())

        val updated = Friend(first.progress.copy(name = "Alex F4ABC", xp = 180), "https://gist.github.com/abcde01234")
        ui.runOnIdle {
            model.addFriend(updated)
            model.refresh()
        }
        assertEquals(2, model.friends.size)
        assertEquals(setOf("Alex", "Alex F4ABC"), model.friends.map { it.progress.name }.toSet())
        assertEquals(setOf(180, 340), model.friends.map { it.progress.xp }.toSet())
    }

    @Test fun backgroundPreferenceWritesRefreshTheOpenProfileAndFriendList() {
        ui.runOnIdle { model.route = "profile" }
        ui.onNodeWithText("Témoin").assertExists()
        ui.onNodeWithText("0 XP · 🔥 0 jours").assertExists()
        val updatedFriend = Friend(ShareProgress("Équipier distant", 650, 8, 16), "https://gist.github.com/abcde12345")
        val write = thread(name = "hamigo-test-background-preferences") {
            // The worker uses its own Progress instance; no AppModel refresh/navigation is requested.
            val background = Progress(isolated)
            background.answer("worker-recall", correct = true)
            background.name = "Après synchronisation"
            background.prefs.edit().putString("friends", JSONArray(listOf(
                JSONObject().put("progress", JSONObject(updatedFriend.progress.toJson())).put("gist", updatedFriend.gist)
            )).toString()).putInt("dailyGoal", 60).commit()
        }
        write.join(10_000)
        check(!write.isAlive) { "The fixture write did not complete." }
        ui.waitUntil(15_000) {
            model.progress.xp == 3 && model.progress.name == "Après synchronisation" &&
                model.progress.dailyGoal == 60 && model.friends.singleOrNull()?.progress?.name == "Équipier distant"
        }
        ui.onNodeWithText("Après synchronisation").assertExists()
        ui.onNodeWithText("3 XP · 🔥 1 jours").assertExists()
        ui.runOnIdle { model.route = "friends" }
        ui.onAllNodesWithText("Équipier distant").onFirst().assertExists()
    }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "app-model-state-test-${UUID.randomUUID()}"
        private val preferenceNames = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val unique = "$prefix-$name"
            synchronized(preferenceNames) { preferenceNames += unique }
            return baseContext.getSharedPreferences(unique, mode)
        }
        fun cleanup() {
            synchronized(preferenceNames) { preferenceNames.toList() }.forEach { name ->
                baseContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                baseContext.deleteSharedPreferences(name)
            }
        }
    }
}
