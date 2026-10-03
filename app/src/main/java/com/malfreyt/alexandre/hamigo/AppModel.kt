package com.malfreyt.alexandre.hamigo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.malfreyt.alexandre.hamigo.platform.GitHubSync
import com.malfreyt.alexandre.hamigo.platform.ShareProgress
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Friend(val progress: ShareProgress, val gist: String = "")
class Session(val title: String, val questions: MutableList<Question>, val lessonId: String? = null, val exam: Boolean = false) {
    var index = 0
    var correct = 0
    var gain = 0
    var feedback: Boolean? = null
    var started = System.currentTimeMillis()
    var regulationScore = 0
    var techniqueScore = 0
    var unanswered = 0
    val missed = linkedMapOf<String, Question>()
    val unresolved = linkedSetOf<String>()
    val firstCount = questions.size
    val done get() = index >= questions.size
    val current get() = questions.getOrNull(index)
    val examTimeRemaining: Long get() = ((if (index < 20) 15 else 30) * 60_000L - (System.currentTimeMillis() - started)).coerceAtLeast(0)
}

class AppModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var context: Context
    lateinit var progress: Progress
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
    var incoming by mutableStateOf<String?>(null)
    var showWelcome by mutableStateOf(false)
    fun initialize(ctx: Context) {
        if (::progress.isInitialized) return
        context = ctx.applicationContext
        progress = Progress(context); sync = GitHubSync(context)
        showWelcome = !progress.prefs.getBoolean("welcomed", false)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Content(context) } }
                .onSuccess { content = it; loadFriends(); refreshSocial() }
                .onFailure { error = "Chargement impossible : ${it.message}" }
        }
    }
    fun welcome(name: String) { progress.name = name; progress.prefs.edit().putBoolean("welcomed", true).apply(); showWelcome=false; revision++ }
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
            s.correct++; s.unresolved.remove(q.id)
            if(q.section == "regulation") s.regulationScore++ else s.techniqueScore++
        } else { s.missed[q.id]=q; s.unresolved.add(q.id); if(omitted) s.unanswered++ }
        s.feedback=correct; revision++
        if(s.exam) next()
    }
    fun next() {
        val s=session ?: return
        val q=s.current
        if (!s.exam && s.feedback == false && q != null && s.questions.count { it.id == q.id } < 3) s.questions.add(q)
        s.index++; s.feedback=null
        if(s.exam && s.index==20) s.started=System.currentTimeMillis()
        if(s.done) {
            if(s.lessonId != null && s.unresolved.isEmpty()) { s.gain += progress.complete(s.lessonId) }
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
    fun refresh() { revision++ }
    fun leaveSession() { session=null; lesson=null; refreshSocial() }
    private fun loadFriends() {
        friends = runCatching { JSONArray(progress.prefs.getString("friends","[]")).objects().map { f ->
            Friend(ShareProgress.fromJson(f.getJSONObject("progress").toString()), f.optString("gist")) } }.getOrDefault(emptyList())
    }
    private fun saveFriends() {
        progress.prefs.edit().putString("friends", JSONArray(friends.map { JSONObject().put("progress", JSONObject(it.progress.toJson())).put("gist",it.gist) }).toString()).apply()
    }
    fun addFriend(friend: Friend) {
        friends=(friends.filterNot { (friend.gist.isNotBlank() && it.gist == friend.gist) || it.progress.name == friend.progress.name } + friend).takeLast(30)
        saveFriends()
    }
    fun removeFriend(friend: Friend) { friends=friends-friend; saveFriends() }
    fun importFriend(json: String) { runCatching { addFriend(Friend(ShareProgress.fromJson(json))) }.onFailure { message=it.message } }
    fun readFriend(gist: String) = task {
        val friend=sync.read(gist.trim()); addFriend(Friend(friend,gist.trim())); message="${friend.name} rejoint ton équipe !"
    }
    fun connect(token: String) = task {
        val user=sync.connect(token); sync.push(progress.snapshot()); revision++; message="Compte ${user.login} connecté. Ta progression est synchronisée."
    }
    fun refreshSocial(manual: Boolean = false) {
        if(busy || socialJob?.isActive==true) return
        // Foreground and finished sessions: network remains optional and never blocks learning.
        socialJob=scope.launch {
            if(manual) busy=true
            var failures=0
            if(progress.prefs.getBoolean("autoSync",true) && runCatching { sync.tokens.get() }.getOrNull()!=null) {
                try { sync.push(progress.snapshot()) } catch(e:CancellationException){throw e} catch(e:Exception){failures++}
            }
            val updates = friends.map { friend ->
                if(friend.gist.isBlank()) friend else try { Friend(sync.read(friend.gist),friend.gist) } catch(e:CancellationException){throw e} catch(e:Exception){failures++;friend}
            }
            val byGist=updates.filter{it.gist.isNotBlank()}.associateBy{it.gist}
            friends=friends.map{byGist[it.gist] ?: it}; saveFriends(); revision++
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
    override fun onCleared() { scope.cancel() }
}
