package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import com.malfreyt.alexandre.hamigo.Progress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class FriendInboxRefresh(val pending:List<FriendRequest>,val newlyArrived:List<FriendRequest>)

/** Requests are only sent by explicit actions; restore and background refresh never POST requests. */
class FriendInboxCoordinator(private val context:Context,private val progress:Progress,private val sync:GitHubSync,
    private val gateway:FriendInboxGateway=FriendInbox(),private val now:()->Instant=Instant::now) {
    private data class Binding(val own:String,val login:String,val token:String)
    private fun binding():Binding? {
        val token=runCatching { sync.tokens.get() }.getOrNull() ?: return null
        val login=sync.accountLogin ?: return null
        val own=sync.savedGistUrl?.let { runCatching { GitHubSync.gistId(it) }.getOrNull() } ?: return null
        return Binding(own,login,token)
    }
    private fun stillCurrent(bound:Binding)=binding()==bound
    private fun assertCurrent(bound:Binding) { require(stillCurrent(bound)) { "La connexion GitHub a changé. Réessaie avec le compte actuel." } }
    fun outgoing():List<OutgoingFriendRequestState> = binding()?.let { FriendInboxState.outgoing(progress.socialInboxState(),it.own,now()) } ?: emptyList()
    fun pending():List<FriendRequest> {
        val bound=binding() ?: return emptyList()
        val cached=runCatching { JSONObject(progress.prefs.getString("friendIncomingCache","{}") ?: "{}") }.getOrDefault(JSONObject())
        if(cached.optString("ownGistId")!=bound.own || cached.optString("accountLogin")!=bound.login) return emptyList()
        val requests=cached.optJSONArray("requests") ?: return emptyList()
        val state=progress.socialInboxState();val records=progress.friendRecords()
        return (0 until minOf(requests.length(),100)).mapNotNull { index ->
            runCatching { FriendInboxState.requestFromJson(requests.getJSONObject(index)) }.getOrNull()
        }.filter { it.recipientGistId==bound.own && FriendInboxState.recent(it.createdAt,now()) && !handled(it,state,records) }
    }
    private fun handled(request:FriendRequest,state:JSONObject,records:JSONObject):Boolean {
        val decisions=state.getJSONObject("decisions")
        if(decisions.has(FriendInboxState.decisionKey(request))) return true
        val created=Instant.parse(request.createdAt).toEpochMilli()
        // A skipped newer request must not uncover an older UUID from the same sender.
        if(decisions.keys().asSequence().any { key ->
            val decision=decisions.getJSONObject(key)
            decision.getString("ownGistId")==request.recipientGistId && decision.getString("senderGistId")==request.senderGistId &&
                created<=maxOf(decision.getLong("updatedAt"),Instant.parse(decision.getString("createdAt")).toEpochMilli())
        }) return true
        val relation=records.optJSONObject(request.senderGistId)
        return relation?.optBoolean("deleted")==true && created<=relation.getLong("modifiedAt")
    }
    private fun cache(bound:Binding,requests:List<FriendRequest>) {
        if(!stillCurrent(bound)) return
        progress.prefs.edit().putString("friendIncomingCache",JSONObject().put("ownGistId",bound.own).put("accountLogin",bound.login)
            .put("requests",JSONArray(requests.take(100).map { FriendInboxState.requestJson(it) })).toString()).apply()
    }
    suspend fun refresh():FriendInboxRefresh = operationLock.withLock {
        val bound=binding() ?: return@withLock FriendInboxRefresh(emptyList(),emptyList())
        val previous=pending().map { "${it.senderGistId}:${it.id}" }.toSet()
        val initial=progress.socialInboxState()
        val decisions=initial.getJSONObject("decisions")
        val exclude=decisions.keys().asSequence().filter { key ->
            val decision=decisions.getJSONObject(key)
            decision.getString("ownGistId")==bound.own &&
                (decision.getString("status")=="ignored" || decision.getBoolean("acknowledged"))
        }.map { it.substringAfter(':') }.toSet()
        val incoming=gateway.readIncoming(bound.own,bound.token,exclude)
        assertCurrent(bound)
        val activeOutgoing=FriendInboxState.outgoing(progress.socialInboxState(),bound.own,now()).filter { it.status != "accepted" }.take(100)
        val accepted=if(activeOutgoing.isEmpty()) emptySet() else gateway.acceptedOutgoing(bound.own,activeOutgoing.map { it.request },bound.token)
        assertCurrent(bound)
        var changed=false
        synchronized(Progress.CLOUD_LOCK) {
            var state=progress.socialInboxState()
            for(out in activeOutgoing.filter { it.request.id in accepted && it.status!="accepted" }) {
                state=FriendInboxState.updateOutgoing(state,out.request,"accepted",out.recipientName,now().toEpochMilli());changed=true
            }
            if(changed) progress.saveSocialInboxState(state)
        }
        // Retry ACKs only for a previous explicit acceptance with a fresh, verified source.
        for(request in incoming) {
            val decision=progress.socialInboxState().getJSONObject("decisions").optJSONObject(FriendInboxState.decisionKey(request))
            val relation=progress.friendRecords().optJSONObject(request.senderGistId)
            if(decision?.optString("status")=="accepted" && !decision.optBoolean("acknowledged") &&
                decision.optString("ownGistId")==bound.own && decision.optString("senderGistId")==request.senderGistId &&
                decision.optString("createdAt")==request.createdAt && relation?.optBoolean("deleted")==false &&
                relation.getLong("modifiedAt")<=decision.getLong("updatedAt")) {
                try { acknowledge(bound,request);changed=true }
                catch(e:CancellationException){throw e}
                catch(_:Exception){ /* The accepted local relationship is already durable. */ }
            }
        }
        assertCurrent(bound)
        val state=progress.socialInboxState();val records=progress.friendRecords()
        val visible=incoming.filter { !handled(it,state,records) }
        cache(bound,visible)
        if(changed) ProgressSyncScheduler.enqueue(context)
        FriendInboxRefresh(visible,visible.filter { "${it.senderGistId}:${it.id}" !in previous })
    }
    suspend fun send(recipientGist:String,name:String):OutgoingFriendRequestState = operationLock.withLock {
        val bound=binding() ?: throw SocialException("Connecte GitHub et synchronise ta progression pour envoyer une demande.")
        val target=GitHubSync.gistId(recipientGist)
        require(target!=bound.own)
        val existing=FriendInboxState.outgoing(progress.socialInboxState(),bound.own,now()).firstOrNull { it.request.recipientGistId==target }
        if(existing?.status in setOf("sent","accepted")) return@withLock existing!!
        val request=existing?.request ?: OutgoingFriendRequest(UUID.randomUUID().toString(),bound.own,target,now().toString())
        sendSaved(bound,request,name)
    }
    suspend fun retry(outgoing:OutgoingFriendRequestState):OutgoingFriendRequestState = operationLock.withLock {
        val bound=binding() ?: throw SocialException("Reconnecte GitHub pour réessayer.")
        val current=FriendInboxState.outgoing(progress.socialInboxState(),bound.own,now()).firstOrNull { it.request.id==outgoing.request.id }
            ?: throw SocialException("Cette demande n’est plus disponible ou a expiré.")
        require(current.request.senderGistId==bound.own)
        if(current.status in setOf("sent","accepted")) return@withLock current
        sendSaved(bound,current.request,current.recipientName)
    }
    private suspend fun sendSaved(bound:Binding,request:OutgoingFriendRequest,name:String):OutgoingFriendRequestState {
        assertCurrent(bound)
        synchronized(Progress.CLOUD_LOCK) { progress.saveSocialInboxState(FriendInboxState.updateOutgoing(progress.socialInboxState(),request,"pending",name,now().toEpochMilli())) }
        ProgressSyncScheduler.enqueue(context)
        try {
            val posted=gateway.sendRequest(bound.own,request.recipientGistId,request.id,bound.token)
            assertCurrent(bound)
            require(posted.id==request.id && posted.senderGistId==request.senderGistId && posted.recipientGistId==request.recipientGistId)
            synchronized(Progress.CLOUD_LOCK) { progress.saveSocialInboxState(FriendInboxState.updateOutgoing(progress.socialInboxState(),posted,"sent",name,now().toEpochMilli())) }
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) {
            if(stillCurrent(bound)) synchronized(Progress.CLOUD_LOCK) {
                progress.saveSocialInboxState(FriendInboxState.updateOutgoing(progress.socialInboxState(),request,"failed",name,now().toEpochMilli()))
            }
            throw e
        }
        ProgressSyncScheduler.enqueue(context)
        return outgoing().first { it.request.id==request.id }
    }
    suspend fun accept(request:FriendRequest) = operationLock.withLock {
        val bound=binding() ?: throw SocialException("Reconnecte GitHub pour accepter cette demande.")
        require(request.recipientGistId==bound.own)
        val verified=gateway.readIncoming(bound.own,bound.token).firstOrNull {
            it.id==request.id && it.senderGistId==request.senderGistId && it.author.id==request.author.id
        } ?: throw SocialException("Cette demande n’est plus disponible ou a expiré.")
        assertCurrent(bound)
        synchronized(Progress.CLOUD_LOCK) {
            val state=progress.socialInboxState();val records=progress.friendRecords()
            require(!handled(verified,state,records)) { "Cette demande a déjà été traitée." }
            val previous=records.optJSONObject(verified.senderGistId)
            require(previous?.optBoolean("deleted")==false || CloudProgress.activeFriends(records).length()<CloudProgress.MAX_FRIENDS) { "Ton équipe peut compter jusqu’à trente équipiers." }
            val modified=if(previous?.optBoolean("deleted")==false) previous.getLong("modifiedAt")
                else maxOf(now().toEpochMilli(),(previous?.optLong("modifiedAt") ?: 0)+1)
            records.put(verified.senderGistId,JSONObject().put("modifiedAt",modified).put("deleted",false)
                .put("progress",JSONObject(verified.profile.toJson())).put("githubIdentity",verified.author.toJson())
                .put("githubIdentityCheckedAt",now().toEpochMilli()))
            progress.saveSocialAcceptance(records,FriendInboxState.decision(state,verified,"accepted",now=maxOf(now().toEpochMilli(),modified)))
        }
        cache(bound,pending().filter { FriendInboxState.decisionKey(it)!=FriendInboxState.decisionKey(verified) })
        ProgressSyncScheduler.enqueue(context)
        try { acknowledge(bound,verified) } catch(e:CancellationException){throw e} catch(_:Exception){ /* Retry ACK on a future refresh. */ }
    }
    fun ignore(request:FriendRequest) {
        val bound=binding() ?: return
        if(request.recipientGistId!=bound.own) return
        synchronized(Progress.CLOUD_LOCK) {
            val state=progress.socialInboxState()
            if(!handled(request,state,progress.friendRecords()))
                progress.saveSocialInboxState(FriendInboxState.decision(state,request,"ignored",now=now().toEpochMilli()))
        }
        cache(bound,pending().filter { FriendInboxState.decisionKey(it)!=FriendInboxState.decisionKey(request) })
        ProgressSyncScheduler.enqueue(context)
    }
    private suspend fun acknowledge(bound:Binding,request:FriendRequest) {
        assertCurrent(bound)
        val decision=progress.socialInboxState().getJSONObject("decisions").optJSONObject(FriendInboxState.decisionKey(request))
        val relation=progress.friendRecords().optJSONObject(request.senderGistId)
        require(decision?.optString("status")=="accepted" && relation?.optBoolean("deleted")==false &&
            relation.getLong("modifiedAt")<=decision.getLong("updatedAt")) { "Cet équipier a été retiré depuis l’acceptation." }
        gateway.acknowledgeAccepted(request,bound.own,bound.token)
        assertCurrent(bound)
        synchronized(Progress.CLOUD_LOCK) {
            val state=progress.socialInboxState();val old=state.getJSONObject("decisions").optJSONObject(FriendInboxState.decisionKey(request))
            if(old?.optString("status")=="accepted") progress.saveSocialInboxState(
                FriendInboxState.decision(state,request,"accepted",acknowledged=true,now=now().toEpochMilli()))
        }
        ProgressSyncScheduler.enqueue(context)
    }
    companion object { private val operationLock=Mutex() }
}
