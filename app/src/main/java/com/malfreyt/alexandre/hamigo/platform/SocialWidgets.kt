package com.malfreyt.alexandre.hamigo.platform

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.malfreyt.alexandre.hamigo.Progress
import org.json.JSONObject
import java.util.concurrent.Executors

internal data class SocialWidgetMember(val profile:ShareProgress,val identity:GitHubIdentity?,val own:Boolean)
internal data class SocialWidgetSnapshot(val members:List<SocialWidgetMember>,val today:Boolean=false) {
    companion object {
        fun read(context:Context,today:Boolean=false):SocialWidgetSnapshot=synchronized(Progress.CLOUD_LOCK) {
            val progress=Progress(context.applicationContext)
            val social=context.getSharedPreferences("hamigo_social",Context.MODE_PRIVATE)
            val ownIdentity=social.getString("ownerLogin",null)?.let {login->runCatching {GitHubIdentity.fromJson(JSONObject().put("login",login).also {if(social.contains("ownerId"))it.put("id",social.getLong("ownerId",0))})}.getOrNull()}
            val records=progress.friendRecords()
            val friends=records.keys().asSequence().mapNotNull {id->runCatching {
                val record=records.getJSONObject(id);if(record.optBoolean("deleted"))null else SocialWidgetMember(ShareProgress.fromJson(record.getJSONObject("progress").toString()),record.optJSONObject("githubIdentity")?.let {GitHubIdentity.fromJson(it)},false)
            }.getOrNull()}.toList()
            SocialWidgetSnapshot(listOf(SocialWidgetMember(progress.snapshot(),ownIdentity,true))+friends,today)
        }
    }
    fun xp(member:SocialWidgetMember):Int?=if(today)SocialActivity.todayXp(member.profile) else SocialActivity.weeklyXp(member.profile)
    val ranked get()=members.sortedWith(compareByDescending<SocialWidgetMember> {xp(it) ?: -1}.thenByDescending {it.own}.thenBy {it.profile.name})
}

/** Cached public data and disk avatars only. No token, bank loading, network or friend mutation. */
internal object SocialWidgets {
    val executor=Executors.newSingleThreadExecutor()
    fun installed(context:Context)=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,SocialWidgetProvider::class.java)).isNotEmpty()
    fun refresh(context:Context) {
        val manager=AppWidgetManager.getInstance(context);val ids=manager.getAppWidgetIds(ComponentName(context,SocialWidgetProvider::class.java))
        if(ids.isNotEmpty())update(context,manager,ids)
    }
    fun update(context:Context,manager:AppWidgetManager,ids:IntArray) {
        val snapshot=SocialWidgetSnapshot.read(context)
        ids.forEach {id->manager.updateAppWidget(id,responsiveViews(context,snapshot.copy(today=WidgetSettings.socialToday(context,id)),manager.getAppWidgetOptions(id),id))}
    }
    fun responsiveViews(context:Context,snapshot:SocialWidgetSnapshot,options:Bundle,id:Int):RemoteViews {
        fun option(key:String,default:Int)=options.getInt(key,default).toFloat().coerceAtLeast(40f)
        val default=WidgetDimensions.defaultSize(context,null)
        val portrait=SizeF(option(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,default.width.toInt()),option(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,default.height.toInt()))
        val landscape=SizeF(option(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,default.width.toInt()),option(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,default.height.toInt()))
        val transparent=WidgetSettings.transparent(context,id);val light=WidgetSettings.lightText(context,id)
        if(Build.VERSION.SDK_INT>=31) {
            @Suppress("DEPRECATION") val sizes=options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty().filter {it.width.isFinite()&&it.height.isFinite()&&it.width>=40&&it.height>=40}.ifEmpty {listOf(portrait,landscape)}.distinct().take(16)
            val budget=minOf(520_000f,1_000_000f/sizes.size)
            return RemoteViews(sizes.associateWith {SocialWidgetPresentation.views(context,snapshot,it,budget,transparent,light)})
        }
        return if(portrait==landscape)SocialWidgetPresentation.views(context,snapshot,portrait,520_000f,transparent,light)
        else RemoteViews(SocialWidgetPresentation.views(context,snapshot,landscape,500_000f,transparent,light),SocialWidgetPresentation.views(context,snapshot,portrait,500_000f,transparent,light))
    }
}

class SocialWidgetProvider:AppWidgetProvider() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        SocialWidgets.executor.execute {try {receiveNow(context,intent)} finally {pending.finish()}}
    }
    private fun receiveNow(context:Context,intent:Intent)=super.onReceive(context,intent)
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray)=HomeWidgets.refresh(context)
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:Bundle)=SocialWidgets.update(context,manager,intArrayOf(id))
    override fun onDeleted(context:Context,ids:IntArray)=WidgetSettings.delete(context,ids)
    override fun onDisabled(context:Context)=HomeWidgets.refresh(context)
}
