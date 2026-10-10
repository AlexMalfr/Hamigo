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
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Friend(val progress: ShareProgress, val gist: String = "", val modifiedAt: Long = 1L,
    val githubIdentity: GitHubIdentity? = null, val githubIdentityCheckedAt: Long = 0L)
class Session(val title: String, val questions: MutableList<Question>, val lessonId: String? = null, val exam: Boolean = false, val returnRoute: String = "path", val testing:Boolean=false) {
    private var scratchpadKey: String? = null
    private var scratchpad = ScratchpadState()
    internal fun scratchpadFor(key: String): ScratchpadState {
        if (scratchpadKey != key) {
            scratchpadKey = key
            scratchpad = ScratchpadState().also { it.typing=scratchpad.typing; it.stylusOnly=scratchpad.stylusOnly }
        }
        return scratchpad
    }
    var index = 0
    var correct = 0
    var firstCorrect = 0
    var gain = 0
    var feedback: Boolean? = null
    var started = System.currentTimeMillis()
    internal var resultPresented = false
    var regulationScore = 0
    var techniqueScore = 0
    var unanswered = 0
    val responses = linkedMapOf<Int, SessionResponse>()
    var examPart = 0
    var examIntroPending = exam
    var examReviewing = false
    val finalizedExamParts = linkedSetOf<Int>()
    var elapsedMillis = 0L
    val examPartStart get() = if(examPart == 0) 0 else 20
    val examPartEnd get() = minOf(if(examPart == 0) 20 else 40, questions.size)
    val examPartLabel get() = if(examPart == 0) "Réglementation" else "Technique"
    val examMinutes get() = if(examPart == 0) 15 else 30
    val missed = linkedMapOf<String, Question>()
    val unresolved = linkedSetOf<String>()
    val firstCount = questions.size
    val lessonPassed get() = LearningRules.lessonPassed(firstCorrect,firstCount) && unresolved.isEmpty()
    val done get() = index >= questions.size && !examReviewing
    val current get() = questions.getOrNull(index)
    val examTimeRemaining: Long get() = if(examIntroPending) examMinutes * 60_000L else
        (examMinutes * 60_000L - (System.currentTimeMillis() - started)).coerceAtLeast(0)
}

data class SessionResponse(val correct: Boolean, val omitted: Boolean = false,
    val display: String = "", val choiceIndex: Int = -1, val quality: Int = if(correct) 4 else 1)

