package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.net.Uri
import com.malfreyt.alexandre.hamigo.platform.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Local GitHub-shaped store used by the normal sync/merge/inbox implementation. No HTTP fallback. */
internal class ReviewGitHubGateway(context:ReviewContext):GitHubGateway {
    private val prefs=context.getSharedPreferences("review_cloud",Context.MODE_PRIVATE)
    private val lock=Any()
    override suspend fun api(method:String,path:String,token:String?,body:String?):String=synchronized(lock) {
        require(token==null||token==ReviewAccess.TOKEN){"La démo refuse les identifiants d’un compte réel."}
        val uri=Uri.parse("https://local.invalid$path");val parts=uri.pathSegments
        val db=JSONObject(prefs.getString("database",null) ?: "{}")
        val gists=db.optJSONObject("gists") ?: JSONObject().also {db.put("gists",it)
            peers().forEach {(id,owner,profile)->it.put(id,gist(id,owner,JSONObject().put(GitHubSync.FILE_NAME,JSONObject().put("content",profile.toJson()))))}}
        val comments=db.optJSONObject("comments") ?: JSONObject().also {db.put("comments",it)}
        fun save(){check(prefs.edit().putString("database",db.toString()).commit())}
        fun commentList(id:String)=comments.optJSONArray(id) ?: JSONArray().also {comments.put(id,it)}
        fun append(id:String,owner:GitHubIdentity,text:String):JSONObject {
            val next=db.optLong("commentId",100)+1;db.put("commentId",next)
            return JSONObject().put("id",next).put("user",owner.toJson()).put("body",text)
                .put("created_at",Instant.now().toString()).also {commentList(id).put(it)}
        }
        fun seedIncoming(own:String) {
            if(db.optBoolean("incomingSeeded"))return
            val sender=peers()[1]
            append(own,sender.second,message("request",UUID.randomUUID().toString(),sender.first,own))
            db.put("incomingSeeded",true)
        }
        val result=when {
            method=="GET"&&parts==listOf("user")->OWNER.toJson().toString()
            method=="GET"&&parts==listOf("user","blocks")->"[]"
            method=="GET"&&parts==listOf("gists")-> {
                val page=uri.getQueryParameter("page")?.toIntOrNull() ?: 1
                if(page>1)"[]" else JSONArray(gists.keys().asSequence().map {gists.getJSONObject(it)}
                    .filter {it.getJSONObject("owner").getLong("id")==OWNER.id}.toList()).toString()
            }
            method=="POST"&&parts==listOf("gists")-> {
                val data=JSONObject(body!!);val number=db.optLong("gistId",100)+1;db.put("gistId",number)
                val id="%032x".format(number);val created=gist(id,OWNER,data.getJSONObject("files"));gists.put(id,created)
                if(created.getJSONObject("files").has(GitHubSync.FILE_NAME))seedIncoming(id)
                created.toString()
            }
            parts.size>=2&&parts[0]=="gists"-> {
                val id=parts[1];val existing=gists.optJSONObject(id) ?: throw SocialException("Gist démo introuvable.",404)
                when {
                    parts.size==2&&method=="GET"->existing.toString()
                    parts.size==2&&method=="PATCH"-> {
                        check(existing.getJSONObject("owner").getLong("id")==OWNER.id)
                        val files=existing.getJSONObject("files");val changes=JSONObject(body!!).getJSONObject("files")
                        changes.keys().forEach {name->files.put(name,JSONObject(changes.getJSONObject(name).toString()).put("filename",name).put("truncated",false))}
                        existing.put("updated_at",Instant.now().toString());existing.toString()
                    }
                    parts.size==2&&method=="DELETE"-> {
                        check(existing.getJSONObject("owner").getLong("id")==OWNER.id)
                        gists.remove(id);comments.remove(id);""
                    }
                    parts.size==3&&parts[2]=="comments"&&method=="GET"->if((uri.getQueryParameter("page")?.toIntOrNull() ?: 1)>1)"[]" else commentList(id).toString()
                    parts.size==3&&parts[2]=="comments"&&method=="POST"-> {
                        val text=JSONObject(body!!).getString("body");val posted=append(id,OWNER,text)
                        // A fictional peer accepts the explicit request; the production parser verifies its ACK.
                        val marker="<!-- hamigo-friends:v1\n";val start=text.indexOf(marker)
                        if(start>=0) {
                            val data=JSONObject(text.substring(start+marker.length,text.length-4))
                            if(data.optString("type")=="request") {
                                val peer=GitHubIdentity.fromJson(existing.getJSONObject("owner"))
                                append(id,peer,message("accepted",data.getString("id"),data.getString("senderGist"),id))
                            }
                        }
                        posted.toString()
                    }
                    parts.size==4&&parts[2]=="comments"&&method=="GET"-> {
                        val list=commentList(id);(0 until list.length()).map {list.getJSONObject(it)}
                            .firstOrNull {it.getLong("id").toString()==parts[3]}?.toString() ?: throw SocialException("Commentaire démo introuvable.",404)
                    }
                    else->error("Action non implémentée dans la démo : $method ${uri.path}")
                }
            }
            else->error("Ressource non implémentée dans la démo : $method ${uri.path}")
        }
        save();result
    }
    override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String=error("Aucun téléchargement externe dans la démo GitHub.")
    companion object {
        val OWNER=GitHubIdentity("hamigo-demo-camille",999999901)
        fun peers():List<Triple<String,GitHubIdentity,ShareProgress>> {
            val today=StudyDay.date();val zone=ZoneId.systemDefault().id
            return listOf("Léa" to 960,"Sam" to 1720,"Noa" to 650).mapIndexed {index,(name,xp)->
                val daily=(0L..35L).associate {today.minusDays(it).toString() to (12+index*3)}
                Triple("f".repeat(30)+"%02x".format(index+1),GitHubIdentity("hamigo-demo-${listOf("lea","sam","noa")[index]}",999999902L+index),
                    ShareProgress("$name · Démo",xp,5+index,6+index,(12+index*3)*7,dailyGoal=60,
                        dailyXp=daily.entries.take(7).reversed().map {DailyPoint(it.key,it.value)},
                        activityHistory=PublicActivityHistory.fromDailyXp(JSONObject(daily),today),zoneId=zone,
                        appVersion=BuildConfig.VERSION_NAME,appVersionCode=BuildConfig.VERSION_CODE))
            }
        }
        private fun gist(id:String,owner:GitHubIdentity,source:JSONObject):JSONObject {
            val files=JSONObject();source.keys().forEach {name->files.put(name,JSONObject(source.getJSONObject(name).toString()).put("filename",name).put("truncated",false))}
            return JSONObject().put("id",id).put("public",false).put("owner",owner.toJson()).put("files",files)
                .put("html_url",GitHubSync.gistPageUrl(id)).put("updated_at",Instant.now().toString())
        }
        private fun message(type:String,id:String,sender:String,recipient:String)="<!-- hamigo-friends:v1\n"+
            JSONObject().put("app","hamigo-friends").put("schema",1).put("type",type).put("id",id)
                .put("senderGist",sender).put("recipientGist",recipient).put("createdAt",Instant.now().toString())+"\n-->"
    }
}
