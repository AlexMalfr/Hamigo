package com.malfreyt.alexandre.hamigo.platform

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlatformInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tokenIsEncryptedAndCanBeReplacedAndDeleted() {
        // Unique namespace isolates test ciphertext AND Keystore alias from the user's real token.
        val isolated = TokenTestContext(context, UUID.randomUUID().toString())
        val store = SecureTokenStore(isolated)
        val first = "synthetic-token-for-test-one"
        val second = "synthetic-token-for-test-two"
        try {
            assertNull(store.get())
            store.store(first)
            assertEquals(first, SecureTokenStore(isolated).get())
            val prefs = isolated.getSharedPreferences("hamigo_secure", Context.MODE_PRIVATE)
            assertNotEquals(first, prefs.getString("ciphertext", null))
            assertFalse(prefs.all.values.any { it.toString().contains(first) })
            val firstCiphertext = prefs.getString("ciphertext", null)
            store.store(first)
            assertNotEquals(firstCiphertext, prefs.getString("ciphertext", null))
            store.store(second)
            assertEquals(second, store.get())
            store.delete()
            assertNull(store.get())
            assertTrue(prefs.all.isEmpty())
            store.store(first)
            assertEquals(first, store.get())
        } finally { store.delete() }
    }

    @Test
    fun snapshotRoundTripsOnlyTheSevenPublicFields() {
        val snapshot = ShareProgress("F4 Ami", 120, 3, 4, 75, "2026-10-03T16:00:00Z")
        val json = snapshot.toJson()
        assertEquals(snapshot, ShareProgress.fromJson(json))
        val objectValue = JSONObject(json)
        assertEquals(setOf("schema", "name", "xp", "streak", "lessons", "weeklyXp", "updatedAt"),
            objectValue.keys().asSequence().toSet())
        assertRejected(json.replace("\"schema\": 1", "\"schema\": 2"))
        assertRejected(JSONObject(json).put("schema", "1").toString())
        assertRejected(JSONObject(json).put("schema", 1.5).toString())
        assertRejected(JSONObject(json).put("name", 123).toString())
        assertRejected(JSONObject(json).put("xp", -1).toString())
        assertRejected(JSONObject(json).put("xp", "120").toString())
        assertRejected(JSONObject(json).put("updatedAt", "not-a-date").toString())
        assertRejected("not json")
    }

    @Test
    fun sharedCardHasCorrectSizeAndAReadableContentUri() {
        val file = NativeShare.renderProgressImage(context, ShareProgress("Test Hamigo", 2048, 12, 30, 400))
        try {
            assertTrue(file.length() > 10_000)
            val image = BitmapFactory.decodeFile(file.absolutePath)
            assertEquals(1080, image.width)
            assertEquals(1350, image.height)
            assertEquals(Color.rgb(255, 249, 233), image.getPixel(0, 0))
            image.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            assertEquals("content", uri.scheme)
            assertEquals("image/png", context.contentResolver.getType(uri))
            context.contentResolver.openInputStream(uri)!!.use {
                assertEquals(0x89, it.read()) // PNG signature, no file:// or storage permission involved.
            }
        } finally { file.delete() }
    }

    @Test
    fun reminderUsesLocalTimeAcrossDaylightSavingChange() {
        val paris = ZoneId.of("Europe/Paris")
        val now = ZonedDateTime.of(2026, 3, 28, 20, 1, 0, 0, paris)
        val trigger = Instant.ofEpochMilli(DailyReminder.nextTriggerMillis(now, 20, 0)).atZone(paris)
        assertEquals(29, trigger.dayOfMonth)
        assertEquals(20, trigger.hour)
        assertEquals(0, trigger.minute)
        assertTrue(trigger.isAfter(now))
        // Local 20:00 is preserved despite the 23-hour DST day.
        assertTrue(trigger.toInstant().toEpochMilli() - now.toInstant().toEpochMilli() < 24L * 60 * 60 * 1000)
    }

    @Test
    fun reminderCanScheduleAndCancelWithoutExactAlarmPermission() {
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toSet().orEmpty()
        assertFalse(permissions.contains(Manifest.permission.SCHEDULE_EXACT_ALARM))
        assertFalse(permissions.contains(Manifest.permission.USE_EXACT_ALARM))
        val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        val previous = prefs.all.filterKeys { it in setOf("reminderEnabled", "reminderHour", "reminderMinute") }
        try {
            DailyReminder.configure(context, true, 21, 15)
            assertTrue(prefs.getBoolean("reminderEnabled", false))
            assertEquals(21, prefs.getInt("reminderHour", -1))
            assertEquals(15, prefs.getInt("reminderMinute", -1))
            DailyReminder.configure(context, false, 21, 15)
            assertFalse(prefs.getBoolean("reminderEnabled", true))
        } finally {
            val edit = prefs.edit().remove("reminderEnabled").remove("reminderHour").remove("reminderMinute")
            previous.forEach { (key, value) -> when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
            } }
            edit.commit()
            DailyReminder.schedule(context)
        }
    }

    @Test
    fun gistLinksRejectOtherHostsAndAcceptStandardShareLinks() {
        val id = "0123456789abcdef0123456789abcdef"
        assertEquals(id, GitHubSync.gistId("https://gist.github.com/friend/$id"))
        assertEquals(id, GitHubSync.gistId("https://api.github.com/gists/$id"))
        assertEquals(id, GitHubSync.gistId(id))
        listOf("http://gist.github.com/friend/$id", "https://example.org/$id",
            "https://gist.github.com.evil.example/$id", "https://gist.github.com@$id.example/$id").forEach {
            try { GitHubSync.gistId(it); fail("Unexpectedly accepted an unrelated URL") }
            catch (_: IllegalArgumentException) { /* expected */ }
        }
    }

    @Test
    fun androidHttpsConnectionAcceptsPatchWithoutOpeningTheNetwork() {
        val connection = URL("https://api.github.com/").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PATCH"
            connection.doOutput = true
            assertEquals("PATCH", connection.requestMethod)
            // Do not call connect(), responseCode, or an I/O stream: this test sends no request.
        } finally { connection.disconnect() }
    }

    @Test
    fun oauthRejectsInvalidClientAndAnExpiredSessionWithoutPolling() = runBlocking {
        try { DeviceOAuth.start("invalid client with spaces"); fail("Invalid client was accepted") }
        catch (_: IllegalArgumentException) { /* rejected before network I/O */ }
        val deviceCredential = "synthetic-device-credential"
        val session = DeviceOAuth.Session(deviceCredential, "ABCD-EFGH", "https://github.com/login/device",
            expiresIn = 1, interval = 5, expiresAtElapsed = 0)
        assertFalse(session.toString().contains(deviceCredential))
        try { DeviceOAuth.awaitToken("synthetic-client-id", session); fail("Expired session was polled") }
        catch (e: SocialException) { assertTrue(e.message.orEmpty().contains("expiré")) }
    }

    private fun assertRejected(json: String) {
        try { ShareProgress.fromJson(json); fail("Unexpectedly accepted an invalid snapshot") }
        catch (_: IllegalArgumentException) { /* expected */ }
    }

    private class TokenTestContext(base: Context, private val namespace: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "${baseContext.packageName}.platformtest.$namespace"
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("platformtest-$namespace-$name", mode)
    }
}