class AppModel internal constructor(
    private val authorizationClient: GitHubAuthorizationClient = DefaultGitHubAuthorizationClient,
    private val friendInboxGateway: FriendInboxGateway = FriendInbox()
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var context: Context
    lateinit var progress: Progress
    internal lateinit var interactionFeedback: AppFeedback
    internal lateinit var diagnostics:DiagnosticSettings
    internal var diagnosticModel=false;private set
    internal var diagnosticAwards=false
    internal var debugToolsOpen by mutableStateOf(false)
    private val recordsLearning get()=!diagnostics.frozen&&(!diagnosticModel||diagnosticAwards)&&session?.testing!=true
    var displayedProgress by mutableStateOf<Progress?>(null)
        private set
    lateinit var sync: GitHubSync
    private lateinit var friendInbox: FriendInboxCoordinator
    var content by mutableStateOf<Content?>(null)
    internal var examBank by mutableStateOf(ExamBankStatus())
    private var examRepository:ExamBankRepository?=null
    internal fun checkExamBank() {
        if(diagnosticModel){message="La banque ne se télécharge pas dans un aperçu.";return}
        scope.launch {
            val repository=examRepository ?: return@launch
            val previous=repository.status.value.snapshot
            if(repository.refresh(force=true))message=when {
                previous==null->"La banque Exam’1 est prête."
                previous.generation!=repository.status.value.snapshot?.generation->"La banque Exam’1 a été mise à jour."
                else->"La banque Exam’1 est à jour."
            }
        }
    }
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var revision by mutableIntStateOf(0)
    var route by mutableStateOf("path")
    var lesson by mutableStateOf<Lesson?>(null)
    var lessonPreviewOnly by mutableStateOf(false)
        private set
    var lessonOpening by mutableIntStateOf(0)
        private set
    var session by mutableStateOf<Session?>(null)
    var resource by mutableStateOf<RefCategory?>(null)
    var friends by mutableStateOf<List<Friend>>(emptyList())
    var friendRequests by mutableStateOf<List<FriendRequest>>(emptyList())
    var outgoingRequests by mutableStateOf<List<OutgoingFriendRequestState>>(emptyList())
    var busy by mutableStateOf(false)
    var socialRefreshing by mutableStateOf(false)
        private set
    private var taskJob: Job? = null
    private var socialJob: Job? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("progress", "friends", "socialInbox", "friendIncomingCache", "name", "dailyGoal", "reminderEnabled", "reminderHour", "reminderMinute",
                "lastSyncedAt", "lastSyncError", "ownerLogin", "ownerId", "ownGistUrl")) {
            // Workers can finish while a screen remains open. Reload data without starting another sync.
            scope.launch { refresh() }
        }
    }
    var incoming by mutableStateOf<String?>(null)
    var showWelcome by mutableStateOf(false)
    var oauthSession by mutableStateOf<DeviceOAuth.Session?>(null)
    internal var authSession by mutableStateOf<GitHubPkce.Session?>(null)
        private set
    private lateinit var pendingAuthorization: PendingGitHubAuthorization
    private var authorizationCode: CompletableDeferred<String>? = null
    var oauthStatus by mutableStateOf<String?>(null)
    internal val githubBrowserCommands = MutableStateFlow<GitHubBrowserCommand?>(null)
    internal val githubBrowserState = GitHubBrowserState()
    internal val githubTabOpen get() = githubBrowserState.open
    internal fun takeGitHubBrowserCommand(command: GitHubBrowserCommand) =
        githubBrowserCommands.compareAndSet(command, null)
    fun openGitHubBrowser() {
        authSession?.let {
            githubBrowserCommands.value = GitHubBrowserCommand.OpenAuthorization(GitHubPkce.authorizationUrl(GitHubApp.CLIENT_ID, it))
            return
        }
        oauthSession?.let { githubBrowserCommands.value = GitHubBrowserCommand.Open(it) }
    }
    internal fun receiveGitHubAuthorization(url: String) {
        val active = authSession ?: return
        // An unsolicited or old callback must never complete or cancel the current connection.
        if (!GitHubPkce.matchesState(url, active)) {
            message = "Ce retour GitHub ne correspond pas à la connexion en cours."
            return
        }
        val result = runCatching { GitHubPkce.codeFromCallback(url, active) }
        result.onSuccess { authorizationCode?.complete(it) }
            .onFailure { authorizationCode?.completeExceptionally(it) }
    }
    internal fun githubBrowserFailed(notice: String) {
        if (authSession != null) authorizationCode?.completeExceptionally(SocialException(notice))
        else message = notice
    }
    var pendingInvite by mutableStateOf<String?>(null)
    fun initialize(ctx: Context, startExamUpdates:Boolean=true) {
        if (::progress.isInitialized) return
        context = ctx.applicationContext
        progress = Progress(context); sync = GitHubSync(context)
        interactionFeedback = AppFeedback(context)
        diagnostics = DiagnosticSettings(context)
        friendInbox = FriendInboxCoordinator(context,progress,sync,friendInboxGateway)
        pendingAuthorization = PendingGitHubAuthorization(context)
        val savedAuthorization = pendingAuthorization.restore()
        if (savedAuthorization != null && authorizationClient.enabled) {
            githubBrowserState.open = true
            connectGitHub(savedAuthorization)
        } else pendingAuthorization.clear()
        displayedProgress=progress
        progress.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        context.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferenceListener)
        DailyReminder.schedule(context)
        HomeWidgets.progressChanged(context)
        showWelcome = !progress.prefs.getBoolean("welcomed", false)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val repository=ExamBankRepository.forContext(context);repository.initialize();examRepository=repository
                Content(context,repository.status.value.snapshot)
            } }
                .onSuccess {
                    content = it;examBank=examRepository!!.status.value
                    loadFriends(); loadFriendRequests(); ProgressSyncScheduler.schedule(context); refreshSocial()
                    scope.launch {examRepository!!.status.collect {state->
                        examBank=state
                        val previous=content
                        if(previous!=null&&previous.examSnapshot?.generation!=state.snapshot?.generation)
                            content=withContext(Dispatchers.Default){previous.withExam(state.snapshot)}
                    }}
                }
                .onFailure { error = "Chargement impossible : ${it.message}" }
        }
        // Scheduled immediately, including during onboarding; it survives closing the app.
        if(startExamUpdates)ExamBankScheduler.schedule(context)
    }
    fun welcome(name: String) { if(name.isNotBlank()) progress.name = name; progress.prefs.edit().putBoolean("welcomed", true).remove("onboardingStep").remove("onboardingName").remove("onboardingReminderHour").remove("onboardingReminderMinute").apply(); showWelcome=false; revision++ }
    internal fun initializeDiagnostics(base:Context,parent:AppModel,loaded:Content) {
        diagnosticModel=true;context=DiagnosticContext(base.applicationContext)
        progress=Progress(context,sideEffects=false);displayedProgress=progress
        FeedbackPreferences.save(progress.prefs,FeedbackPreferences.read(parent.progress.prefs))
        GameplayPreferences.save(progress.prefs,GameplayPreferences.read(parent.progress.prefs))
        interactionFeedback=parent.interactionFeedback;diagnostics=parent.diagnostics;content=loaded;examBank=parent.examBank
        sync=GitHubSync(context,object:GitHubGateway {
            override suspend fun api(method:String,path:String,token:String?,body:String?):String=error("Diagnostic hors réseau")
            override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String=error("Diagnostic hors réseau")
        })
        DiagnosticData.seed(this)
    }
    internal val diagnosticContext get()=context
    internal fun disposeDiagnostics(){check(diagnosticModel);onCleared()}
    internal fun startClientSandbox() {
        val loaded=content ?: return
        diagnostics.pauseSync()
        socialJob?.cancel();taskJob?.cancel();ProgressSyncScheduler.cancel(context)
        val base=context
        val demo=AppModel().apply {initializeDiagnostics(base,this@AppModel,loaded);diagnosticAwards=true}
        diagnostics.useSandbox(demo)
    }
    internal fun stopClientSandbox() {
        diagnostics.sandbox?.disposeDiagnostics();diagnostics.useSandbox(null)
        ProgressSyncScheduler.schedule(context);ProgressSyncScheduler.enqueue(context);refresh()
    }
    fun startLesson(l: Lesson) { lesson=l; resource=null; session=null; lessonPreviewOnly=false; lessonOpening++ }
    fun previewLesson(l: Lesson) { startLesson(l); lessonPreviewOnly=true }
    fun startQuestions(title: String, questions: List<Question>, lessonId: String? = null, exam: Boolean = false) {
        require(questions.isNotEmpty())
        lesson=null; resource=null; lessonPreviewOnly=false; session=Session(title, questions.toMutableList(), lessonId, exam,
            returnRoute=route.takeIf { it in setOf("path", "practice", "resources", "friends", "profile") } ?: "path",
            testing=diagnostics.frozen||(diagnosticModel&&!diagnosticAwards))
    }
    fun answer(correct: Boolean, quality: Int = if(correct) 4 else 1, omitted: Boolean = false,
               display: String = "", choiceIndex: Int = -1) {
        val s=session ?: return; val q=s.current ?: return
        if (s.feedback != null) return
        if(s.exam && (s.examIntroPending || s.examReviewing || s.examPart in s.finalizedExamParts)) return
        s.responses[s.index]=SessionResponse(correct,omitted,display,choiceIndex,quality)
        if(s.exam) {
            // A draft can be changed freely; XP and spaced repetition are recorded once on finalisation.
            if(s.index+1 < s.examPartEnd) s.index++ else s.examReviewing=true
            revision++; return
        }
        if (!omitted&&recordsLearning) s.gain += progress.answer(q.id, correct, quality)
        if (correct) {
            if(s.index<s.firstCount) s.firstCorrect++
            s.correct++; s.unresolved.remove(q.id)
            if(q.section == "regulation") s.regulationScore++ else s.techniqueScore++
        } else { s.missed[q.id]=q; s.unresolved.add(q.id); if(omitted) s.unanswered++ }
        s.feedback=correct; revision++
        stopMorse()
        interactionFeedback.silence()
        // Exam answers above are drafts: never reveal their correctness through a sound.
        // Let the prompt stop its narration before playing a verdict.
        if(q.kind=="flash")interactionFeedback.event(FeedbackCue.SNAP) else scope.launch {
            delay(60)
            if(session===s&&s.current===q&&s.feedback!=null)interactionFeedback.event(if(correct)FeedbackCue.SUCCESS else FeedbackCue.ERROR)
        }
        if(!diagnosticModel&&recordsLearning)ProgressSyncScheduler.enqueue(context)
    }
    fun next() {
        val s=session ?: return
        val q=s.current
        if (!s.exam && s.feedback == false && q != null && s.questions.count { it.id == q.id } < 3) s.questions.add(q)
        s.index++; s.feedback=null
        if(s.done) {
            interactionFeedback.silence()
            s.elapsedMillis=System.currentTimeMillis()-s.started
            if(s.lessonId != null && s.lessonPassed&&recordsLearning) { s.gain += progress.complete(s.lessonId) }
            refreshSocial()
        }
        revision++
    }
    fun timeoutExamPart() {
        val s=session ?: return
        if(!s.exam || s.examIntroPending || s.done) return
        finishExamPart()
    }
    fun beginExamPart() {
        val s=session ?: return
        if(!s.exam || !s.examIntroPending) return
        s.examIntroPending=false; s.examReviewing=false; s.index=s.examPartStart
        s.started=System.currentTimeMillis(); revision++
    }
    fun revisitExamQuestion(index:Int) {
        val s=session ?: return
        if(!s.exam || s.examIntroPending || s.examPart in s.finalizedExamParts || index !in s.examPartStart until s.examPartEnd) return
        s.index=index; s.examReviewing=false; s.feedback=null; revision++
    }
    fun reviewExamPart() {
        val s=session ?: return
        if(!s.exam || s.examIntroPending || s.done) return
        s.examReviewing=true; revision++
    }
    fun finishExamPart() {
        val s=session ?: return
        if(!s.exam || s.examIntroPending || !s.finalizedExamParts.add(s.examPart)) return
        s.elapsedMillis+=(System.currentTimeMillis()-s.started).coerceIn(0L,s.examMinutes*60_000L)
        for(index in s.examPartStart until s.examPartEnd) {
            val q=s.questions[index]
            val response=s.responses[index] ?: SessionResponse(false,omitted=true)
            s.responses[index]=response
            if(!response.omitted&&recordsLearning) s.gain+=progress.answer(q.id,response.correct,response.quality)
            if(response.correct) {
                s.correct++; s.firstCorrect++
                if(s.examPart==0) s.regulationScore++ else s.techniqueScore++
            } else {
                s.missed[q.id]=q; s.unresolved.add(q.id)
                if(response.omitted) s.unanswered++
            }
        }
        s.feedback=null; s.examReviewing=false
        if(s.examPart==0 && s.questions.size>20) {
            s.examPart=1; s.index=20; s.examIntroPending=true
        } else {
            interactionFeedback.silence();s.index=s.questions.size; s.examIntroPending=false; refreshSocial()
        }
        if(!diagnosticModel&&recordsLearning)ProgressSyncScheduler.enqueue(context); revision++
    }
    fun refresh() {
        if(diagnosticModel){progress.reload();displayedProgress=Progress(context,sideEffects=false);revision++;return}
        if(::progress.isInitialized) {
            progress.reload(); loadFriends(); loadFriendRequests()
            // Compose also needs a new display value, rather than a mutated cached Progress instance.
            displayedProgress=Progress(context)
        }
        revision++
    }
    fun leaveSession() { session?.let { route=it.returnRoute }; session=null; lesson=null; refreshSocial() }
    private fun loadFriends() {
        friends = CloudProgress.activeFriends(progress.friendRecords()).objects().map { f ->
            Friend(ShareProgress.fromJson(f.getJSONObject("progress").toString()), f.getString("gist"), f.getLong("modifiedAt"),
                GitHubIdentity.fromApi(f.optJSONObject("githubIdentity")),f.optLong("githubIdentityCheckedAt")) }
    }
    private fun loadFriendRequests() {
        if(::friendInbox.isInitialized) { friendRequests=friendInbox.pending();outgoingRequests=friendInbox.outgoing() }
    }
    private fun saveFriends() {
        val deleted = CloudProgress.friendTombstones(progress.friendRecords())
        val active = JSONArray(friends.map { JSONObject().put("progress", JSONObject(it.progress.toJson()))
            .put("gist",it.gist).put("modifiedAt",it.modifiedAt).also { entry ->
                it.githubIdentity?.let { identity -> entry.put("githubIdentity", identity.toJson())
                    .put("githubIdentityCheckedAt",it.githubIdentityCheckedAt) }
            } })
        progress.saveFriendRecords(CloudProgress.localFriends(active, deleted))
    }
    fun addFriend(friend: Friend) {
        synchronized(Progress.CLOUD_LOCK) {
            loadFriends()
            val identity = GitHubSync.gistId(friend.gist)
            val previous = progress.friendRecords().optJSONObject(identity)
            require(previous?.optBoolean("deleted") == false || friends.size < CloudProgress.MAX_FRIENDS) { "Ton équipe peut compter jusqu'à trente équipiers." }
            val modifiedAt = maxOf(System.currentTimeMillis(), (previous?.optLong("modifiedAt") ?: 0L) + 1L)
            friends = friends.filterNot { GitHubSync.gistId(it.gist) == identity } +
                friend.copy(gist="https://gist.github.com/$identity", modifiedAt=modifiedAt)
            saveFriends()
        }
        ProgressSyncScheduler.enqueue(context)
    }
    fun removeFriend(friend: Friend) {
        synchronized(Progress.CLOUD_LOCK) {
            val id = GitHubSync.gistId(friend.gist)
            val records = progress.friendRecords()
            val previous = records.optJSONObject(id) ?: return
            val modifiedAt = maxOf(System.currentTimeMillis(), previous.getLong("modifiedAt") + 1L)
            records.put(id,JSONObject().put("modifiedAt",modifiedAt).put("deleted",true))
            progress.saveFriendRecords(records)
            loadFriends()
        }
        ProgressSyncScheduler.enqueue(context)
    }
    fun startGitHubConnection() = connectGitHub()
    private fun connectGitHub(savedAuthorization: GitHubPkce.Session? = null) = task {
        try {
            route="friends"
            oauthStatus="Préparation de la connexion…"
            val token = if (authorizationClient.enabled) {
                val authorization = savedAuthorization ?: GitHubPkce.start()
                authorizationCode = CompletableDeferred()
                authSession = authorization
                pendingAuthorization.save(authorization)
                oauthStatus = "Autorise Hamigo dans ton navigateur habituel."
                if (savedAuthorization == null) openGitHubBrowser()
                val remaining = (authorization.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1L)
                val code = try { withTimeout(remaining) { authorizationCode!!.await() } }
                    catch (_: TimeoutCancellationException) { throw SocialException("La connexion GitHub a expiré. Relance-la.") }
                // Consume once before exchanging: a replayed App Link cannot launch another request.
                authorizationCode = null; authSession = null; pendingAuthorization.clear()
                oauthStatus = "GitHub a autorisé Hamigo. Vérification du compte…"
                githubBrowserCommands.value = GitHubBrowserCommand.Close
                authorizationClient.exchange(authorization, code)
            } else {
                val device = DeviceOAuth.start(GitHubApp.CLIENT_ID); oauthSession = device
                openGitHubBrowser()
                DeviceOAuth.awaitToken(GitHubApp.CLIENT_ID, device) { notice -> oauthStatus = notice }
            }
            // Receiving the token completes the browser part; a slow Gist must not keep it open.
            oauthSession=null;oauthStatus="GitHub a autorisé Hamigo. Vérification du compte…"
            githubBrowserCommands.value=GitHubBrowserCommand.Close
            var user:GitHubIdentity?=null
            for(attempt in 0..2) {
                try {user=sync.connect(token);break} catch(e:GitHubNetworkException) {
                    if(attempt==2)throw e
                    delay((attempt+1)*1500L)
                }
            }
            oauthStatus="Compte connecté. Première sauvegarde…"
            ProgressSyncScheduler.schedule(context)
            try {
                sync.synchronize(progress)
                message="Bienvenue ${user!!.login} ! Ton voyage est synchronisé."
            } catch(e:SocialException) {
                ProgressSyncScheduler.enqueue(context,0)
                message="Compte ${user!!.login} connecté. La sauvegarde sera réessayée : ${e.message}"
            }
            refresh()
        } finally {
            oauthSession = null; authSession = null; authorizationCode = null
            pendingAuthorization.clear(); oauthStatus = null
            githubBrowserCommands.value = GitHubBrowserCommand.Close
        }
    }
    fun disconnectGitHub() { taskJob?.cancel();socialJob?.cancel();sync.disconnect(); ProgressSyncScheduler.cancel(context); busy=false;refresh() }
    fun setAutoSync(enabled:Boolean) { progress.prefs.edit().putBoolean("autoSync",enabled).apply(); ProgressSyncScheduler.schedule(context); refresh() }
    fun acceptInvite(sendReciprocal:Boolean=false) {
        val invite=pendingInvite ?: return
        pendingInvite=null
        task {
            if(sync.savedGistUrl?.let { GitHubSync.gistId(it) } == invite) {message="C'est ton propre lien d'invitation.";return@task}
            val friend=sync.readProfile(invite)
            addFriend(Friend(friend.progress,"https://gist.github.com/$invite",githubIdentity=friend.identity,
                githubIdentityCheckedAt=System.currentTimeMillis()));route="friends"
            if(sendReciprocal) {
                try {
                    friendInbox.send(invite,friend.progress.name)
                    message="${friend.progress.name} ajouté. Ta demande réciproque est envoyée."
                } catch(e:CancellationException){throw e}
                catch(_:Exception){message="${friend.progress.name} ajouté. La demande n’a pas pu partir ; tu peux réessayer dans Équipe."}
            } else message="${friend.progress.name} rejoint ton équipe !"
            refresh()
        }
    }
    fun acceptFriendRequest(request:FriendRequest) = task {
        friendInbox.accept(request);refresh();message="${request.profile.name} rejoint ton équipe !"
    }
    fun ignoreFriendRequest(request:FriendRequest) {
        if(busy) return
        runCatching { friendInbox.ignore(request);refresh() }.onFailure { message=it.message }
    }
    fun retryFriendRequest(outgoing:OutgoingFriendRequestState) = task {
        try { friendInbox.retry(outgoing);message="Demande envoyée." }
        finally {refresh()}
    }
    fun sendFriendRequest(friend:Friend) = task {
        try { friendInbox.send(friend.gist,friend.progress.name);message="Ta demande réciproque est envoyée." }
        catch(e:CancellationException){throw e}
        catch(_:Exception){message="La demande n’a pas pu partir ; ton équipier est conservé. Tu peux réessayer dans Équipe."}
        finally {refresh()}
    }
    fun refreshSocial(manual: Boolean = false) {
        if(diagnosticModel){refresh();return}
        if(diagnostics.sandbox!=null)return
        if(busy || socialJob?.isActive==true) return
        // Foreground and finished sessions: network remains optional and never blocks learning.
        socialJob=scope.launch {
            socialRefreshing=true
            if(manual) busy=true
            try {
            var failures=0
            if((manual || progress.prefs.getBoolean("autoSync",true)) && runCatching { sync.tokens.get() }.getOrNull()!=null) {
                try { sync.synchronize(progress) } catch(e:CancellationException){throw e} catch(e:Exception){failures++}
            }
            val updates = friends.map { friend ->
                if(friend.gist.isBlank()) friend else try {
                    val profile=sync.readProfile(friend.gist)
                    friend.copy(progress=profile.progress,githubIdentity=profile.identity ?: friend.githubIdentity,
                        githubIdentityCheckedAt=if(profile.identity!=null) System.currentTimeMillis() else friend.githubIdentityCheckedAt)
                } catch(e:CancellationException){throw e} catch(e:Exception){failures++;friend}
            }
            val byGist=updates.filter{it.gist.isNotBlank()}.associateBy{it.gist}
            synchronized(Progress.CLOUD_LOCK) {
                loadFriends()
                friends=friends.map { previous ->
                    val updated=byGist[previous.gist]
                    val previousTime=runCatching { Instant.parse(previous.progress.updatedAt) }.getOrNull()
                    val updateTime=updated?.let { runCatching { Instant.parse(it.progress.updatedAt) }.getOrNull() }
                    if(updated==null) previous
                    else previous.copy(
                        progress=if(previousTime!=null && updateTime!=null && previousTime.isAfter(updateTime)) previous.progress else updated.progress,
                        githubIdentity=updated.githubIdentity,githubIdentityCheckedAt=updated.githubIdentityCheckedAt)
                }
                saveFriends()
            }; revision++
            try { friendInbox.refresh();loadFriendRequests() }
            catch(e:CancellationException){throw e}
            catch(_:Exception){failures++}
            if(manual) { message=if(failures==0) "Progressions actualisées." else "Connexion indisponible. Les dernières progressions restent consultables." }
            } finally {socialRefreshing=false;if(manual)busy=false}
        }
    }
    fun task(action: suspend () -> Unit) {
        if(diagnosticModel)return
        if(busy) return
        busy=true
        taskJob=scope.launch { try { action() } catch(e: CancellationException) {throw e} catch(e:Exception){message=e.message ?: "Opération impossible."} finally {busy=false;revision++} }
    }
    fun cancelTask() { taskJob?.cancel() }
    fun restore(json: String) { runCatching { progress.import(json);revision++;message="Sauvegarde restaurée." }.onFailure {message=it.message} }
    override fun onCleared() {
        if(::diagnostics.isInitialized&&!diagnosticModel)diagnostics.sandbox?.disposeDiagnostics()
        if(::interactionFeedback.isInitialized&&!diagnosticModel)interactionFeedback.close()
        if(::progress.isInitialized&&!diagnosticModel) {
            progress.prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            context.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }
        scope.cancel()
    }
}
