package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.Progress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FriendInboxCoordinatorInstrumentedTest {
    private val own="abcde12345"
    private val peer="fedcb54321"
    private val fixed=Instant.now()
    private fun request(sender:String=peer,created:Instant=fixed.minusSeconds(20),uuid:String=UUID.randomUUID().toString()) =
        FriendRequest(uuid,sender,own,created.toString(),GitHubIdentity("friend",42L),ShareProgress("Friend",9,1,0),100L)

    @Test fun restoredFailedAndPendingRequestsNeverTriggerAutomaticPostAndRetryReusesUuid() = runBlocking {
        fixture { progress,_,gateway,coordinator ->
            val request=OutgoingFriendRequest(UUID.randomUUID().toString(),own,peer,fixed.toString())
            progress.saveSocialInboxState(FriendInboxState.updateOutgoing(FriendInboxState.empty(),request,"pending","Friend",fixed.toEpochMilli()))
            val export=progress.export()
            progress.import(export)
            coordinator.refresh()
            assertEquals(0,gateway.sends)
            val failed=FriendInboxState.updateOutgoing(progress.socialInboxState(),request,"failed","Friend",fixed.toEpochMilli()+1)
            progress.saveSocialInboxState(failed)
            coordinator.refresh();assertEquals(0,gateway.sends)
            coordinator.retry(coordinator.outgoing().single())
            assertEquals(listOf(request.id),gateway.sentIds)
            assertEquals("sent",coordinator.outgoing().single().status)
        }
    }

    @Test fun acceptancePersistsBeforeAckAndRemovedFriendCannotReappearOrAckFromOldRequest() = runBlocking {
        fixture { progress,_,gateway,coordinator ->
            val request=request();gateway.incoming=listOf(request);gateway.failAck=true
            coordinator.refresh();coordinator.accept(request)
            assertEquals(1,gateway.acks)
            val records=progress.friendRecords();assertFalse(records.getJSONObject(peer).getBoolean("deleted"))
            val decision=progress.socialInboxState().getJSONObject("decisions").getJSONObject(FriendInboxState.decisionKey(request))
            assertEquals("accepted",decision.getString("status"));assertFalse(decision.getBoolean("acknowledged"))
            records.put(peer,JSONObject().put("modifiedAt",fixed.toEpochMilli()+1).put("deleted",true))
            progress.saveFriendRecords(records);gateway.failAck=false
            coordinator.refresh()
            assertEquals(1,gateway.acks)
            assertTrue(progress.friendRecords().getJSONObject(peer).getBoolean("deleted"))
            assertTrue(coordinator.pending().isEmpty())
        }
    }

    @Test fun failedAckIsRetriedForDurableAcceptedFriendButDoesNotResendRequest() = runBlocking {
        fixture { progress,_,gateway,coordinator ->
            val request=request();gateway.incoming=listOf(request);gateway.failAck=true
            coordinator.accept(request);gateway.failAck=false;coordinator.refresh()
            assertEquals(2,gateway.acks);assertEquals(0,gateway.sends)
            assertTrue(progress.socialInboxState().getJSONObject("decisions").getJSONObject(FriendInboxState.decisionKey(request)).getBoolean("acknowledged"))
            coordinator.refresh();assertEquals(2,gateway.acks)
        }
    }

    @Test fun ignoredNewestUuidCannotRevealOlderUuidFromSameSenderAndDoesNotPublishReply() = runBlocking {
        fixture { _,_,gateway,coordinator ->
            val newest=request();val older=request(created=fixed.minusSeconds(200))
            gateway.incoming=listOf(newest);coordinator.refresh();coordinator.ignore(newest)
            gateway.incoming=listOf(older);val result=coordinator.refresh()
            assertTrue(result.pending.isEmpty());assertTrue(coordinator.pending().isEmpty())
            assertEquals(0,gateway.sends);assertEquals(0,gateway.acks)
            gateway.incoming=listOf(request(created=fixed.plusSeconds(1)))
            assertEquals(1,coordinator.refresh().pending.size)
        }
    }

    @Test fun copiedUuidFromAnotherSenderCannotHideAuthenticRequest() = runBlocking {
        fixture { _,_,gateway,coordinator ->
            val attacker=request(sender="aabbcc12345")
            val actual=request(uuid=attacker.id)
            gateway.incoming=listOf(attacker);coordinator.refresh();coordinator.ignore(attacker)
            gateway.incoming=listOf(actual)
            assertEquals(peer,coordinator.refresh().pending.single().senderGistId)
        }
    }

    @Test fun accountSwitchHidesOldCachedInboxAndOutgoingAndDoesNotPostRestoredRecords() = runBlocking {
        fixture { progress,sync,gateway,coordinator ->
            gateway.incoming=listOf(request());coordinator.refresh();assertEquals(1,coordinator.pending().size)
            val outgoing=OutgoingFriendRequest(UUID.randomUUID().toString(),own,peer,fixed.toString())
            progress.saveSocialInboxState(FriendInboxState.updateOutgoing(FriendInboxState.empty(),outgoing,"failed","Friend",fixed.toEpochMilli()))
            val social=syncContextPreferences(progress)
            social.edit().putString("ownerLogin","different").putString("ownGistId","112233aabb").putString("ownGistUrl","https://gist.github.com/112233aabb").commit()
            assertTrue(coordinator.pending().isEmpty());assertTrue(coordinator.outgoing().isEmpty())
            gateway.incoming=emptyList();coordinator.refresh();assertEquals(0,gateway.sends);assertEquals(0,gateway.acks)
        }
    }

    @Test fun localAvatarCacheIsPreservedAcrossCloudMergeButNeverWrittenIntoBackup() = runBlocking {
        fixture { progress,_,gateway,coordinator ->
            val request=request();gateway.incoming=listOf(request);coordinator.accept(request)
            assertEquals(42L,progress.friendRecords().getJSONObject(peer).getJSONObject("githubIdentity").getLong("id"))
            val backup=progress.export()
            assertFalse(JSONObject(backup).getJSONObject("friends").getJSONObject(peer).has("githubIdentity"))
            progress.mergeCloud(backup)
            assertEquals(42L,progress.friendRecords().getJSONObject(peer).getJSONObject("githubIdentity").getLong("id"))
            val legacy=JSONObject(backup).apply { remove("socialInbox") }
            progress.mergeCloud(legacy.toString())
            assertEquals("accepted",progress.socialInboxState().getJSONObject("decisions").getJSONObject(FriendInboxState.decisionKey(request)).getString("status"))
        }
    }

    @Test fun stateMergeIsCommutativeAssociativeAndAcceptedOutgoingCannotBeDowngraded() {
        val request=request();val out=OutgoingFriendRequest(UUID.randomUUID().toString(),own,peer,fixed.toString())
        val a=FriendInboxState.decision(FriendInboxState.empty(),request,"ignored",now=fixed.toEpochMilli())
        val b=FriendInboxState.updateOutgoing(FriendInboxState.empty(),out,"accepted","Friend",fixed.toEpochMilli())
        val c=FriendInboxState.updateOutgoing(FriendInboxState.empty(),out,"failed","Friend",fixed.toEpochMilli()+50)
        val left=FriendInboxState.merge(FriendInboxState.merge(a,b,fixed),c,fixed)
        val right=FriendInboxState.merge(a,FriendInboxState.merge(b,c,fixed),fixed)
        assertEquals(left.toString(),right.toString())
        assertEquals(left.toString(),FriendInboxState.merge(c,FriendInboxState.merge(b,a,fixed),fixed).toString())
        assertEquals("accepted",FriendInboxState.outgoing(left,own,fixed).single().status)
    }

    @Test fun pruningRetainsRecentDecisionsAndDropsExpiredHistory() {
        val old=request(created=fixed.minusSeconds(70L*86400))
        val recent=request()
        var state=FriendInboxState.decision(FriendInboxState.empty(),old,"ignored",now=fixed.minusSeconds(70L*86400).toEpochMilli())
        state=FriendInboxState.decision(state,recent,"ignored",now=fixed.toEpochMilli())
        assertFalse(state.getJSONObject("decisions").has(FriendInboxState.decisionKey(old)))
        assertTrue(state.getJSONObject("decisions").has(FriendInboxState.decisionKey(recent)))
    }

    private val socialPreferences=mutableMapOf<Progress,SharedPreferences>()
    private fun syncContextPreferences(progress:Progress)=socialPreferences.getValue(progress)
    private suspend fun fixture(block:suspend (Progress,GitHubSync,FakeGateway,FriendInboxCoordinator)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val suffix=UUID.randomUUID().toString()
        val context=object : ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getPackageName():String=base.packageName+".inbox."+suffix
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("coordinator-$suffix-$name",mode)
        }
        val progress=Progress(context)
        progress.prefs.edit().putBoolean("autoSync",false).commit()
        val sync=GitHubSync(context)
        sync.tokens.store("synthetic-inbox-test-token")
        val social=context.getSharedPreferences("hamigo_social",Context.MODE_PRIVATE)
        social.edit().putString("ownerLogin","me").putLong("ownerId",99L)
            .putString("ownGistId",own).putString("ownGistUrl","https://gist.github.com/$own").commit()
        socialPreferences[progress]=social
        val gateway=FakeGateway()
        try { block(progress,sync,gateway,FriendInboxCoordinator(context,progress,sync,gateway){fixed}) }
        finally {sync.tokens.delete();socialPreferences.remove(progress)}
    }

    private inner class FakeGateway : FriendInboxGateway {
        var incoming=emptyList<FriendRequest>()
        var sends=0;var acks=0;var failAck=false
        val sentIds=mutableListOf<String>()
        override suspend fun readIncoming(ownSocialGist:String,token:String,handledIds:Set<String>) =
            incoming.filter { it.id !in handledIds && "${it.senderGistId}:${it.id}" !in handledIds }
        override suspend fun sendRequest(ownSocialGist:String,recipientSocialGist:String,requestId:String,token:String):OutgoingFriendRequest {
            sends++;sentIds+=requestId
            return OutgoingFriendRequest(requestId,ownSocialGist,recipientSocialGist,fixed.toString())
        }
        override suspend fun acknowledgeAccepted(request:FriendRequest,ownSocialGist:String,token:String) {
            acks++;if(failAck) throw SocialException("Offline")
        }
        override suspend fun acceptedOutgoing(ownSocialGist:String,outgoing:List<OutgoingFriendRequest>,token:String)=emptySet<String>()
    }
}
