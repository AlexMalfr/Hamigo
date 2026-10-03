package com.malfreyt.alexandre.hamigo.platform

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncInstrumentedTest {
    @Test fun disjointOfflineAttemptsMergeAdditivelyAndRepeatedPullsDoNotAwardAgain() {
        val left = backup(100, 8, 6)
        val right = backup(100, 8, 6)
        CloudProgress.recordEvent(left.getJSONObject("progress"), "2026-10-03", 3, 1, 1, awardKey = "answer:first:2026-10-03")
        CloudProgress.recordEvent(right.getJSONObject("progress"), "2026-10-03", 3, 1, 1, awardKey = "answer:second:2026-10-03")
        val merged = CloudProgress.merge(left.toString(), right.toString())
        val state = JSONObject(merged).getJSONObject("progress")
        assertEquals(106, state.getInt("xp")); assertEquals(10, state.getInt("answers")); assertEquals(8, state.getInt("correct"))
        assertEquals(6, state.getJSONObject("dailyXp").getInt("2026-10-03"))
        assertEquals(merged, CloudProgress.merge(merged, right.toString()))
        assertEquals(merged, CloudProgress.merge(right.toString(), left.toString()))
    }

    @Test fun sameQuestionDayAndLessonBonusAreDeduplicatedAcrossDevices() {
        val left = backup(0, 0, 0); val right = backup(0, 0, 0)
        for (wrapper in listOf(left, right)) {
            val state = wrapper.getJSONObject("progress")
            CloudProgress.recordEvent(state, "2026-10-03", 3, 1, 1, awardKey = "answer:same:2026-10-03")
            CloudProgress.recordEvent(state, "2026-10-03", 6, completed = "c01-l01")
        }
        val state = JSONObject(CloudProgress.merge(left.toString(), right.toString())).getJSONObject("progress")
        assertEquals(9, state.getInt("xp")); assertEquals(2, state.getInt("answers"))
        assertEquals("c01-l01", state.getJSONArray("completed").getString(0))
    }

    @Test fun completedHistoricalLessonCannotReceiveAnotherCloudBonus() {
        val left = backup(80, 6, 4)
        left.getJSONObject("progress").getJSONObject("syncBase").put("completed", JSONArray(listOf("already")))
        val right = backup(0, 0, 0)
        CloudProgress.recordEvent(right.getJSONObject("progress"), "2026-10-03", 6, completed = "already")
        assertEquals(80, JSONObject(CloudProgress.merge(left.toString(), right.toString())).getJSONObject("progress").getInt("xp"))
    }

    @Test fun historicalQuestionAwardCannotReceiveAnotherBonusDuringFirstCloudRestore() {
        val oldPhone=backup(80,6,4).put("schema",1)
        val state=oldPhone.getJSONObject("progress")
        state.remove("syncBase"); state.remove("syncEvents")
        state.getJSONObject("awarded").put("already","2026-10-03")
        state.getJSONObject("dailyXp").put("2026-10-03",3)
        val newPhone=backup(0,0,0)
        CloudProgress.recordEvent(newPhone.getJSONObject("progress"),"2026-10-03",3,1,1,awardKey="answer:already:2026-10-03")
        val merged=CloudProgress.merge(oldPhone.toString(),newPhone.toString())
        val result=JSONObject(merged).getJSONObject("progress")
        assertEquals(80,result.getInt("xp")); assertEquals(7,result.getInt("answers")); assertEquals(5,result.getInt("correct"))
        assertEquals(3,result.getJSONObject("dailyXp").getInt("2026-10-03"))
        assertEquals(merged,CloudProgress.merge(merged,newPhone.toString()))
        assertEquals(merged,CloudProgress.merge(newPhone.toString(),oldPhone.toString()))
    }

    @Test fun malformedCollectionsAreRejectedBeforeRestoreCanCorruptLocalState() {
        val valid=backup(1,1,1)
        for(key in listOf("syncBase","syncEvents","reviews","awarded","dailyXp","completed")) {
            val invalid=JSONObject(valid.toString()).apply { getJSONObject("progress").put(key,"oops") }
            assertThrows(Exception::class.java) { CloudProgress.validate(invalid.toString()) }
            assertThrows(Exception::class.java) { CloudProgress.merge(valid.toString(),invalid.toString()) }
        }
        assertThrows(Exception::class.java) { CloudProgress.validate(JSONObject(valid.toString()).put("preferences","oops").toString()) }
    }

    @Test fun newestReviewAndExplicitProfilePreferencesWinWithoutResettingTotals() {
        val left = backup(90, 9, 7).put("name", "Téléphone").put("profileUpdatedAt", 200L)
        left.put("preferences", JSONObject().put("dailyGoal", 45)).put("preferencesUpdatedAt", 200L)
        left.getJSONObject("progress").put("reviews", JSONObject().put("card", review(100, 5000)))
        val right = backup(10, 1, 1).put("name", "Défaut").put("profileUpdatedAt", 0)
        right.getJSONObject("progress").put("reviews", JSONObject().put("card", review(300, 1000)))
        val merged = JSONObject(CloudProgress.merge(left.toString(), right.toString()))
        assertEquals("Téléphone", merged.getString("name")); assertEquals(45, merged.getJSONObject("preferences").getInt("dailyGoal"))
        assertEquals(1000L, merged.getJSONObject("progress").getJSONObject("reviews").getJSONObject("card").getLong("due"))
        assertEquals(90, merged.getJSONObject("progress").getInt("xp"))
    }

    @Test fun legacyBackupsUseLargestHistoricalBaselineAndUnionCompletedLessons() {
        val left = backup(70, 10, 6).put("schema", 1)
        val right = backup(40, 5, 4).put("schema", 1)
        left.getJSONObject("progress").remove("syncBase"); left.getJSONObject("progress").remove("syncEvents")
        right.getJSONObject("progress").remove("syncBase"); right.getJSONObject("progress").remove("syncEvents")
        left.getJSONObject("progress").put("completed", JSONArray(listOf("first")))
        right.getJSONObject("progress").put("completed", JSONArray(listOf("second")))
        val state = JSONObject(CloudProgress.merge(left.toString(), right.toString())).getJSONObject("progress")
        assertEquals(70, state.getInt("xp")); assertEquals(10, state.getInt("answers"))
        assertEquals(2, state.getJSONArray("completed").length())
    }

    @Test fun malformedCloudPreferencesAndCountersAreRejectedBeforeImport() {
        val valid = backup(1, 1, 1)
        val cases = listOf(
            JSONObject(valid.toString()).put("preferences", JSONObject().put("githubToken", "not-allowed")),
            JSONObject(valid.toString()).put("preferences", JSONObject().put("reminderHour", 24)),
            JSONObject(valid.toString()).apply { getJSONObject("progress").put("xp", -1) },
            JSONObject(valid.toString()).apply { getJSONObject("progress").put("answers", "1") },
            JSONObject(valid.toString()).apply { getJSONObject("progress").put("dailyXp", JSONObject().put("not-a-day", 2)) }
        )
        cases.forEach { value ->
            try { CloudProgress.merge(valid.toString(), value.toString()); fail("Invalid cloud data accepted") }
            catch (_: Exception) { /* no file, account, or network touched */ }
        }
    }

    @Test fun invitationLinksRoundTripAndRejectOtherHostsAmbiguousQueriesAndCredentials() {
        val id = "0123456789abcdef0123456789abcdef"
        val link = FriendInvite.link("https://gist.github.com/ami/$id")
        assertEquals("https://alexmalfr.github.io/hamigo/?invite=$id", link)
        assertEquals(id, FriendInvite.parse(link)); assertEquals(id, FriendInvite.parse("hamigo://join?invite=$id"))
        listOf("http://alexmalfr.github.io/hamigo/?invite=$id", "https://other.github.io/hamigo/?invite=$id",
            "https://alexmalfr.github.io.evil.test/hamigo/?invite=$id", "$link&invite=$id", "$link&x=1",
            "$link#fragment", "https://user@alexmalfr.github.io/hamigo/?invite=$id", "hamigo://other?invite=$id").forEach { assertNull(FriendInvite.parse(it)) }
    }

    @Test fun generatedQrCanBeDecodedBackIntoTheSameHttpsInvitation() {
        val link = FriendInvite.link("0123456789abcdef0123456789abcdef")
        val bitmap: Bitmap = FriendInvite.qr(link, 640)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
            assertEquals(link, decoded.text)
        } finally { bitmap.recycle() }
    }

    private fun backup(xp: Int, answers: Int, correct: Int): JSONObject {
        val state = JSONObject().put("schema", 1).put("xp", xp).put("answers", answers).put("correct", correct)
            .put("dailyXp", JSONObject()).put("completed", JSONArray()).put("reviews", JSONObject()).put("awarded", JSONObject())
        CloudProgress.ensureLedger(state)
        return JSONObject().put("app", "hamigo").put("schema", 2).put("name", "Ami")
            .put("profileUpdatedAt", 0).put("preferencesUpdatedAt", 0).put("preferences", JSONObject()).put("progress", state)
    }
    private fun review(updated: Long, due: Long) = JSONObject().put("updatedAt", updated).put("due", due)
        .put("interval", 1.0).put("ease", 2.5).put("repetitions", 1).put("lapses", 0)
}
