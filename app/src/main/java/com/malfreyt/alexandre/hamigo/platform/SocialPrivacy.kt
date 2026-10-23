package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import com.malfreyt.alexandre.hamigo.DiagnosticContext

/** Local decisions are never uploaded to a public social summary. */
object SocialRules {
    private fun prefs(context:Context)=context.getSharedPreferences("hamigo_privacy",Context.MODE_PRIVATE)
    fun accepted(context:Context)=context.applicationContext is DiagnosticContext || prefs(context).getBoolean("socialRules-v2",false)
    fun accept(context:Context){check(prefs(context).edit().putBoolean("socialRules-v2",true).commit())}
    fun deletionPending(context:Context)=prefs(context).getBoolean("remoteDeletionPending",false)
    fun setDeletionPending(context:Context,value:Boolean){check(prefs(context).edit().putBoolean("remoteDeletionPending",value).commit())}
}
