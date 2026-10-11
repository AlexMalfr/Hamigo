package com.malfreyt.alexandre.hamigo.platform

import android.appwidget.AppWidgetManager
import android.content.Context

/** Launcher instance settings are local, separate from progression and GitHub backups. */
internal object WidgetSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("hamigo_widgets", Context.MODE_PRIVATE)
    private fun key(id: Int) = "transparent_$id"
    fun lightText(context: Context, id: Int) = prefs(context).getBoolean("lightText_$id",true)
    fun socialToday(context:Context,id:Int)=prefs(context).getBoolean("socialToday_$id",false)
    fun transparent(context: Context, id: Int) = id != AppWidgetManager.INVALID_APPWIDGET_ID &&
        prefs(context).getBoolean(key(id), false)
    fun setTransparent(context: Context, id: Int, value: Boolean): Boolean {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        return prefs(context).edit().putBoolean(key(id), value).commit()
    }
    fun save(context: Context, id: Int, transparent: Boolean, lightText: Boolean,socialToday:Boolean=false): Boolean {
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID)return false
        return prefs(context).edit().putBoolean(key(id),transparent).putBoolean("lightText_$id",lightText).putBoolean("socialToday_$id",socialToday).commit()
    }
    fun delete(context: Context, ids: IntArray) {
        prefs(context).edit().apply { ids.forEach { remove(key(it));remove("lightText_$it");remove("socialToday_$it") } }.apply()
    }
}
