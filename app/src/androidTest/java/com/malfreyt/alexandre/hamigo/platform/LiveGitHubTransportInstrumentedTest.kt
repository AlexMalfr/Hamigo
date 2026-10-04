package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.Progress
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URLEncoder
import java.util.UUID

/**
 * Explicit opt-in audit of Android's production TLS/HTTP transport. Never discovers an account's Gists.
 * Run on the emulator with -e liveGithub true. The credential is read only from the app-private file
 * github-transport-test-token, transferred separately through stdin; never through test arguments.
 */
@RunWith(AndroidJUnit4::class)
class LiveGitHubTransportInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun requireExplicitEmulatorAudit() {
        assumeTrue("Live GitHub transport audit is opt-in",isEnabled())
        check(isEmulator()) {"Live GitHub transport audit is restricted to an emulator."}
    }

    @Test fun registeredDeviceFlowReturnsAuthorizationPendingThroughTheRealAndroidTransport()=runBlocking {
        val session=DeviceOAuth.start(GitHubApp.CLIENT_ID)
        assertEquals("https://github.com/login/device",session.verificationUri)
        assertTrue(session.expiresIn>0)
        assertTrue(session.interval>=5)
        // Observe GitHub's polling interval. Neither the device code nor user code is printed.
        delay(session.interval*1000L)
        val reply=GitHubHttp.oauth("/login/oauth/access_token",form(
            "client_id" to GitHubApp.CLIENT_ID,
            "device_code" to session.deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
        ))
        assertEquals("authorization_pending",reply.optString("error"))
        assertFalse(reply.has("access_token"))
    }

    @Test fun secretAuditGistsRoundTripAndMergeTwoFictitiousInstallations()=runBlocking {
        val credentialFile=File(context.filesDir,CREDENTIAL_FILE)
        check(credentialFile.isFile && credentialFile.length() in 10..4098) {"The app-private audit credential file is missing or invalid."}
        val token=credentialFile.readText(Charsets.UTF_8).trim()
        check(token.length in 10..4096 && token.none {it.isWhitespace() || it.isISOControl()}) {"The audit credential has an invalid format."}
        val nonce=UUID.randomUUID().toString()
        val description="hamigo-audit-$nonce"
        val fullFile="hamigo-audit-$nonce-full.json"
        val socialFile="hamigo-audit-$nonce-social.json"
        val created=mutableListOf<CreatedAuditGist>()
        val inventory=File(context.filesDir,INVENTORY_FILE)
        check(!inventory.exists()) {"Recover the previous audit Gists before starting another live audit."}
        val firstContext=IsolatedContext(context,"$nonce-first")
        val secondContext=IsolatedContext(context,"$nonce-second")
        val tokenStore=SecureTokenStore(firstContext)
        var owner:String?=null
        var originalFailure:Throwable?=null
        try {
            // The fixture has its own preference namespace and Keystore alias; real app credentials are untouched.
            tokenStore.store(token)
            assertTrue("Encrypted audit credential did not round-trip",tokenStore.get()==token)
            val user=JSONObject(GitHubHttp.api("GET","/user",token))
            val ownerLogin=user.getString("login")
            owner=ownerLogin
            check(ownerLogin.matches(Regex("[A-Za-z0-9-]{1,100}"))) {"GitHub returned an unexpected audit account identity."}
            val first=Progress(firstContext)
            first.name="Pilote audit fictif"
            first.setDailyGoal(45)
            first.answer("audit-$nonce-question-first",true)
            first.complete("audit-$nonce-lesson-first")
            val initialFull=first.cloudExport()
            CloudProgress.validate(initialFull)
            val initialSocial=first.snapshot().toJson()

            suspend fun create(fileName:String,content:String):JSONObject {
                val reply=JSONObject(GitHubHttp.api("POST","/gists",token,gistBody(description,fileName,content,create=true)))
                val id=reply.getString("id")
                check(id.matches(Regex("[a-fA-F0-9]{5,64}"))) {"GitHub returned an invalid audit Gist identity."}
                // Record only IDs created by this POST before further assertions, so failures still clean up.
                created+=CreatedAuditGist(id,fileName)
                inventory.writeText(JSONObject().put("description",description).put("owner",ownerLogin)
                    .put("gists",JSONArray(created.map {JSONObject().put("id",it.id).put("file",it.fileName)})).toString(),Charsets.UTF_8)
                verifyAuditGist(reply,id,ownerLogin,description,fileName)
                assertTrue("Created Gist content differs from the fictitious payload",contentOf(reply,fileName)==content)
                return reply
            }
            val full=create(fullFile,initialFull)
            val social=create(socialFile,initialSocial)
            assertTrue("Backup and social summary must use separate Gists",full.getString("id")!=social.getString("id"))

            val fetchedFull=JSONObject(GitHubHttp.api("GET","/gists/${full.getString("id")}",token))
            verifyAuditGist(fetchedFull,full.getString("id"),ownerLogin,description,fullFile)
            val second=Progress(secondContext)
            second.answer("audit-$nonce-question-second",true)
            second.complete("audit-$nonce-lesson-second")
            val remote=contentOf(fetchedFull,fullFile)
            CloudProgress.validate(remote)
            second.mergeCloud(remote)
            assertEquals(18,second.xp)
            assertEquals(2,second.totalAnswers)
            assertEquals(2,second.completed.size)
            assertEquals(2,second.reviews.size)
            val mergedFull=second.cloudExport()
            val mergedSocial=second.snapshot().toJson()
            CloudProgress.validate(mergedFull)
            assertFalse("An audit credential must never enter a progression backup",mergedFull.contains(token))
            val patchedFull=JSONObject(GitHubHttp.api("PATCH","/gists/${full.getString("id")}",token,gistBody(description,fullFile,mergedFull)))
            val patchedSocial=JSONObject(GitHubHttp.api("PATCH","/gists/${social.getString("id")}",token,gistBody(description,socialFile,mergedSocial)))
            verifyAuditGist(patchedFull,full.getString("id"),ownerLogin,description,fullFile)
            verifyAuditGist(patchedSocial,social.getString("id"),ownerLogin,description,socialFile)
            assertTrue("PATCH did not preserve the complete fictitious progression",contentOf(patchedFull,fullFile)==mergedFull)
            assertTrue("PATCH did not preserve the fictitious social summary",contentOf(patchedSocial,socialFile)==mergedSocial)

            val finalFull=JSONObject(GitHubHttp.api("GET","/gists/${full.getString("id")}",token))
            val finalSocial=JSONObject(GitHubHttp.api("GET","/gists/${social.getString("id")}",token))
            verifyAuditGist(finalFull,full.getString("id"),ownerLogin,description,fullFile)
            verifyAuditGist(finalSocial,social.getString("id"),ownerLogin,description,socialFile)
            assertTrue("Persisted backup differs from the validated merge",contentOf(finalFull,fullFile)==mergedFull)
            assertTrue("Persisted social summary differs from its snapshot",contentOf(finalSocial,socialFile)==mergedSocial)
            CloudProgress.validate(contentOf(finalFull,fullFile))
            val stored=JSONObject(contentOf(finalFull,fullFile))
            assertEquals(18,stored.getJSONObject("progress").getInt("xp"))
            assertEquals(45,stored.getJSONObject("preferences").getInt("dailyGoal"))
            val summary=JSONObject(contentOf(finalSocial,socialFile))
            assertEquals(18,ShareProgress.fromJson(summary.toString()).xp)
            for(field in listOf("progress","reviews","preferences","syncEvents","friends","token"))
                assertFalse("Private learning field entered the social summary: $field",summary.has(field))
        } catch(failure:Throwable) {
            originalFailure=failure
            throw failure
        } finally {
            var cleanupFailure:Throwable?=null
            fun record(failure:Throwable) {if(cleanupFailure==null)cleanupFailure=failure else cleanupFailure!!.addSuppressed(failure)}
            // DELETE is allowed only for this run's POST IDs, after rechecking owner, nonce and exact file set.
            created.asReversed().forEach {gist ->
                try {
                    val latest=JSONObject(GitHubHttp.api("GET","/gists/${gist.id}",token))
                    verifyAuditGist(latest,gist.id,checkNotNull(owner),description,gist.fileName)
                    GitHubHttp.api("DELETE","/gists/${gist.id}",token)
                    try {
                        GitHubHttp.api("GET","/gists/${gist.id}",token)
                        error("A temporary audit Gist still exists after deletion.")
                    } catch(expected:SocialException) {check(expected.httpStatus==404) {"Deletion of an audit Gist could not be verified."}}
                } catch(failure:Throwable) {record(failure)}
            }
            try {tokenStore.delete()} catch(failure:Throwable) {record(failure)}
            try {firstContext.cleanup();secondContext.cleanup()} catch(failure:Throwable) {record(failure)}
            try {check(!credentialFile.exists() || credentialFile.delete()) {"The private audit credential file could not be removed."}} catch(failure:Throwable) {record(failure)}
            if(cleanupFailure==null)inventory.delete()
            // If cleanup fails, retain only non-secret recovery metadata; never the credential.
            cleanupFailure?.let {if(originalFailure!=null)originalFailure!!.addSuppressed(it) else throw it}
        }
    }

    private data class CreatedAuditGist(val id:String,val fileName:String)
    private fun verifyAuditGist(gist:JSONObject,id:String,owner:String,description:String,fileName:String) {
        check(gist.getString("id")==id && gist.getJSONObject("owner").getString("login").equals(owner,true)) {"Audit Gist ownership could not be verified."}
        check(!gist.getBoolean("public")) {"An audit Gist unexpectedly became public."}
        check(gist.optString("description")==description) {"The audit Gist nonce no longer matches."}
        val files=gist.getJSONObject("files")
        check(files.keys().asSequence().toSet()==setOf(fileName) && files.getJSONObject(fileName).getString("filename")==fileName) {"The audit Gist file set no longer matches."}
    }
    private fun contentOf(gist:JSONObject,fileName:String):String {
        val file=gist.getJSONObject("files").getJSONObject(fileName)
        check(!file.optBoolean("truncated")) {"Fictitious audit payload was unexpectedly truncated."}
        return file.getString("content")
    }
    private fun gistBody(description:String,fileName:String,content:String,create:Boolean=false)=JSONObject()
        .put("description",description).put("files",JSONObject().put(fileName,JSONObject().put("content",content)))
        .apply {if(create)put("public",false)}.toString()
    private fun form(vararg pairs:Pair<String,String>)=pairs.joinToString("&") {"${URLEncoder.encode(it.first,"UTF-8")}=${URLEncoder.encode(it.second,"UTF-8")}"}

    private class IsolatedContext(base:Context,nonce:String):ContextWrapper(base) {
        private val prefix="live-github-audit-$nonce"
        private val names=mutableSetOf<String>()
        override fun getApplicationContext():Context=this
        override fun getPackageName():String="${baseContext.packageName}.audit.${prefix.replace('-','_')}"
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
            val key="$prefix-$name";synchronized(names) {names+=key}
            return baseContext.getSharedPreferences(key,mode).also {prefs->
                if(name=="hamigo" && !prefs.contains("autoSync"))prefs.edit().putBoolean("autoSync",false).putBoolean("reminderEnabled",false).commit()
            }
        }
        fun cleanup() {synchronized(names) {names.toList()}.forEach {baseContext.deleteSharedPreferences(it)}}
    }

    companion object {
        private const val CREDENTIAL_FILE="github-transport-test-token"
        private const val INVENTORY_FILE="github-transport-test-cleanup.json"
        private fun isEnabled()=InstrumentationRegistry.getArguments().getString("liveGithub")=="true"
        private fun isEmulator()=Build.HARDWARE in listOf("ranchu","goldfish") || Build.FINGERPRINT.contains("generic")
        @JvmStatic @AfterClass fun removeCredentialAfterTheOptInClass() {
            if(isEnabled() && isEmulator()) {
                val file=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,CREDENTIAL_FILE)
                check(!file.exists() || file.delete()) {"The app-private live audit credential could not be removed."}
            }
        }
    }
}
