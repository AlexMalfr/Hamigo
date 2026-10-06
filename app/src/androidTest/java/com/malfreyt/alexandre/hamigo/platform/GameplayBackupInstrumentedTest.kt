package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.GameplayPreferences
import com.malfreyt.alexandre.hamigo.MorseInputSettings
import com.malfreyt.alexandre.hamigo.Progress
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GameplayBackupInstrumentedTest {
    @Test fun defaultsAndSavedSettingsSurviveRecreatingProgress() = isolated { context ->
        val progress = Progress(context)
        assertEquals(MorseInputSettings(false, 300), GameplayPreferences.read(progress.prefs))
        GameplayPreferences.save(progress.prefs, MorseInputSettings(true, 450))
        assertEquals(MorseInputSettings(true, 450), GameplayPreferences.read(Progress(context).prefs))
        assertTrue(progress.prefs.getLong("preferencesUpdatedAt", 0) > 0)
    }

    @Test fun exportRestoresBothSettingsByImportAndByCloudOnFreshInstallations() = isolated { sourceContext ->
        val source = Progress(sourceContext)
        GameplayPreferences.save(source.prefs, MorseInputSettings(true, 420))
        val exported = source.export()
        CloudProgress.validate(exported)
        val preferences = JSONObject(exported).getJSONObject("preferences")
        assertTrue(preferences.getBoolean(GameplayPreferences.SINGLE_KEY))
        assertEquals(420, preferences.getInt(GameplayPreferences.THRESHOLD))
        isolated { importContext ->
            val imported = Progress(importContext)
            imported.import(exported)
            assertEquals(MorseInputSettings(true, 420), GameplayPreferences.read(Progress(importContext).prefs))
        }
        isolated { cloudContext ->
            val restored = Progress(cloudContext)
            assertTrue(restored.mergeCloud(exported))
            assertEquals(MorseInputSettings(true, 420), GameplayPreferences.read(Progress(cloudContext).prefs))
            assertFalse(restored.mergeCloud(exported))
        }
    }

    @Test fun newerExplicitSettingsWinInBothMergeOrdersAndRemainStable() = isolated { context ->
        val progress = Progress(context)
        val older = backup(progress, false, 300, 100)
        val newer = backup(progress, true, 550, 200)
        val forward = CloudProgress.merge(older.toString(), newer.toString())
        assertEquals(forward, CloudProgress.merge(newer.toString(), older.toString()))
        assertEquals(forward, CloudProgress.merge(forward, older.toString()))
        val merged = JSONObject(forward).getJSONObject("preferences")
        assertTrue(merged.getBoolean(GameplayPreferences.SINGLE_KEY))
        assertEquals(550, merged.getInt(GameplayPreferences.THRESHOLD))
        progress.import(older.toString())
        // Import intentionally sets a fresh clock: fix it to model two offline devices.
        progress.prefs.edit().putLong("preferencesUpdatedAt", 100).commit()
        assertTrue(progress.mergeCloud(newer.toString()))
        assertEquals(MorseInputSettings(true, 550), GameplayPreferences.read(progress.prefs))
        assertEquals(200L, progress.prefs.getLong("preferencesUpdatedAt", 0))
    }

    @Test fun oldBackupsWithoutMorseKeysPreserveLocalSettingsDuringImportAndNewerCloudMerge() = isolated { context ->
        val progress = Progress(context)
        GameplayPreferences.save(progress.prefs, MorseInputSettings(true, 470))
        val legacy = JSONObject(progress.export()).apply {
            put("preferencesUpdatedAt", 200)
            getJSONObject("preferences").apply {
                remove(GameplayPreferences.SINGLE_KEY)
                remove(GameplayPreferences.THRESHOLD)
                put("dailyGoal", 75)
            }
        }
        CloudProgress.validate(legacy.toString())
        progress.import(legacy.toString())
        assertEquals(75, progress.dailyGoal)
        assertEquals(MorseInputSettings(true, 470), GameplayPreferences.read(progress.prefs))
        progress.prefs.edit().putLong("preferencesUpdatedAt", 100).putInt("dailyGoal", 30).commit()
        assertTrue(progress.mergeCloud(legacy.toString()))
        assertEquals(75, progress.dailyGoal)
        assertEquals(MorseInputSettings(true, 470), GameplayPreferences.read(Progress(context).prefs))
    }

    @Test fun inclusiveTimingBoundsAreAcceptedAndExplicitTwoButtonModeIsRestored() = isolated { context ->
        val progress = Progress(context)
        for (threshold in listOf(150, 600)) {
            val candidate = backup(progress, false, threshold, 300)
            CloudProgress.validate(candidate.toString())
            GameplayPreferences.save(progress.prefs, MorseInputSettings(true, 300))
            progress.import(candidate.toString())
            assertEquals(MorseInputSettings(false, threshold), GameplayPreferences.read(progress.prefs))
        }
    }

    @Test fun malformedGameplaySettingsAreRejectedWithoutChangingLocalProgressOrPreferences() = isolated { context ->
        val progress = Progress(context)
        GameplayPreferences.save(progress.prefs, MorseInputSettings(true, 420))
        val before = progress.cloudExport()
        val invalidPreferences = listOf(
            GameplayPreferences.SINGLE_KEY to "true",
            GameplayPreferences.SINGLE_KEY to 1,
            GameplayPreferences.SINGLE_KEY to JSONObject.NULL,
            GameplayPreferences.THRESHOLD to 149,
            GameplayPreferences.THRESHOLD to 601,
            GameplayPreferences.THRESHOLD to 300.5,
            GameplayPreferences.THRESHOLD to "300",
            GameplayPreferences.THRESHOLD to true,
            GameplayPreferences.THRESHOLD to JSONObject.NULL
        )
        for ((key, value) in invalidPreferences) {
            val invalid = JSONObject(before).apply { getJSONObject("preferences").put(key, value) }.toString()
            assertThrows("Invalid $key=$value accepted", Exception::class.java) { CloudProgress.validate(invalid) }
            assertThrows(Exception::class.java) { progress.import(invalid) }
            assertEquals(before, progress.cloudExport())
            assertThrows(Exception::class.java) { progress.mergeCloud(invalid) }
            assertEquals(before, progress.cloudExport())
            assertEquals(MorseInputSettings(true, 420), GameplayPreferences.read(progress.prefs))
        }
    }

    private fun backup(progress: Progress, singleKey: Boolean, threshold: Int, clock: Long) =
        JSONObject(progress.cloudExport()).put("preferencesUpdatedAt", clock).apply {
            getJSONObject("preferences").put(GameplayPreferences.SINGLE_KEY, singleKey)
                .put(GameplayPreferences.THRESHOLD, threshold)
        }

    private fun isolated(test: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "gameplay-backup-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("$namespace-$name", mode)
        }
        context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit().putBoolean("autoSync", false).commit()
        try { test(context) } finally {
            listOf("hamigo", "hamigo_social", "hamigo_secure").forEach { base.deleteSharedPreferences("$namespace-$it") }
        }
    }
}
