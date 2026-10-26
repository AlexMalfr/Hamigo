package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.HamigoLinks
import com.malfreyt.alexandre.hamigo.Progress
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** All accounts and Gists are fixtures. No request can reach GitHub. */
@RunWith(AndroidJUnit4::class)
class SocialPrivacyInstrumentedTest {
    @Before fun emulatorOnly() {check(Build.HARDWARE in listOf("ranchu","goldfish"))}
    private fun context():Context {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="privacy-test-${UUID.randomUUID()}-"
        return object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=base.getSharedPreferences(prefix+name,mode)
        }
    }
    private fun gist(id:String,file:String,owner:String="tester",public:Boolean=false)=JSONObject()
        .put("id",id).put("public",public).put("owner",JSONObject().put("login",owner).put("id",42))
        .put("files",JSONObject().put(file,JSONObject().put("content","fixture")))
    private inner class Remote:GitHubGateway {
        val gists=linkedMapOf("aaaaa" to gist("aaaaa",GitHubSync.BACKUP_FILE_NAME),"bbbbb" to gist("bbbbb",GitHubSync.FILE_NAME))
        val calls=mutableListOf<String>()
        var failDelete:String?=null
        var missingDelete:String?=null
        override suspend fun api(method:String,path:String,token:String?,body:String?):String {
            calls+="$method $path"
            if(path=="/user")return JSONObject().put("login","tester").put("id",42).toString()
            if(path.startsWith("/gists?"))return JSONArray(gists.values.toList()).toString()
            val id=path.removePrefix("/gists/")
            if(method=="DELETE") {
                if(id==failDelete)throw SocialException("Offline fixture",503)
                gists.remove(id)
                if(id==missingDelete)throw SocialException("Already deleted fixture",404)
                return ""
            }
            check(method=="GET"){"Unexpected write: $method $path"}
            return gists[id]?.toString() ?: throw SocialException("Missing fixture",404)
        }
        override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String=error("No raw downloads")
    }
    @Test fun deletionRequiresConfirmationAndPreservesLocalLearning()=runBlocking {
        val c=context();val remote=Remote();val sync=GitHubSync(c,remote)
        val progress=Progress(c,sideEffects=false);progress.name="Local learner"
        val before=progress.export();sync.connect("fixture_token_12345")
        assertTrue(runCatching {sync.deleteRemoteData()}.isFailure)
        assertFalse(remote.calls.any {it.startsWith("DELETE")})
        remote.gists["ccccc"]=gist("ccccc","notes.txt")
        remote.gists["ddddd"]=gist("ddddd",GitHubSync.FILE_NAME,"someone-else")
        remote.gists["eeeee"]=gist("eeeee",GitHubSync.FILE_NAME,public=true)
        SocialRules.setDeletionPending(c,true)
        assertEquals(2,sync.deleteRemoteData())
        assertEquals(setOf("ccccc","ddddd","eeeee"),remote.gists.keys)
        assertFalse(sync.tokens.hasToken());assertFalse(SocialRules.deletionPending(c))
        assertEquals(before,progress.export())
    }
    @Test fun mixedGistPreflightPreventsEveryDelete()=runBlocking {
        val c=context();val remote=Remote();val sync=GitHubSync(c,remote);sync.connect("fixture_token_12345")
        remote.gists.getValue("bbbbb").getJSONObject("files").put("keep.txt",JSONObject().put("content","personal"))
        SocialRules.setDeletionPending(c,true)
        assertTrue(runCatching {sync.deleteRemoteData()}.isFailure)
        assertFalse(remote.calls.any {it.startsWith("DELETE")})
        assertTrue(sync.tokens.hasToken());assertTrue(SocialRules.deletionPending(c))
    }
    @Test fun partialDeletionPausesWritesAcrossClientRecreationAndCanRetry()=runBlocking {
        val c=context();val remote=Remote();var sync=GitHubSync(c,remote);sync.connect("fixture_token_12345")
        SocialRules.setDeletionPending(c,true);remote.failDelete="bbbbb"
        assertTrue(runCatching {sync.deleteRemoteData()}.isFailure)
        assertEquals(setOf("bbbbb"),remote.gists.keys)
        assertTrue(sync.tokens.hasToken());assertTrue(SocialRules.deletionPending(c))
        sync=GitHubSync(c,remote)
        assertTrue(runCatching {sync.push(ShareProgress("Local",1,0,0))}.isFailure)
        assertFalse(remote.calls.any {it.startsWith("POST")||it.startsWith("PATCH")})
        remote.failDelete=null
        assertEquals(1,sync.deleteRemoteData());assertFalse(sync.tokens.hasToken())
    }
    @Test fun alreadyDeletedGistIsAnIdempotentSuccess()=runBlocking {
        val c=context();val remote=Remote();val sync=GitHubSync(c,remote);sync.connect("fixture_token_12345")
        SocialRules.setDeletionPending(c,true);remote.missingDelete="aaaaa"
        assertEquals(2,sync.deleteRemoteData());assertFalse(SocialRules.deletionPending(c))
    }
    @Test fun feedbackReturnAcceptsOnlyTheDedicatedEmptyRoute() {
        listOf("${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://return","${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://return/").forEach {assertTrue(HamigoLinks.isFeedbackReturn(it))}
        listOf("${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://return?invite=abcde","${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://return/extra","${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://user@return","${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://return:80","${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://return#x","https://return","${com.malfreyt.alexandre.hamigo.BuildConfig.LINK_SCHEME}://invite/abcde").forEach {assertFalse(it,HamigoLinks.isFeedbackReturn(it))}
    }
}
