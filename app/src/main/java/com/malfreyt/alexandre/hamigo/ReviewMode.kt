package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Explicit local demo, never an authentication credential for a real GitHub account. */
internal object ReviewAccess {
    const val CODE="691427083519640782135906"
    const val TOKEN="hamigo-local-review-not-a-github-token"
    @Volatile var networkPaused=false;private set
    private fun root(context:Context)=if(context is ReviewContext)context.original else context.applicationContext
    fun active(context:Context)=root(context).getSharedPreferences("hamigo_review_access",Context.MODE_PRIVATE).getBoolean("active",false)
    fun context(context:Context):Context {
        val base=if(context is ReviewContext)context.original.applicationContext else context.applicationContext
        networkPaused=active(base)
        return if(networkPaused)ReviewContext(base) else base
    }
    fun matches(value:String)=value.filterNot(Char::isWhitespace)==CODE
    fun setActive(context:Context,value:Boolean) {
        check(root(context).getSharedPreferences("hamigo_review_access",Context.MODE_PRIVATE).edit().putBoolean("active",value).commit())
        networkPaused=value
    }
    fun restart(activity:ComponentActivity,value:Boolean) {
        setActive(activity,value)
        ProgressSyncScheduler.cancel(activity)
        activity.viewModelStore.clear()
        activity.recreate()
    }
    fun prepare(context:ReviewContext,content:Content) {
        val state=context.getSharedPreferences("review_state",Context.MODE_PRIVATE)
        if(state.getBoolean("prepared",false))return
        val today=StudyDay.date();val daily=JSONObject()
        (0L..20L).filter {it%5!=3L}.forEach {daily.put(today.minusDays(it).toString(),if(it==0L)24 else 48)}
        val completed=JSONArray(content.lessons.take(8).map {it.id})
        val base=JSONObject().put("xp",1240).put("answers",210).put("correct",180).put("completed",completed)
            .put("dailyXp",daily).put("awarded",JSONObject())
        val progress=JSONObject(base.toString()).put("schema",2).put("questionIdsVersion",1)
            .put("syncBase",base).put("syncEvents",JSONObject())
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putString("name","Camille · Démo")
            .putString("progress",progress.toString()).putBoolean("welcomed",true).putInt("hamigoTourStep",-1)
            .putBoolean("autoSync",true).putBoolean("reminderEnabled",false).putBoolean("interactionSound",false)
            .putBoolean("interactionHaptics",false).putInt("dailyGoal",60).commit()
        SocialRules.accept(context)
        SecureTokenStore(context).store(TOKEN)
        context.getSharedPreferences("hamigo_social",Context.MODE_PRIVATE).edit()
            .putString("ownerLogin",ReviewGitHubGateway.OWNER.login).putLong("ownerId",ReviewGitHubGateway.OWNER.id!!).commit()
        val friend=ReviewGitHubGateway.peers().first()
        val list=JSONArray().put(JSONObject().put("gist",GitHubSync.gistPageUrl(friend.first))
            .put("modifiedAt",System.currentTimeMillis()).put("progress",JSONObject(friend.third.toJson()))
            .put("githubIdentity",friend.second.toJson()))
        Progress(context,sideEffects=false).saveFriendRecords(CloudProgress.localFriends(list,JSONObject()))
        state.edit().putBoolean("prepared",true).commit()
    }
}
/** Persistent preference namespace. In particular, no personal token or friend list is copied. */
internal class ReviewContext(val original:Context):ContextWrapper(original) {
    override fun getApplicationContext():Context=this
    override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=original.getSharedPreferences("play_review_$name",mode)
}

internal val LocalOpenReview=staticCompositionLocalOf<((Boolean)->Unit)?> {null}

@Composable internal fun ReviewAccessDialog(approved:Boolean,onDismiss:()->Unit) {
    val activity=LocalActivity.current as? ComponentActivity ?: return
    var code by rememberSaveable {mutableStateOf("")}
    HamigoSheet(title="Accès de démonstration",onDismiss=onDismiss,actions={
        TextButton(onDismiss){Text("Annuler")}
        TextButton({onDismiss();ReviewAccess.restart(activity,true)},enabled=approved||ReviewAccess.matches(code),modifier=Modifier.testTag("review-enter")){Text("Entrer dans la démonstration")}
    }) {
        Text("Un compte fictif permet d’essayer la sauvegarde, l’équipe et la suppression. Les échanges GitHub sont simulés localement. Aucune donnée personnelle n’est copiée ni envoyée à GitHub.")
        Text("Le bandeau Démonstration permet de revenir à tes données. Ce mode reste actif après fermeture de l’app jusqu’à ce que tu le quittes.")
        if(!approved)OutlinedTextField(code,{code=it.filter(Char::isDigit).take(24)},label={Text("Code d’accès")},singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("review-access-code"))
    }
}

@Composable internal fun ReviewBanner() {
    val activity=LocalActivity.current as? ComponentActivity ?: return
    Surface(color=Mist,modifier=Modifier.fillMaxWidth().testTag("review-banner")) {
        Row(Modifier.statusBarsPadding().padding(horizontal=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("DÉMONSTRATION\nDonnées fictives · GitHub simulé",fontSize=11.sp,lineHeight=15.sp,color=Ink,modifier=Modifier.weight(1f))
            TextButton({ReviewAccess.restart(activity,false)},Modifier.testTag("review-exit")){Text("Quitter")}
        }
    }
}
