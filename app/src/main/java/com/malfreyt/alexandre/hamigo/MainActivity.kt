package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.browser.auth.AuthTabIntent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var model: AppModel
    private lateinit var gitHubBrowser: GitHubBrowser
    private val gitHubTabLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (::gitHubBrowser.isInitialized) gitHubBrowser.onTabResult()
    }
    private val gitHubAuthTabLauncher = AuthTabIntent.registerActivityResultLauncher(this) { result ->
        if (::gitHubBrowser.isInitialized) gitHubBrowser.onTabResult()
        if (::model.isInitialized) {
            when (result.resultCode) {
                AuthTabIntent.RESULT_OK -> result.resultUri?.toString()?.let { model.receiveGitHubAuthorization(it) }
                AuthTabIntent.RESULT_VERIFICATION_FAILED, AuthTabIntent.RESULT_VERIFICATION_TIMED_OUT ->
                    if (model.authSession != null && !model.githubTabOpen)
                        model.message = "Le navigateur n’a pas pu vérifier le retour vers Hamigo. Tu peux rouvrir l’onglet GitHub pour réessayer."
                // A Custom Tab fallback can report cancellation after an App Link already returned.
                // Keep the pending authorization available; AppModel validates and consumes it once.
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model=ViewModelProvider(this)[AppModel::class.java];model.initialize(this)
        gitHubBrowser=GitHubBrowser(this,model.githubBrowserState,
            launchAuthorization={ tab, uri ->
                val callback = Uri.parse(GitHubPkce.CALLBACK)
                tab.launch(gitHubAuthTabLauncher, uri, callback.host!!, callback.path!!)
            }) { gitHubTabLauncher.launch(it) }
        // Remains active while a Custom Tab covers this activity; Compose is paused then.
        lifecycleScope.launch {
            model.githubBrowserCommands.collect { command ->
                if(command!=null && model.takeGitHubBrowserCommand(command)) {
                    when(command) {
                        is GitHubBrowserCommand.Open -> runCatching {gitHubBrowser.open(command.session)}
                            .onFailure {model.message="Impossible d’ouvrir le navigateur. Tu peux saisir le code sur github.com/login/device."}
                        is GitHubBrowserCommand.OpenAuthorization -> runCatching {gitHubBrowser.openAuthorization(command.url)}
                            .onFailure {model.githubBrowserFailed("Impossible d’ouvrir le navigateur pour connecter GitHub. Réessaie depuis Hamigo.")}
                        GitHubBrowserCommand.Close -> gitHubBrowser.close()
                    }
                }
            }
        }
        // A widget/reminder destination is consumed once; recreating the same Activity keeps its current page.
        if(savedInstanceState==null)receive(intent)
        setContent { HamigoTheme { HamigoApp(model) } }
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);receive(intent)}
    override fun onResume() {super.onResume();if(::model.isInitialized && model.content!=null) {model.refresh();model.refreshSocial()}}
    override fun onPause() {stopMorse();super.onPause()}
    private fun receive(intent: Intent?) {
        val callback = intent?.dataString
        if (intent?.action == Intent.ACTION_VIEW && callback != null && GitHubPkce.isCallback(callback)) {
            try { model.receiveGitHubAuthorization(callback) }
            finally {
                // Activity recreation must not replay or retain an authorization code in Intent.data.
                intent.data = null
                if (this.intent?.dataString?.let(GitHubPkce::isCallback) == true) this.intent.data = null
            }
            return
        }
        intent?.getStringExtra("hamigo_route")?.takeIf {it in setOf("path","friends")}?.let {model.route=it}
        if(intent?.action==Intent.ACTION_VIEW&&callback!=null&&HamigoLinks.isFeedbackReturn(callback)) {
            // Keep the question, memo, scroll and draft that were present before opening Tally.
            intent.data=null
            if(this.intent?.dataString?.let(HamigoLinks::isFeedbackReturn)==true)this.intent.data=null
            return
        }
        if(intent?.action !in listOf(Intent.ACTION_SEND,Intent.ACTION_VIEW)) return
        @Suppress("DEPRECATION")
        val uri=if(intent?.action==Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else intent?.data
        if(uri!=null && uri.scheme in listOf("https","hamigo","hamigo-dev")) {
            val id=FriendInvite.parse(uri.toString())
            if(id!=null) model.pendingInvite=id
            else if(uri.scheme=="https" && uri.host=="alexmalfr.github.io" && uri.path==Uri.parse(FriendInvite.HTTPS_BASE).path && uri.query==null) model.route="friends"
            else model.message="Cette invitation Hamigo n’est pas valide."
            return
        }
        if(uri!=null) model.task { model.incoming=withContext(Dispatchers.IO) {
            readImport(contentResolver,uri)
        } }
    }
}

/** The raised circle overlays the screen; only the normal bar body reserves content space. */
internal fun hamigoContentPadding(padding: PaddingValues, layoutDirection: LayoutDirection, raisedBarVisible: Boolean): PaddingValues = PaddingValues(
    start=padding.calculateStartPadding(layoutDirection),
    top=padding.calculateTopPadding(),
    end=padding.calculateEndPadding(layoutDirection),
    bottom=(padding.calculateBottomPadding() - if(raisedBarVisible) HamigoNavigationCutoutDepth else 0.dp).coerceAtLeast(0.dp),
)

/** A completed gesture may finish visually before its unchanged screen is removed. */
private data class BackScreenSnapshot(
    val route: String,
    val session: Session?,
    val lesson: Lesson?,
    val resource: RefCategory?,
) {
    val dockRoute get() = when {
        session != null -> session.returnRoute
        resource != null -> if(lesson!=null) "path" else "resources"
        lesson != null -> "path"
        route == "settings" -> "profile"
        route == "exam-history" -> "practice"
        else -> route
    }
    fun isCurrent(model: AppModel) = model.route == route && model.session === session &&
        model.lesson === lesson && model.resource === resource
    fun navigate(model: AppModel) {
        when {
            session != null -> model.leaveSession()
            resource != null -> model.resource = null
            lesson != null -> model.backFromLesson()
            route == "settings" -> model.route = "profile"
            route == "exam-history" -> model.route = "practice"
            else -> model.route = "path"
        }
    }
}

@Composable fun HamigoApp(model: AppModel) {
    var reviewDialog by rememberSaveable {mutableStateOf(false)}
    var reviewApproved by rememberSaveable {mutableStateOf(false)}
    val permissionOwner=LocalActivityResultRegistryOwner.current
    val hostContext=LocalContext.current.applicationContext
    val speechHost=remember(hostContext,permissionOwner){SpeechInputHost(hostContext,permissionOwner)}
    FeedbackHost(model.interactionFeedback) {
        CompositionLocalProvider(LocalSpeechInputHost provides speechHost,LocalOpenDiagnostics provides {model.debugToolsOpen=true},LocalOpenReview provides {approved->reviewApproved=approved;reviewDialog=true},LocalDiagnosticsCovered provides model.debugToolsOpen) {
            val shown=model.diagnostics.sandbox ?: model
            FeedbackPreferenceScope(model.interactionFeedback,shown.progress.prefs) {key(shown){HamigoAppContent(shown,model)}}
            if(model.debugToolsOpen)DiagnosticsScreen(model)
            if(reviewDialog)ReviewAccessDialog(reviewApproved){reviewDialog=false}
        }
    }
}

@Composable internal fun HamigoAppContent(model:AppModel,diagnosticOwner:AppModel,diagnosticPreview:Boolean=false) {
    val dayContext=LocalContext.current.applicationContext
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model,lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            var day=StudyDay.date()
            while(kotlinx.coroutines.currentCoroutineContext().isActive) {
                val now=java.time.Instant.now()
                kotlinx.coroutines.delay(java.time.Duration.between(now,StudyDay.nextBoundary(now)).toMillis().coerceIn(100L,60_000L))
                val next=StudyDay.date()
                if(next!=day) {day=next;model.refresh();if(!model.diagnosticModel)HomeWidgets.progressChanged(dayContext)}
            }
        }
    }
    val pageStates=rememberSaveableStateHolder()
    val activeSession=model.session
    DisposableEffect(activeSession) {onDispose {
        if(activeSession!=null&&model.session!==activeSession)pageStates.removeState("session-${activeSession.attemptId}")
    }}
    val content=model.content
    val tick=model.revision
    val context=LocalContext.current
    var quit by remember {mutableStateOf(false)}
    var backProgress by remember {mutableFloatStateOf(0f)}
    var backWidth by remember {mutableFloatStateOf(0f)}
    var backHeight by remember {mutableFloatStateOf(0f)}
    var backEdge by remember {mutableIntStateOf(BackEventCompat.EDGE_LEFT)}
    var backDragY by remember {mutableFloatStateOf(0f)}
    var backGestureActive by remember {mutableStateOf(false)}
    var backDocking by remember {mutableStateOf(false)}
    var backSettling by remember {mutableStateOf(false)}
    var backDockProgress by remember {mutableFloatStateOf(0f)}
    var backDockCenter by remember {mutableStateOf(Offset.Zero)}
    var backDockScaleX by remember {mutableFloatStateOf(.05f)}
    var backDockScaleY by remember {mutableFloatStateOf(.05f)}
    var backMemoTarget by remember {mutableStateOf<MemoReturnTarget?>(null)}
    var backSource by remember {mutableStateOf<BackScreenSnapshot?>(null)}
    var backMotion by remember {mutableStateOf<Job?>(null)}
    var backGeneration by remember {mutableIntStateOf(0)}
    var windowBounds by remember {mutableStateOf(Rect.Zero)}
    var contentBounds by remember {mutableStateOf(Rect.Zero)}
    val navigationBounds=remember {mutableMapOf<String,Rect>()}
    val tourTargets=remember(model){HamigoTourTargets()}
    val guideAccessible=rememberTouchExploration()&&model.tour.step==0
    val backAnchors=remember {BackMotionAnchors()}
    val backAnimationScope=rememberCoroutineScope()
    val density=LocalDensity.current
    val layoutDirection=LocalLayoutDirection.current
    val navLeft=WindowInsets.navigationBars.getLeft(density,layoutDirection)
    val navRight=WindowInsets.navigationBars.getRight(density,layoutDirection)
    val navBottom=WindowInsets.navigationBars.getBottom(density)
    val raisedBarVisible=model.session==null && model.lesson==null && model.resource==null && model.route !in setOf("settings","exam-history") &&
        !(WindowInsets.ime.getBottom(density)>0&&questionToolsHorizontal())
    fun resetBackMotion() {
        backGeneration++
        backMotion?.cancel()
        backMotion=null
        backGestureActive=false
        backSettling=false
        backDocking=false
        backProgress=0f
        backDockProgress=0f
        backSource=null
        backMemoTarget=null
    }
    fun dockBounds(route:String):Rect {
        if(raisedBarVisible) navigationBounds[route]?.let {return it}
        // Hidden bars on detail screens use the same current window, insets and font geometry.
        val routes=listOf("practice","resources","path","friends","profile")
        val logicalIndex=routes.indexOf(route).takeIf {it>=0} ?: 2
        val index=if(layoutDirection==LayoutDirection.Rtl)4-logicalIndex else logicalIndex
        val availableWidth=(windowBounds.width-navLeft-navRight).coerceAtLeast(0f)
        val centerX=windowBounds.left+navLeft+availableWidth*(index+.5f)/5f
        val central=route=="path"
        val growth=((density.fontScale.coerceAtLeast(1f)-1f)*14f).dp
        val barHeight=with(density){(94.dp+growth).toPx()}
        val centerY=windowBounds.bottom-navBottom-barHeight+with(density){(if(central)34.dp else 46.dp).toPx()}
        val width=with(density){(if(central)64.dp else 56.dp).toPx()}
        val height=with(density){(if(central)64.dp else 40.dp).toPx()}
        return Rect(centerX-width/2f,centerY-height/2f,centerX+width/2f,centerY+height/2f)
    }
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(model.message) {model.message?.let {snackbar.showSnackbar(it);model.message=null}}
    if(content==null) {
        Box(Modifier.fillMaxSize().background(Cream),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Pico(Modifier.size(160.dp));Text("On accorde les antennes…",fontWeight=FontWeight.Bold,fontSize=22.sp)
                model.error?.let {Text(it,Modifier.padding(24.dp),color=Coral)} ?: CircularProgressIndicator(color=Teal)
            }
        };return
    }
    if(model.showWelcome) {OnboardingScreen(model);SocialRulesDialog(model);return}
    val p=model.progress
    LaunchedEffect(model.route,model.session,model.lesson,model.resource) {
        if(backSource?.isCurrent(model)==false) resetBackMotion()
    }
    BackHandler(enabled=model.session?.done==false) { quit=true }
    BackHandler(enabled=model.tour.step in 1..2) {model.tour.requestSkip()}
    BackHandler(enabled=model.tour.navigationPhase) {model.allowTourNavigation()}
    PredictiveBackHandler(enabled=!model.tour.navigationPhase&&model.tour.step !in 1..2&&model.session?.done!=false && (model.session!=null || model.lesson!=null || model.resource!=null || model.route!="path")) {events ->
        resetBackMotion()
        val generation=backGeneration
        val source=BackScreenSnapshot(model.route,model.session,model.lesson,model.resource)
        val memoTarget=source.resource?.let {MemoReturnTarget(it.id)}
        backMemoTarget=memoTarget
        backSource=source
        backGestureActive=true
        backProgress=0f
        backDragY=0f
        var firstTouchY:Float?=null
        try {
            events.collect {event ->
                if(generation==backGeneration) {
                    if(firstTouchY==null) firstTouchY=event.touchY
                    backProgress=event.progress.coerceIn(0f,1f)
                    backEdge=event.swipeEdge
                    backDragY=(event.touchY-firstTouchY!!)*.12f
                }
            }
            if(generation==backGeneration && source.isCurrent(model)) {
                backSettling=true
                backMotion=backAnimationScope.launch {
                    // Reveal and measure this exact fiche in an independent library preview.
                    // The foreground remains at its released size and position while it lays out.
                    val measuredTarget=when {
                        memoTarget!=null -> memoTarget.reveal()
                        source.route=="settings" -> withTimeoutOrNull(400) { snapshotFlow {backAnchors.settings}.filterNotNull().first() }
                            ?: Rect(contentBounds.right-with(density){64.dp.toPx()},contentBounds.top+with(density){16.dp.toPx()},contentBounds.right-with(density){16.dp.toPx()},contentBounds.top+with(density){64.dp.toPx()})
                        else -> dockBounds(source.dockRoute)
                    }
                    if(generation!=backGeneration) return@launch
                    if(!source.isCurrent(model)) {resetBackMotion();return@launch}
                    // A delayed layout may omit the visual docking, but must never prevent Back.
                    // Fade in place rather than inventing a different row or changing saved scroll.
                    val target=measuredTarget ?: Rect(
                        contentBounds.center.x-backWidth*.45f,contentBounds.center.y-backHeight*.45f,
                        contentBounds.center.x+backWidth*.45f,contentBounds.center.y+backHeight*.45f,
                    )
                    backDockCenter=target.center-contentBounds.topLeft
                    if(memoTarget!=null) {
                        backDockScaleX=(target.width/backWidth.coerceAtLeast(1f)).coerceIn(.01f,1f)
                        backDockScaleY=(target.height/backHeight.coerceAtLeast(1f)).coerceIn(.01f,1f)
                    } else {
                        val scale=(min(target.width/backWidth.coerceAtLeast(1f),target.height/backHeight.coerceAtLeast(1f))*.8f).coerceIn(.01f,.2f)
                        backDockScaleX=scale
                        backDockScaleY=scale
                    }
                    backDockProgress=0f
                    backDocking=true
                    animate(0f,1f,animationSpec=tween(220,easing=FastOutSlowInEasing)) {value,_ ->
                        if(generation==backGeneration) backDockProgress=value
                    }
                    if(generation==backGeneration) {
                        if(source.isCurrent(model)) {
                            if(measuredTarget!=null) memoTarget?.commitPosition()
                            source.navigate(model)
                        }
                        resetBackMotion()
                    }
                }
            } else if(generation==backGeneration) {
                resetBackMotion()
            }
        } catch(_:CancellationException) {
            // Separate scope lets a cancelled gesture settle without changing navigation or input.
            if(generation==backGeneration && source.isCurrent(model)) {
                val releasedProgress=backProgress
                backMotion=backAnimationScope.launch {
                    animate(releasedProgress,0f,animationSpec=tween(180,easing=FastOutSlowInEasing)) {value,_ ->
                        if(generation==backGeneration) backProgress=value
                    }
                    if(generation==backGeneration) resetBackMotion()
                }
            } else if(generation==backGeneration) {
                resetBackMotion()
            }
        }
    }
    LaunchedEffect(model.tour.step,model.lesson,model.session) {
        model.restoreTourLocation()
        if(model.tour.step in 1..2&&model.lesson==null&&model.session==null) {
            model.tour.move(1)
            content.lessons.firstOrNull {it.id==HamigoWelcomeLesson}?.let {model.startLesson(it)}
        }
    }
    CompositionLocalProvider(LocalHamigoTourTargets provides tourTargets,LocalTourNavigationGuard provides model::allowTourNavigation) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(modifier=Modifier.onGloballyPositioned {windowBounds=it.boundsInWindow()}.then(if(guideAccessible)Modifier.clearAndSetSemantics {} else Modifier),containerColor=Cream,snackbarHost={SnackbarHost(snackbar)},topBar={if(model.reviewMode)ReviewBanner()},bottomBar={
        if(raisedBarVisible) {
            HamigoBottomBar(model.route,onDestination={destination ->
                resetBackMotion()
                model.navigateTab(destination)
            },onDestinationBounds={route,bounds ->navigationBounds[route]=bounds},friendRequestCount=model.friendRequests.size,
                allowedDestinations=if(model.tour.navigationPhase)setOfNotNull(model.tour.currentRoute,model.tour.destination) else null)
        }
    }) { padding ->
        val regularPadding=hamigoContentPadding(padding,layoutDirection,raisedBarVisible)
        // An active question paints its footer behind the system navigation area.
        // The footer owns that inset, so its controls retain their previous safe position.
        val contentPadding=if(model.session?.done==false)PaddingValues(
            start=regularPadding.calculateStartPadding(layoutDirection),top=regularPadding.calculateTopPadding(),
            end=regularPadding.calculateEndPadding(layoutDirection),bottom=0.dp
        )else regularPadding
        CompositionLocalProvider(LocalNavigationContentOverlap provides if(raisedBarVisible)HamigoNavigationContentOverlap else 0.dp, LocalBackMotionAnchors provides backAnchors,
            LocalAnimatedBack provides { if(diagnosticPreview)BackScreenSnapshot(model.route,model.session,model.lesson,model.resource).navigate(model) else (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() }) {
        Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)
            .onGloballyPositioned {contentBounds=it.boundsInWindow()}
            .pointerInput(backSettling) {
                if(backSettling) awaitPointerEventScope {
                    while(true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}
                }
            }) {
            if(backGestureActive) {
                Box(Modifier.fillMaxSize().testTag("back-destination-${backSource?.dockRoute ?: model.route}")) {
                    when {
                        model.resource!=null && model.lesson!=null -> LessonScreen(model,model.lesson!!,backMemoTarget)
                        model.resource!=null->{
                            // Match the future library viewport without changing the departing fiche.
                            val growth=((density.fontScale.coerceAtLeast(1f)-1f)*14f).dp
                            val libraryBarBody=(94.dp+growth-HamigoNavigationCutoutDepth).coerceAtLeast(0.dp)
                            CompositionLocalProvider(LocalNavigationContentOverlap provides HamigoNavigationContentOverlap) {
                                Box(Modifier.fillMaxSize().padding(bottom=libraryBarBody)) {
                                    ResourceLibraryScreen(model,content,backMemoTarget)
                                }
                            }
                        }
                        model.route=="settings"->ProfileScreen(model,content)
                        model.session!=null || model.lesson!=null->MainDestination(model,content,backSource?.session?.returnRoute ?: model.route)
                        else->PathScreen(model,content)
                    }
                }
            }
            val memoMorph=backDocking && backMemoTarget!=null && backSource?.resource!=null
            val movement=backProgress*(2f-backProgress)
            val followX=backWidth*.34f*movement*(if(backEdge==BackEventCompat.EDGE_LEFT)1 else -1)
            val followY=backDragY.coerceIn(-with(density){24.dp.toPx()},with(density){24.dp.toPx()})*movement
            val followScale=1f-.07f*movement
            val dock=if(backDocking)backDockProgress else 0f
            val morphWidth=backWidth*(followScale+(backDockScaleX-followScale)*dock)
            val morphHeight=backHeight*(followScale+(backDockScaleY-followScale)*dock)
            val morphCenter=Offset(backWidth/2f+followX+(backDockCenter.x-backWidth/2f-followX)*dock,
                backHeight/2f+followY+(backDockCenter.y-backHeight/2f-followY)*dock)
            // Remeasure the frame into the row instead of stretching every glyph in the page.
            val frameModifier=if(memoMorph)Modifier.offset { androidx.compose.ui.unit.IntOffset((morphCenter.x-morphWidth/2).toInt(),(morphCenter.y-morphHeight/2).toInt()) }
                .size(with(density){morphWidth.toDp()},with(density){morphHeight.toDp()})
                .graphicsLayer { shape=RoundedCornerShape((28f+(18f-28f)*dock).dp);clip=true;shadowElevation=12.dp.toPx()*(1f-dock) }
            else Modifier.fillMaxSize().onSizeChanged {backWidth=it.width.toFloat();backHeight=it.height.toFloat()}.graphicsLayer {
                val movement=backProgress*(2f-backProgress)
                val followX=backWidth*.34f*movement*(if(backEdge==BackEventCompat.EDGE_LEFT)1 else -1)
                val followY=backDragY.coerceIn(-24.dp.toPx(),24.dp.toPx())*movement
                val dock=if(backDocking)backDockProgress else 0f
                translationX=followX+(backDockCenter.x-backWidth/2f-followX)*dock
                translationY=followY+(backDockCenter.y-backHeight/2f-followY)*dock
                val followScale=1f-.07f*movement
                scaleX=followScale+(backDockScaleX-followScale)*dock
                scaleY=followScale+(backDockScaleY-followScale)*dock
                alpha=1f-((dock-.72f)/.28f).coerceIn(0f,1f)
                shape=RoundedCornerShape((28*movement).dp);clip=backGestureActive
                shadowElevation=16.dp.toPx()*movement
            }
            Box(frameModifier.background(if(memoMorph)Color.White else Cream).testTag("back-foreground")) {
            Box(if(memoMorph)Modifier.wrapContentSize(Alignment.TopStart,unbounded=true)
                .requiredSize(with(density){backWidth.toDp()},with(density){backHeight.toDp()}).graphicsLayer {alpha=(1f-dock/.65f).coerceIn(0f,1f)} else Modifier.fillMaxSize()) {
            pageStates.SaveableStateProvider(model.visibleScreenKey()) {
            CompositionLocalProvider(LocalContext provides if(model.diagnosticModel)model.diagnosticContext else context) {when {
                model.session!=null -> QuizScreen(model)
                model.resource!=null -> ReferenceDetailScreen(model,model.resource!!)
                model.lesson!=null -> LessonScreen(model,model.lesson!!)
                model.route=="settings" -> SettingsScreen(model)
                else -> MainDestination(model,content)
            }}
            }
            }
            if(memoMorph)MemoMorphRow(backSource!!.resource!!,Modifier.fillMaxWidth().graphicsLayer { alpha=((dock-.18f)/.5f).coerceIn(0f,1f) },titleSize=(21f-5f*dock).sp)
            }
            if(!diagnosticPreview)DiagnosticHud(diagnosticOwner,model,Modifier.align(Alignment.TopStart).padding(start=8.dp,top=82.dp))
        }
        }
    }
    if(!diagnosticPreview)HamigoTourOverlay(model,tourTargets)
    }
    }
    if(quit) AlertDialog(onDismissRequest={quit=false},title={Text("Une pause radio ?")},text={Text(if(model.session?.exam==true)"Les épreuves finalisées sont enregistrées. Les réponses de l’épreuve en cours seront perdues si tu quittes." else "Ton XP et tes révisions sont enregistrés. Pour valider une leçon, vise au moins 80 % dès le premier essai et corrige les erreurs restantes.")},
        confirmButton={TextButton(feedbackClick {quit=false;model.leaveSession()}){Text("Quitter la séance")}},dismissButton={TextButton(feedbackClick {quit=false}){Text("Continuer")}})
    model.ignoredLessonRequest?.let { (lesson,_) ->
        AlertDialog(onDismissRequest={model.ignoredLessonRequest=null},title={Text("Suivre ce cours ?")},
            text={Text("« "+lesson.title+" » est ignoré. Pour le suivre, il sera d’abord marqué comme non ignoré. Ignorer ne donne aucun XP. Tu pourras gagner les récompenses habituelles en suivant le cours.",Modifier.verticalScroll(rememberScrollState()))},
            confirmButton={TextButton(feedbackClick {model.confirmIgnoredLessonLaunch()}){Text("Désignorer et ouvrir")}},
            dismissButton={TextButton(feedbackClick {model.ignoredLessonRequest=null}){Text("Annuler")}})
    }
    SocialRulesDialog(model)
    model.incoming?.let { json ->
        AlertDialog(onDismissRequest={model.incoming=null},title={Text("Restaurer la sauvegarde ?")},
            text={Text("La sauvegarde remplacera ta progression locale actuelle. Ton compte GitHub reste connecté.")},
            confirmButton={TextButton(feedbackClick {model.restore(json);model.incoming=null}){Text("Restaurer")}},
            dismissButton={TextButton(feedbackClick {model.incoming=null}){Text("Annuler")}})
    }
    if(!model.showWelcome && !model.socialConsentPending&&!model.socialDataConsentPending && model.oauthSession==null && model.authSession==null) model.pendingInvite?.let {
        val canRequest=model.sync.tokens.hasToken() && model.sync.savedGistUrl!=null
        AlertDialog(onDismissRequest={model.pendingInvite=null},icon={Pico(Modifier.size(64.dp),mood=MascotMood.GOOFY,pose=MascotPose.WAVE)},
            title={Text("Rejoindre cette équipe ?")},text={Text(if(canRequest)
                "Ajoute cet équipier et envoie-lui une demande pour qu’il puisse aussi t’ajouter. La demande sera visible dans les commentaires de son Gist social."
                else "Hamigo va récupérer le résumé de progression de cet équipier et l’ajouter à ton équipe. Connecte GitHub pour demander l’ajout en retour.")},
            confirmButton={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Button(onClick=feedbackClick {model.acceptInvite(sendReciprocal=canRequest)},enabled=!model.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(15.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Teal,contentColor=Color.White)) {
                    Text(if(canRequest) "Ajouter et envoyer la demande" else "Ajouter l’équipier",textAlign=TextAlign.Center,fontWeight=FontWeight.Bold)
                }
                if(canRequest) OutlinedButton(onClick=feedbackClick {model.acceptInvite()},enabled=!model.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(15.dp)) {Text("Ajouter seulement")}
                TextButton(feedbackClick {model.pendingInvite=null}) {Text("Annuler")}
            }})
    }
}

@Composable private fun MainDestination(model:AppModel,content:Content,route:String=model.route) {
    when(route) {
        "path"->PathScreen(model,content)
        "practice"->PracticeHubScreen(model,content)
        "exam-history"->ExamHistoryScreen(model,content)
        "resources"->ResourceLibraryScreen(model,content)
        "friends"->FriendsScreen(model)
        else->ProfileScreen(model,content)
    }
}

@Composable fun PageHeader(title: String,subtitle: String="",back: (() ->Unit)?=null) {
    if(back!=null) IconButton(back,Modifier.offset(x=(-12).dp)){Icon(Icons.Rounded.ArrowBack,"Retour")}
    BigTitle(title,subtitle)
}
