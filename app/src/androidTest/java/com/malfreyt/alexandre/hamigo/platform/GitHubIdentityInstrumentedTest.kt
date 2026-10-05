package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GitHubIdentityInstrumentedTest {
    @Test fun ownerMetadataWinsOverIdentityForgedInsideSharedProgress() = runBlocking {
        val content=JSONObject(ShareProgress("Alice",3,0,0).toJson())
            .put("githubIdentity",JSONObject().put("login","attacker").put("id",777))
            .put("avatar_url","https://evil.test/tracking.png")
        val gist=JSONObject().put("owner",JSONObject().put("login","actual-owner").put("id",42))
            .put("files",JSONObject().put(GitHubSync.FILE_NAME,JSONObject().put("content",content.toString())))
        val paths=mutableListOf<String>()
        val gateway=object : GitHubGateway {
            override suspend fun api(method:String,path:String,token:String?,body:String?):String {
                assertEquals("GET",method); paths+=path; return gist.toString()
            }
            override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String = error("Unexpected")
        }
        val profile=GitHubSync(isolatedContext(),gateway).readProfile("https://gist.github.com/forged-name/abcde012345")
        assertEquals(listOf("/gists/abcde012345"),paths)
        assertEquals("Alice",profile.progress.name)
        assertEquals(GitHubIdentity("actual-owner",42L),profile.identity)
        assertEquals("https://avatars.githubusercontent.com/u/42?s=128&v=4",profile.identity?.avatarUrl)
        assertFalse(JSONObject(profile.progress.toJson()).has("githubIdentity"))
    }

    @Test fun missingOwnerKeepsLegacyStatisticsUsableWithoutTrustingPayload() = runBlocking {
        val gist=JSONObject().put("files",JSONObject().put(GitHubSync.FILE_NAME,
            JSONObject().put("content",ShareProgress("Legacy",0,0,0).toJson())))
        val gateway=object : GitHubGateway {
            override suspend fun api(method:String,path:String,token:String?,body:String?) = gist.toString()
            override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String = error("Unexpected")
        }
        val profile=GitHubSync(isolatedContext(),gateway).readProfile("abcde12345")
        assertEquals("Legacy",profile.progress.name); assertNull(profile.identity)
    }

    @Test fun identityCacheEnrichmentSurvivesLegacyMergeAndDoesNotChangeRelationVersion() {
        val old=JSONObject().put("modifiedAt",100L).put("deleted",false)
            .put("progress",JSONObject(ShareProgress("Friend",0,0,0,updatedAt="2026-10-05T10:00:00Z").toJson()))
        val enriched=JSONObject(old.toString()).put("githubIdentity",GitHubIdentity("friend",42L).toJson())
            .put("githubIdentityCheckedAt",200L)
        val renamed=JSONObject(old.toString()).put("githubIdentity",GitHubIdentity("renamed",42L).toJson())
            .put("githubIdentityCheckedAt",300L)
        fun backup(record:JSONObject)=JSONObject().put("app","hamigo").put("schema",2).put("name","Me")
            .put("progress",JSONObject().put("xp",0).put("answers",0).put("correct",0))
            .put("friends",JSONObject().put("abcde12345",record)).toString()
        val a=backup(old);val b=backup(enriched);val c=backup(renamed)
        val merged=CloudProgress.merge(CloudProgress.merge(a,b),c)
        assertEquals(merged,CloudProgress.merge(a,CloudProgress.merge(b,c)))
        assertEquals(merged,CloudProgress.merge(c,CloudProgress.merge(b,a)))
        val records=JSONObject(merged).getJSONObject("friends")
        val entry=CloudProgress.activeFriends(records).getJSONObject(0)
        assertEquals(100L,entry.getLong("modifiedAt"))
        assertEquals("renamed",entry.getJSONObject("githubIdentity").getString("login"))
        val local=CloudProgress.localFriends(CloudProgress.activeFriends(records),JSONObject())
        assertEquals(records.toString(),local.toString())
    }

    @Test fun cachedIdentityRejectsInjectedUrlsAndMalformedIds() {
        for(id in listOf(-1,0,1.5,"42")) {
            assertThrows(Exception::class.java) { GitHubIdentity.fromJson(JSONObject().put("login","valid").put("id",id)) }
        }
        assertNull(GitHubIdentity.fromApi(JSONObject().put("login","bad/name").put("id",42)))
        val safe=GitHubIdentity.fromJson(JSONObject().put("login","valid").put("id",42)
            .put("avatar_url","https://evil.test"))
        assertEquals("https://avatars.githubusercontent.com/u/42?s=128&v=4",safe.avatarUrl)
    }

    private fun isolatedContext(): Context {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val suffix=UUID.randomUUID().toString()
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences = base.getSharedPreferences("identity-test-$suffix-$name",mode)
        }
    }
}
