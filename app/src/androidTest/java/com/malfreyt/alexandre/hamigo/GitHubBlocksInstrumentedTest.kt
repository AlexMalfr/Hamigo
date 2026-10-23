package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GitHubBlocksInstrumentedTest {
    @Test fun synchronizationPrunesBlockedIdentityAcrossRenameAndCloudMergeWithoutChangingLearning()=runBlocking {
        fixture {progress,sync,remote->
            val peers=ReviewGitHubGateway.peers().take(2)
            val records=JSONObject()
            peers.forEach {(gist,identity,profile)->records.put(gist,JSONObject().put("deleted",false)
                .put("modifiedAt",System.currentTimeMillis()).put("githubIdentity",identity.toJson()).put("progress",JSONObject(profile.toJson())))}
            progress.saveFriendRecords(records)
            sync.synchronize(progress)
            val before=progress.export();val xp=progress.xp
            remote.blocked=listOf(peers.first().second.copy(login="renamed-on-github"))
            sync.synchronize(progress)
            assertEquals(xp,progress.xp)
            assertEquals(listOf(peers[1].first),CloudProgress.activeFriends(progress.friendRecords()).let {list->(0 until list.length()).map {GitHubSync.gistId(list.getJSONObject(it).getString("gist"))}})
            assertTrue(progress.friendRecords().getJSONObject(peers[0].first).getBoolean("deleted"))
            try {sync.readProfile(peers[0].first);fail("Blocked profile accepted")} catch(_:SocialException){}
            progress.mergeCloud(before);sync.synchronize(progress)
            assertEquals(1,CloudProgress.activeFriends(progress.friendRecords()).length())
            assertFalse(progress.cloudExport().contains("githubBlocks"))
            remote.blocked=emptyList();sync.synchronize(progress)
            assertEquals(1,CloudProgress.activeFriends(progress.friendRecords()).length())
        }
    }
    @Test fun permissionFailureKeepsTheBlockCacheAndDoesNotPublishOrDisconnect()=runBlocking {
        fixture {progress,sync,remote->
            val blocked=ReviewGitHubGateway.peers().first().second
            remote.blocked=listOf(blocked);sync.refreshBlocks(progress)
            remote.fail=404
            val before=progress.export();val writes=remote.writes
            try {sync.synchronize(progress);fail("Missing permission ignored")} catch(e:SocialException) {
                assertTrue(e.message!!.contains("Déconnecte puis reconnecte"))
            }
            assertTrue(sync.isBlocked(blocked));assertTrue(sync.tokens.hasToken())
            assertEquals(before,progress.export());assertEquals(writes,remote.writes)
        }
    }
    @Test fun blockPagesAreCompleteBeforeReplacingThePreviousSnapshot()=runBlocking {
        fixture {progress,sync,remote->
            val old=ReviewGitHubGateway.peers().first().second
            remote.blocked=listOf(old);sync.refreshBlocks(progress)
            remote.blocked=(1..100).map {GitHubIdentity("blocked-$it",it.toLong())}
            remote.failPage=2
            try {sync.refreshBlocks(progress);fail("Partial block list accepted")} catch(_:SocialException){}
            assertTrue(sync.isBlocked(old));assertFalse(sync.isBlocked(GitHubIdentity("blocked-1",1)))
            remote.failPage=null;sync.refreshBlocks(progress)
            assertFalse(sync.isBlocked(old));assertTrue(sync.isBlocked(GitHubIdentity("renamed",1)))
            assertFalse(sync.isBlocked(GitHubIdentity("blocked-1",999)))
        }
    }
    private suspend fun fixture(block:suspend (Progress,GitHubSync,Remote)->Unit) {
        check(Build.HARDWARE in listOf("ranchu","goldfish")||Build.FINGERPRINT.contains("generic"))
        val base=InstrumentationRegistry.getInstrumentation().targetContext;val suffix=UUID.randomUUID().toString()
        val context=ReviewContext(object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("blocks-$suffix-$name",mode)
        })
        val remote=Remote(ReviewGitHubGateway(context));val sync=GitHubSync(context,remote)
        val progress=Progress(context,sideEffects=false)
        progress.prefs.edit().putBoolean("autoSync",false).commit();SocialRules.accept(context)
        sync.connect(ReviewAccess.TOKEN)
        try {block(progress,sync,remote)} finally {sync.tokens.delete()}
    }
    private class Remote(private val delegate:GitHubGateway):GitHubGateway {
        var blocked=emptyList<GitHubIdentity>();var fail:Int?=null;var failPage:Int?=null;var writes=0
        override suspend fun api(method:String,path:String,token:String?,body:String?):String {
            if(path.startsWith("/user/blocks?")) {
                val page=path.substringAfterLast("page=").toInt()
                if(fail!=null||page==failPage)throw SocialException("Isolated block-list failure",fail ?: 503)
                return JSONArray(blocked.drop((page-1)*100).take(100).map {it.toJson()}).toString()
            }
            if(method!="GET")writes++
            return delegate.api(method,path,token,body)
        }
        override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String)=error("No external transport")
    }
}
