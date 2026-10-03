package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.malfreyt.alexandre.hamigo.platform.GitHubSync
import com.malfreyt.alexandre.hamigo.platform.ShareProgress
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Friend(val progress: ShareProgress, val gist: String = "")
class Session(val title: String, val questions: MutableList<Question>, val lessonId: String? = null, val exam: Boolean = false) {
    var index = 0
    var correct = 0
    var firstCorrect = 0
    var gain = 0
    var feedback: Boolean? = null
    var started = System.currentTimeMillis()
    var regulationScore = 0
    var techniqueScore = 0
    var unanswered = 0
    val missed = linkedMapOf<String, Question>()
    val unresolved = linkedSetOf<String>()
    val firstCount = questions.size
    val lessonPassed get() = LearningRules.lessonPassed(firstCorrect,firstCount) && unresolved.isEmpty()
    val done get() = index >= questions.size
    val current get() = questions.getOrNull(index)
    val examTimeRemaining: Long get() = ((if (index < 20) 15 else 30) * 60_000L - (System.currentTimeMillis() - started)).coerceAtLeast(0)
}

class AppModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var context: Context
    lateinit var progress: Progress
    var displayedProgress by mutableStateOf<Progress?>(null)
        private set
    lateinit var sync: GitHubSync
    var content by mutableStateOf<Content?>(null)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var revision by mutableIntStateOf(0)
    var route by mutableStateOf("path")
    var lesson by mutableStateOf<Lesson?>(null)
    var session by mutableStateOf<Session?>(null)
    var resource by mutableStateOf<RefCategory?>(null)
    var friends by mutableStateOf<List<Friend>>(emptyList())
    var busy by mutableStateOf(false)
    private var taskJob: Job? = null
    private var socialJob: Job? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("progress", "friends", "name", "dailyGoal", "reminderEnabled", "reminderHour", "reminderMinute",
                "lastSyncedAt", "lastSyncError", "ownerLogin", "ownGistUrl")) {
            // Workers can finish while a screen remains open. Reload data without starting another sync.
            scope.launch { refresh() }
        }
    }
    var incoming by mutableStateOf<String?>(null)
    var showWelcome by mutableStateOf(false)
    var oauthSession by mutableStateOf<DeviceOAuth.Session?>(null)
    var pendingInvite by mutableStateOf<String?>(null)
    fun initialize(ctx: Context) {
        if (::progress.isInitialized) return
        context = ctx.applicationContext
        progress = Progress(context); sync = GitHubSync(context)
        displayedProgress=progress
        progress.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        context.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceListener)
        DailyReminder.schedule(context)
        showWelcome = !progress.prefs.getBoolean("welcomed", false)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Content(context) } }
                .onSuccess { content = it; loadFriends(); ProgressSyncScheduler.schedule(context); refreshSocial() }
                .onFailure { error = "Chargement impossible : ${it.message}" }
        }
    }
    fun welcome(name: String) { if(name.isNotBlank()) progress.name = name; progress.prefs.edit().putBoolean("welcomed", true).apply(); showWelcome=false; revision++ }
    fun startLesson(l: Lesson) { lesson=l; session=null }
    fun startQuestions(title: String, questions: List<Question>, lessonId: String? = null, exam: Boolean = false) {
        require(questions.isNotEmpty())
        lesson=null; resource=null; session=Session(title, questions.toMutableList(), lessonId, exam)
    }
    fun answer(correct: Boolean, quality: Int = if(correct) 4 else 1, omitted: Boolean = false) {
        val s=session ?: return; val q=s.current ?: return
        if (s.feedback != null) return
        if (!omitted) s.gain += progress.answer(q.id, correct, quality)
        if (correct) {
            if(s.index<s.firstCount) s.firstCorrect++
            s.correct++; s.unresolved.remove(q.id)
            if(q.section == "regulation") s.regulationScore++ else s.techniqueScore++
        } else { s.missed[q.id]=q; s.unresolved.add(q.id); if(omitted) s.unanswered++ }
        s.feedback=correct; revision++
        ProgressSyncScheduler.enqueue(context)
        if(s.exam) next()
    }
    fun next() {
        val s=session ?: return
        val q=s.current
        if (!s.exam && s.feedback == false && q != null && s.questions.count { it.id == q.id } < 3) s.questions.add(q)
        s.index++; s.feedback=null
        if(s.exam && s.index==20) s.started=System.currentTimeMillis()
        if(s.done) {
            if(s.lessonId != null && s.lessonPassed) { s.gain += progress.complete(s.lessonId) }
            refreshSocial()
        }
        revision++
    }
    fun timeoutExamPart() {
        val s=session ?: return
        if(!s.exam) return
        val end=if(s.index<20) 20 else 40
        while(s.index < end && !s.done) answer(false, omitted=true)
    }
    fun refresh() {
        if(::progress.isInitialized) {
            progress.reload(); loadFriends()
            // Compose also needs a new display value, rather than a mutated cached Progress instance.
            displayedProgress=Progress(context)
        }
        revision++
    }
    fun leaveSession() { session=null; lesson=null; refreshSocial() }
    private fun loadFriends() {
        friends = runCatching { JSONArray(progress.prefs.getString("friends","[]")).objects().map { f ->
            Friend(ShareProgress.fromJson(f.getJSONObject("progress").toString()), f.optString("gist")) } }.getOrDefault(emptyList())
    }
    private fun saveFriends() {
        progress.prefs.edit().putString("friends", JSONArray(friends.map { JSONObject().put("progress", JSONObject(it.progress.toJson())).put("gist",it.gist) }).toString()).apply()
    }
    fun addFriend(friend: Friend) {
        synchronized(Progress.CLOUD_LOCK) {
        loadFriends()
        val identity = runCatching { GitHubSync.gistId(friend.gist) }.getOrNull()
        friends=(friends.filterNot { identity != null && runCatching { GitHubSync.gistId(it.gist) }.getOrNull() == identity } + friend).takeLast(30)
        saveFriends()
        }
    }
    fun removeFriend(friend: Friend) { synchronized(Progress.CLOUD_LOCK) { loadFriends();friends=friends.filterNot {it.gist==friend.gist && it.progress.name==friend.progress.name}; saveFriends() } }
    fun startGitHubConnection() = task {
        try {
            val device=DeviceOAuth.start(GitHubApp.CLIENT_ID); oauthSession=device
            val token=DeviceOAuth.awaitToken(GitHubApp.CLIENT_ID,device)
            val user=sync.connect(token); sync.synchronize(progress)
            ProgressSyncScheduler.schedule(context); message="Bienvenue ${user.login} ! Ton voyage est synchronisé."
        } finally { oauthSession=null }
    }
    fun disconnectGitHub() { sync.disconnect(); ProgressSyncScheduler.cancel(context); refresh() }
    fun setAutoSync(enabled:Boolean) { progress.prefs.edit().putBoolean("autoSync",enabled).apply(); ProgressSyncScheduler.schedule(context); refresh() }
    fun acceptInvite() {
        val invite=pendingInvite ?: return
        pendingInvite=null
        task {
            if(sync.savedGistUrl?.let { GitHubSync.gistId(it) } == invite) {message="C'est ton propre lien d'invitation.";return@task}
            val friend=sync.read(invite)
            addFriend(Friend(friend,"https://gist.github.com/$invite"));route="friends";message="${friend.name} rejoint ton équipe !"
        }
    }
    fun refreshSocial(manual: Boolean = false) {
        if(busy || socialJob?.isActive==true) return
        // Foreground and finished sessions: network remains optional and never blocks learning.
        socialJob=scope.launch {
            if(manual) busy=true
            var failures=0
            if((manual || progress.prefs.getBoolean("autoSync",true)) && runCatching { sync.tokens.get() }.getOrNull()!=null) {
                try { sync.synchronize(progress) } catch(e:CancellationException){throw e} catch(e:Exception){failures++}
            }
            val updates = friends.map { friend ->
                if(friend.gist.isBlank()) friend else try { Friend(sync.read(friend.gist),friend.gist) } catch(e:CancellationException){throw e} catch(e:Exception){failures++;friend}
            }
            val byGist=updates.filter{it.gist.isNotBlank()}.associateBy{it.gist}
            synchronized(Progress.CLOUD_LOCK) {
                loadFriends()
                friends=friends.map { previous ->
                    val updated=byGist[previous.gist]
                    val previousTime=runCatching { Instant.parse(previous.progress.updatedAt) }.getOrNull()
                    val updateTime=updated?.let { runCatching { Instant.parse(it.progress.updatedAt) }.getOrNull() }
                    if(updated==null || (previousTime!=null && updateTime!=null && previousTime.isAfter(updateTime))) previous else updated
                }
                saveFriends()
            }; revision++
            if(manual) { busy=false; message=if(failures==0) "Progressions actualisées." else "Connexion indisponible. Les dernières progressions restent consultables." }
        }
    }
    fun task(action: suspend () -> Unit) {
        if(busy) return
        busy=true
        taskJob=scope.launch { try { action() } catch(e: CancellationException) {throw e} catch(e:Exception){message=e.message ?: "Opération impossible."} finally {busy=false;revision++} }
    }
    fun cancelTask() { taskJob?.cancel() }
    fun restore(json: String) { runCatching { progress.import(json);revision++;message="Sauvegarde restaurée." }.onFailure {message=it.message} }
    override fun onCleared() {
        if(::progress.isInitialized) {
            progress.prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            context.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        scope.cancel()
    }
}
