package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReviewModeInstrumentedTest {
    private val base get()=InstrumentationRegistry.getInstrumentation().targetContext
    private class Isolated(base:Context,val prefix:String):ContextWrapper(base) {
        val stores=mutableSetOf<String>()
        override fun getApplicationContext():Context=this
        override fun getPackageName()=super.getPackageName()+".reviewfixture"
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(prefix+name,mode).also {stores+=name}
        fun clear(){stores.forEach {super.getSharedPreferences(prefix+it,MODE_PRIVATE).edit().clear().commit()}}
    }
    @Test fun nativeSyncInboxDeletionAndReconnectStayInsideTheDemoNamespace()=runBlocking {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val isolated=Isolated(base,"review-test-${UUID.randomUUID()}-")
        val review=ReviewContext(isolated);val personal=Progress(isolated,sideEffects=false)
        personal.prefs.edit().putString("name","Personal sentinel").putString("friends","[]").commit()
        val personalToken=SecureTokenStore(isolated);personalToken.store("fixture-personal-token-do-not-upload")
        val original=personal.prefs.all.toMap()
        try {
            ReviewAccess.setActive(isolated,true)
            assertTrue(ReviewAccess.context(isolated) is ReviewContext)
            ReviewAccess.prepare(review,Content(base))
            val progress=Progress(review,sideEffects=false)
            val originalXp=progress.xp
            val gateway=ReviewGitHubGateway(review);val sync=GitHubSync(review,gateway)
            val result=sync.synchronize(progress)
            assertTrue(result.social.progress.name.contains("Démo"))
            val inbox=FriendInboxCoordinator(review,progress,sync,FriendInbox(gateway))
            val request=inbox.refresh().pending.single()
            assertTrue(request.profile.name.startsWith("Sam"))
            inbox.accept(request)
            assertEquals(2,CloudProgress.activeFriends(progress.friendRecords()).length())
            val peer=ReviewGitHubGateway.peers().last()
            val outgoing=FriendInbox(gateway).sendRequest(result.social.id,peer.first,UUID.randomUUID().toString(),ReviewAccess.TOKEN)
            assertEquals(setOf(outgoing.id),FriendInbox(gateway).acceptedOutgoing(result.social.id,listOf(outgoing),ReviewAccess.TOKEN))
            val persisted=ReviewGitHubGateway(ReviewContext(isolated))
            assertEquals(result.social.id,JSONObject(persisted.api("GET","/gists/${result.social.id}",ReviewAccess.TOKEN)).getString("id"))
            SocialRules.setDeletionPending(review,true)
            assertEquals(2,sync.deleteRemoteData())
            assertFalse(sync.tokens.hasToken());assertEquals(originalXp,progress.xp)
            assertEquals(0,JSONArray(persisted.api("GET","/gists",ReviewAccess.TOKEN)).length())
            ReviewGitHubGateway.peers().forEach {assertEquals(it.first,JSONObject(persisted.api("GET","/gists/${it.first}",ReviewAccess.TOKEN)).getString("id"))}
            assertEquals("fixture-personal-token-do-not-upload",personalToken.get())
            assertEquals(original,personal.prefs.all)
            sync.connect(ReviewAccess.TOKEN);sync.synchronize(progress)
            assertEquals(2,JSONArray(persisted.api("GET","/gists",ReviewAccess.TOKEN)).length())
            ReviewAccess.setActive(isolated,false)
            assertSame(isolated,ReviewAccess.context(isolated));assertFalse(ReviewAccess.networkPaused)
            assertEquals(original,personal.prefs.all)
        } finally {
            ReviewAccess.setActive(isolated,false)
            SecureTokenStore(review).delete();personalToken.delete();isolated.clear()
        }
    }
    @Test fun demoGatewayRejectsRealCredentialsAndUnknownDestinations()=runBlocking {
        val isolated=Isolated(base,"review-reject-${UUID.randomUUID()}-")
        try {
            val gateway=ReviewGitHubGateway(ReviewContext(isolated))
            try {gateway.api("GET","/user","a-real-looking-personal-token");fail("Credential must be rejected")}
            catch(_:IllegalArgumentException){}
            try {gateway.api("GET","/gists/${"a".repeat(32)}",ReviewAccess.TOKEN);fail("Unknown Gist must not reach GitHub")}
            catch(error:SocialException){assertEquals(404,error.httpStatus)}
            assertTrue(ReviewAccess.matches(ReviewAccess.CODE));assertFalse(ReviewAccess.matches(ReviewAccess.CODE+"0"))
            assertFalse(ReviewAccess.matches("1+"+ReviewAccess.CODE));assertFalse(ReviewAccess.matches(DiagnosticAccess.CODE))
        } finally {isolated.clear()}
    }
}
