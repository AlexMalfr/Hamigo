package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class OutgoingFriendRequestState(val request: OutgoingFriendRequest, val status: String,
    val recipientName: String, val updatedAt: Long)

/** Durable decisions live in the backup; pending received requests remain a verified local cache. */
object FriendInboxState {
    private const val MAX_RECORDS=2_000
    private val gistPattern=Regex("[a-f0-9]{5,64}")
    private val statuses=setOf("pending","failed","sent","accepted")
    private fun id(value:String):String { require(UUID.fromString(value).toString()==value);return value }
    private fun gist(value:String):String { require(gistPattern.matches(value));return value }
    private fun time(value:String):String { require(value.length<=64);require(Instant.parse(value)>=Instant.EPOCH);return value }
    private fun stamp(value:Any?):Long {
        require(value is Number && value.toDouble().isFinite() && value.toDouble()%1.0==0.0 && value.toDouble() in 1.0..9_007_199_254_740_991.0)
        return value.toLong()
    }
    fun empty()=JSONObject().put("schema",1).put("decisions",JSONObject()).put("outgoing",JSONObject())
    fun validate(state:JSONObject):JSONObject {
        require(state.optInt("schema")==1)
        require(state.keys().asSequence().all { it in setOf("schema","decisions","outgoing") })
        val decisions=state.getJSONObject("decisions");val outgoing=state.getJSONObject("outgoing")
        require(decisions.length()+outgoing.length()<=MAX_RECORDS)
        decisions.keys().forEach { key ->
            val parts=key.split(':');require(parts.size==3);gist(parts[0]);gist(parts[1]);id(parts[2])
            val entry=decisions.getJSONObject(key)
            require(entry.keys().asSequence().all { it in setOf("ownGistId","senderGistId","createdAt","status","updatedAt","acknowledged") })
            gist(entry.getString("ownGistId"));gist(entry.getString("senderGistId"))
            require(parts[0]==entry.getString("ownGistId") && parts[1]==entry.getString("senderGistId"))
            require(entry.getString("ownGistId")!=entry.getString("senderGistId"))
            time(entry.getString("createdAt"));stamp(entry.opt("updatedAt"))
            require(entry.getString("status") in setOf("accepted","ignored"))
            require(entry.opt("acknowledged") is Boolean)
            require(!entry.getBoolean("acknowledged") || entry.getString("status")=="accepted")
        }
        outgoing.keys().forEach { key ->
            id(key);val entry=outgoing.getJSONObject(key)
            require(entry.keys().asSequence().all { it in setOf("senderGistId","recipientGistId","createdAt","status","updatedAt","recipientName") })
            gist(entry.getString("senderGistId"));gist(entry.getString("recipientGistId"))
            require(entry.getString("senderGistId")!=entry.getString("recipientGistId"))
            time(entry.getString("createdAt"));stamp(entry.opt("updatedAt"))
            require(entry.getString("status") in statuses)
            require(entry.opt("recipientName") is String && entry.getString("recipientName").length in 1..48)
        }
        return state
    }
    fun prune(state:JSONObject,now:Instant=Instant.now()):JSONObject {
        validate(state)
        val result=empty()
        val cutoff=now.minus(Duration.ofDays(60)).toEpochMilli()
        val entries=listOf("decisions","outgoing").flatMap { collection ->
            val source=state.getJSONObject(collection)
            source.keys().asSequence().map { Triple(collection,it,source.getJSONObject(it)) }.toList()
        }.filter { it.third.getLong("updatedAt")>=cutoff }
            .sortedWith(compareByDescending<Triple<String,String,JSONObject>> { it.third.getLong("updatedAt") }.thenBy { it.first }.thenBy { it.second })
            .take(MAX_RECORDS)
        entries.sortedWith(compareBy<Triple<String,String,JSONObject>> { it.first }.thenBy { it.second }).forEach {
            result.getJSONObject(it.first).put(it.second,JSONObject(it.third.toString()))
        }
        return result
    }
    fun merge(left:JSONObject,right:JSONObject,now:Instant=Instant.now()):JSONObject {
        validate(left);validate(right)
        val result=empty()
        for(collection in listOf("decisions","outgoing")) {
            val a=left.getJSONObject(collection);val b=right.getJSONObject(collection)
            (a.keys().asSequence().toSet()+b.keys().asSequence().toSet()).sorted().forEach { key ->
                val first=a.optJSONObject(key);val second=b.optJSONObject(key)
                val chosen=when {
                    first==null -> second!!
                    second==null -> first
                    collection=="outgoing" && first.getString("status")=="accepted" && second.getString("status")!="accepted" -> first
                    collection=="outgoing" && second.getString("status")=="accepted" && first.getString("status")!="accepted" -> second
                    collection=="outgoing" && first.getString("status")=="sent" && second.getString("status") in setOf("pending","failed") -> first
                    collection=="outgoing" && second.getString("status")=="sent" && first.getString("status") in setOf("pending","failed") -> second
                    first.getLong("updatedAt")>second.getLong("updatedAt") -> first
                    second.getLong("updatedAt")>first.getLong("updatedAt") -> second
                    canonical(first)>=canonical(second) -> first
                    else -> second
                }
                result.getJSONObject(collection).put(key,JSONObject(chosen.toString()))
            }
        }
        // Allow the temporary union to be larger than the on-disk bound, then retain newest records.
        return pruneUnion(result,now)
    }
    private fun pruneUnion(state:JSONObject,now:Instant):JSONObject {
        val entries=listOf("decisions","outgoing").flatMap { collection ->
            val source=state.getJSONObject(collection)
            source.keys().asSequence().map { Triple(collection,it,source.getJSONObject(it)) }.toList()
        }.filter { it.third.getLong("updatedAt")>=now.minus(Duration.ofDays(60)).toEpochMilli() }
            .sortedWith(compareByDescending<Triple<String,String,JSONObject>> { it.third.getLong("updatedAt") }.thenBy { it.first }.thenBy { it.second }).take(MAX_RECORDS)
        val result=empty()
        entries.sortedWith(compareBy<Triple<String,String,JSONObject>>{it.first}.thenBy{it.second}).forEach {
            result.getJSONObject(it.first).put(it.second,JSONObject(it.third.toString()))
        }
        return validate(result)
    }
    fun decision(state:JSONObject,request:FriendRequest,status:String,acknowledged:Boolean=false,now:Long=System.currentTimeMillis()):JSONObject {
        require(status in setOf("accepted","ignored"));id(request.id)
        val result=JSONObject(state.toString())
        val key=decisionKey(request)
        val previous=result.getJSONObject("decisions").optJSONObject(key)
        result.getJSONObject("decisions").put(key,JSONObject().put("ownGistId",request.recipientGistId)
            .put("senderGistId",request.senderGistId).put("createdAt",request.createdAt).put("status",status)
            .put("updatedAt",maxOf(now,(previous?.optLong("updatedAt") ?: 0)+1)).put("acknowledged",acknowledged))
        return pruneUnion(result,Instant.ofEpochMilli(now))
    }
    fun updateOutgoing(state:JSONObject,request:OutgoingFriendRequest,status:String,name:String,now:Long=System.currentTimeMillis()):JSONObject {
        require(status in statuses);id(request.id)
        val result=JSONObject(state.toString())
        val old=result.getJSONObject("outgoing").optJSONObject(request.id)
        val entry=JSONObject().put("senderGistId",request.senderGistId).put("recipientGistId",request.recipientGistId)
            .put("createdAt",request.createdAt).put("status",status).put("recipientName",name.trim().take(48).ifBlank { "Équipier" })
            .put("updatedAt",maxOf(now,(old?.optLong("updatedAt") ?: 0)+1))
        // A late failure or retry cannot downgrade a received acknowledgement or a known successful send.
        if(old!=null && (old.getString("status")=="accepted" || (old.getString("status")=="sent" && status in setOf("pending","failed")))) return result
        result.getJSONObject("outgoing").put(request.id,entry)
        return pruneUnion(result,Instant.ofEpochMilli(now))
    }
    fun outgoing(state:JSONObject,ownGist:String,now:Instant=Instant.now()):List<OutgoingFriendRequestState> {
        val values=state.getJSONObject("outgoing")
        return values.keys().asSequence().map { key ->
            val entry=values.getJSONObject(key)
            OutgoingFriendRequestState(OutgoingFriendRequest(key,entry.getString("senderGistId"),entry.getString("recipientGistId"),entry.getString("createdAt")),
                entry.getString("status"),entry.getString("recipientName"),entry.getLong("updatedAt"))
        }.filter { it.request.senderGistId==ownGist && recent(it.request.createdAt,now) }
            .sortedByDescending { it.updatedAt }.toList()
    }
    fun handled(state:JSONObject,ownGist:String):Set<String> {
        val records=state.getJSONObject("decisions")
        return records.keys().asSequence().filter { records.getJSONObject(it).getString("ownGistId")==ownGist }
            .map { it.substringAfter(':') }.toSet()
    }
    fun decisionKey(request:FriendRequest)="${request.recipientGistId}:${request.senderGistId}:${request.id}"
    fun recent(value:String,now:Instant=Instant.now()):Boolean = runCatching {
        val created=Instant.parse(value)
        created>=now.minus(Duration.ofDays(30)) && created<=now.plusSeconds(300)
    }.getOrDefault(false)
    fun requestJson(request:FriendRequest):JSONObject = JSONObject().put("id",request.id).put("senderGistId",request.senderGistId)
        .put("recipientGistId",request.recipientGistId).put("createdAt",request.createdAt).put("author",request.author.toJson())
        .put("profile",JSONObject(request.profile.toJson())).put("commentId",request.commentId)
    fun requestFromJson(value:JSONObject):FriendRequest {
        val author=GitHubIdentity.fromJson(value.getJSONObject("author"));require(author.id!=null)
        val comment=value.getLong("commentId");require(comment>0)
        return FriendRequest(id(value.getString("id")),gist(value.getString("senderGistId")),gist(value.getString("recipientGistId")),
            time(value.getString("createdAt")),author,ShareProgress.fromJson(value.getJSONObject("profile").toString()),comment)
    }
    private fun canonical(value:JSONObject)=value.keys().asSequence().sorted().joinToString { "$it:${value.opt(it)}" }
}
