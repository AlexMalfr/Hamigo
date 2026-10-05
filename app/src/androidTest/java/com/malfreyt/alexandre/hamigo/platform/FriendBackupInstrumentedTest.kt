package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.Progress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FriendBackupInstrumentedTest {
    private val first = "abcde0123456789"
    private val second = "fedcb9876543210"

    @Test fun independentAddsAreUnionedAndRepeatedMergesAreIdempotentAndCommutative() {
        val a = backup(JSONObject().put(first, relation(100)))
        val b = backup(JSONObject().put(second, relation(200)))
        val merged = CloudProgress.merge(a.toString(), b.toString())
        val records = JSONObject(merged).getJSONObject("friends")
        assertEquals(setOf(first, second), records.keys().asSequence().toSet())
        assertEquals(merged, CloudProgress.merge(b.toString(), a.toString()))
        assertEquals(merged, CloudProgress.merge(merged, a.toString()))
    }

    @Test fun deletionSurvivesStaleBackupAndNewerStatisticsWithoutChangingRelationTime() {
        val old = backup(JSONObject().put(first, relation(100)))
        val deleted = backup(JSONObject().put(first, relation(200, deleted = true)))
        val cache = relation(100, cacheDate="2026-10-06T10:00:00Z")
        val staleWithFreshStatistics = backup(JSONObject().put(first, cache))
        val merged = CloudProgress.merge(deleted.toString(), staleWithFreshStatistics.toString())
        assertTrue(JSONObject(merged).getJSONObject("friends").getJSONObject(first).getBoolean("deleted"))
        assertEquals(merged, CloudProgress.merge(merged, old.toString()))
        assertEquals(200L, JSONObject(merged).getJSONObject("friends").getJSONObject(first).getLong("modifiedAt"))
    }

    @Test fun explicitReaddWinsOverDeletionAndEqualTimeRemovalWinsDeterministically() {
        val removed = backup(JSONObject().put(first, relation(200, deleted=true)))
        val equal = backup(JSONObject().put(first, relation(200)))
        val tied = JSONObject(CloudProgress.merge(removed.toString(), equal.toString())).getJSONObject("friends")
        assertTrue(tied.getJSONObject(first).getBoolean("deleted"))
        val newer = backup(JSONObject().put(first, relation(201)))
        val restored = JSONObject(CloudProgress.merge(removed.toString(), newer.toString())).getJSONObject("friends")
        assertFalse(restored.getJSONObject(first).getBoolean("deleted"))
    }

    @Test fun relationshipAndCacheMergeIsAssociativeAcrossRemoveAndReadd() {
        val before = backup(JSONObject().put(first,relation(100,cacheDate="2026-10-06T10:00:00Z"))).toString()
        val removed = backup(JSONObject().put(first,relation(200,deleted=true))).toString()
        val readded = backup(JSONObject().put(first,relation(300))).toString()
        val left = CloudProgress.merge(CloudProgress.merge(before,removed),readded)
        val right = CloudProgress.merge(before,CloudProgress.merge(removed,readded))
        assertEquals(left,right)
        assertFalse(JSONObject(left).getJSONObject("friends").getJSONObject(first).getBoolean("deleted"))
    }

    @Test fun cachedStatsMergeIndependentlyAndSameGistUrlsDeduplicateDuringLegacyMigration() {
        val older = JSONObject().put("gist", "https://gist.github.com/person/$first")
            .put("progress", relation(100).getJSONObject("progress"))
        val fresher = JSONObject().put("gist", first.uppercase()).put("modifiedAt", 100)
            .put("progress", relation(100, cacheDate="2026-10-06T10:00:00Z").getJSONObject("progress"))
        val records = CloudProgress.localFriends(JSONArray(listOf(older, fresher)), JSONObject())
        assertEquals(1, records.length())
        assertEquals(100L, records.getJSONObject(first).getLong("modifiedAt"))
        assertEquals("2026-10-06T10:00:00Z", records.getJSONObject(first).getJSONObject("progress").getString("updatedAt"))
        val merged = JSONObject(CloudProgress.merge(backup(JSONObject().put(first,relation(100))).toString(),backup(records).toString()))
            .getJSONObject("friends").getJSONObject(first)
        assertEquals(100L,merged.getLong("modifiedAt"))
        assertEquals("2026-10-06T10:00:00Z",merged.getJSONObject("progress").getString("updatedAt"))
    }

    @Test fun freshInstallationRestoresRelationshipsAndOldImportPreservesThem() = isolated { context ->
        val old = Progress(context)
        old.prefs.edit().putString("friends", JSONArray(listOf(JSONObject().put("gist", first)
            .put("progress",relation(1).getJSONObject("progress")))).toString()).commit()
        val exported = old.cloudExport()
        val social = JSONObject(old.snapshot().toJson())
        assertFalse(social.has("friends"))
        old.prefs.edit().clear().putBoolean("autoSync", false).commit()
        val fresh = Progress(context)
        assertTrue(fresh.mergeCloud(exported))
        assertEquals(first, fresh.friendRecords().keys().asSequence().single())
        assertEquals(1L, fresh.friendRecords().getJSONObject(first).getLong("modifiedAt"))
        fresh.import(backup(null).toString())
        assertEquals(first, fresh.friendRecords().keys().asSequence().single())
        fresh.import(backup(JSONObject()).toString())
        assertEquals(0, fresh.friendRecords().length())
    }

    @Test fun localTombstoneSurvivesCloudRestoreAndCacheRefresh() = isolated { context ->
        val progress = Progress(context)
        progress.saveFriendRecords(JSONObject().put(first, relation(200, deleted=true)))
        progress.mergeCloud(backup(JSONObject().put(first, relation(100))).toString())
        assertEquals(0, CloudProgress.activeFriends(progress.friendRecords()).length())
        assertEquals(200L, CloudProgress.friendTombstones(progress.friendRecords()).getLong(first))
        val restored = Progress(context)
        assertEquals(progress.friendRecords().toString(), restored.friendRecords().toString())
    }

    @Test fun malformedIdentifiersStatisticsClocksAndOversizedListsRejectAtomically() = isolated { context ->
        val progress = Progress(context)
        val before = progress.cloudExport()
        val cases = listOf(
            JSONObject().put("https://evil.test/profile", relation(1)),
            JSONObject().put(first, relation(0)),
            JSONObject().put(first, relation(1).put("modifiedAt", "1")),
            JSONObject().put(first, relation(1).put("deleted", "false")),
            JSONObject().put(first, relation(1).apply { getJSONObject("progress").put("xp", -1) }),
            JSONObject().put(first, relation(1, deleted=true).put("progress", JSONObject())),
            JSONObject().apply { (1..31).forEach { put(it.toString(16).padStart(5,'0'), relation(it.toLong())) } }
        )
        for (records in cases) {
            assertThrows(Exception::class.java) { progress.import(backup(records).toString()) }
            assertEquals(before, progress.cloudExport())
        }
    }

    @Test fun concurrentlyExceedingThirtyRetainsDeterministicLimitAndOverflowTombstones() {
        val left = JSONObject(); val right = JSONObject()
        (1..40).forEach { id -> (if (id <= 20) left else right).put(id.toString(16).padStart(5,'0'),relation(id.toLong())) }
        val merged = CloudProgress.merge(backup(left).toString(), backup(right).toString())
        val records = JSONObject(merged).getJSONObject("friends")
        assertEquals(30, CloudProgress.activeFriends(records).length())
        assertEquals(10, CloudProgress.friendTombstones(records).length())
        assertEquals(merged, CloudProgress.merge(merged, backup(left).toString()))
        assertEquals(merged, CloudProgress.merge(backup(right).toString(), backup(left).toString()))
        CloudProgress.validate(merged)
    }

    private fun relation(time: Long, deleted: Boolean = false, cacheDate: String="2026-10-05T10:00:00Z") =
        JSONObject().put("modifiedAt",time).put("deleted",deleted).also {
            if (!deleted) it.put("progress",JSONObject(ShareProgress("Équipier",12,1,2,updatedAt=cacheDate).toJson()))
        }
    private fun backup(friends: JSONObject?) = JSONObject().put("app","hamigo").put("schema",2).put("name","Pilote des ondes")
        .put("progress",JSONObject().put("xp",0).put("answers",0).put("correct",0)).also { friends?.let { f -> it.put("friends",f) } }

    private fun isolated(test: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "friend-backup-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("$file-$name",mode)
        }
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("autoSync",false).commit()
        try { test(context) } finally {
            listOf("hamigo", "hamigo_social", "hamigo_secure").forEach { base.deleteSharedPreferences("$file-$it") }
        }
    }
}
