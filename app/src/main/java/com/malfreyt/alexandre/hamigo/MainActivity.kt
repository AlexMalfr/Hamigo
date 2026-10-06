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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.browser.auth.AuthTabIntent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.*
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
        receive(intent)
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
        if(intent?.getStringExtra("hamigo_route")=="path") model.route="path"
        if(intent?.action !in listOf(Intent.ACTION_SEND,Intent.ACTION_VIEW)) return
        @Suppress("DEPRECATION")
        val uri=if(intent?.action==Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else intent?.data
        if(uri!=null && uri.scheme in listOf("https","hamigo")) {
            val id=FriendInvite.parse(uri.toString())
            if(id!=null) model.pendingInvite=id
            else if(uri.scheme=="https" && uri.host=="alexmalfr.github.io" && uri.path=="/hamigo/" && uri.query==null) model.route="friends"
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
        resource != null -> "resources"
        lesson != null -> "path"
        route == "settings" -> "profile"
        else -> route
    }
    fun isCurrent(model: AppModel) = model.route == route && model.session === session &&
        model.lesson === lesson && model.resource === resource
    fun navigate(model: AppModel) {
        when {
            lesson != null -> model.lesson = null
            resource != null -> model.resource = null
            route == "settings" -> model.route = "profile"
            else -> model.route = "path"
        }
    }
}

@Composable fun HamigoApp(model: AppModel) {
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
    val backAnchors=remember {BackMotionAnchors()}
    val backAnimationScope=rememberCoroutineScope()
    val density=LocalDensity.current
    val layoutDirection=LocalLayoutDirection.current
    val navLeft=WindowInsets.navigationBars.getLeft(density,layoutDirection)
    val navRight=WindowInsets.navigationBars.getRight(density,layoutDirection)
    val navBottom=WindowInsets.navigationBars.getBottom(density)
    val raisedBarVisible=model.session==null && model.lesson==null && model.resource==null && model.route!="settings"
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
    if(model.showWelcome) {OnboardingScreen(model);return}
    val p=model.progress
    LaunchedEffect(model.route,model.session,model.lesson,model.resource) {
        if(backSource?.isCurrent(model)==false) resetBackMotion()
    }
    BackHandler(enabled=model.session!=null) { quit=true }
    PredictiveBackHandler(enabled=model.session==null && (model.lesson!=null || model.resource!=null || model.route!="path")) {events ->
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
    Scaffold(modifier=Modifier.onGloballyPositioned {windowBounds=it.boundsInWindow()},containerColor=Cream,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        if(raisedBarVisible) {
            HamigoBottomBar(model.route,onDestination={destination ->
                resetBackMotion()
                model.route=destination
            },onDestinationBounds={route,bounds ->navigationBounds[route]=bounds},friendRequestCount=model.friendRequests.size)
        }
    }) { padding ->
        val contentPadding=hamigoContentPadding(padding,layoutDirection,raisedBarVisible)
        CompositionLocalProvider(LocalNavigationContentOverlap provides if(raisedBarVisible)HamigoNavigationContentOverlap else 0.dp, LocalBackMotionAnchors provides backAnchors,
            LocalAnimatedBack provides { (context as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() }) {
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
                        model.session!=null || model.lesson!=null->MainDestination(model,content)
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
            when {
                model.session!=null -> QuizScreen(model)
                model.lesson!=null -> LessonScreen(model,model.lesson!!)
                model.resource!=null -> ReferenceDetailScreen(model,model.resource!!)
                model.route=="settings" -> SettingsScreen(model)
                else -> MainDestination(model,content)
            }
            }
            if(memoMorph)MemoMorphRow(backSource!!.resource!!,Modifier.fillMaxWidth().graphicsLayer { alpha=((dock-.18f)/.5f).coerceIn(0f,1f) },titleSize=(21f-5f*dock).sp)
            }
        }
        }
    }
    if(quit) AlertDialog(onDismissRequest={quit=false},title={Text("Une pause radio ?")},text={Text(if(model.session?.exam==true)"Les épreuves finalisées sont enregistrées. Les réponses de l’épreuve en cours seront perdues si tu quittes." else "Ton XP et tes révisions sont enregistrés. Pour valider une leçon, vise au moins 80 % dès le premier essai et corrige les erreurs restantes.")},
        confirmButton={TextButton({quit=false;model.leaveSession()}){Text("Quitter la séance")}},dismissButton={TextButton({quit=false}){Text("Continuer")}})
    model.incoming?.let { json ->
        AlertDialog(onDismissRequest={model.incoming=null},title={Text("Restaurer la sauvegarde ?")},
            text={Text("La sauvegarde remplacera ta progression locale actuelle. Ton compte GitHub reste connecté.")},
            confirmButton={TextButton({model.restore(json);model.incoming=null}){Text("Restaurer")}},
            dismissButton={TextButton({model.incoming=null}){Text("Annuler")}})
    }
    if(!model.showWelcome && model.oauthSession==null && model.authSession==null) model.pendingInvite?.let {
        val canRequest=model.sync.tokens.hasToken() && model.sync.savedGistUrl!=null
        AlertDialog(onDismissRequest={model.pendingInvite=null},icon={Pico(Modifier.size(64.dp),mood=MascotMood.GOOFY,pose=MascotPose.WAVE)},
            title={Text("Rejoindre cette équipe ?")},text={Text(if(canRequest)
                "Ajoute cet équipier et envoie-lui une demande pour qu’il puisse aussi t’ajouter. La demande sera visible dans les commentaires de son Gist social."
                else "Hamigo va récupérer le résumé de progression de cet équipier et l’ajouter à ton équipe. Connecte GitHub pour demander l’ajout en retour.")},
            confirmButton={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Button(onClick={model.acceptInvite(sendReciprocal=canRequest)},enabled=!model.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(15.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=Teal,contentColor=Color.White)) {
                    Text(if(canRequest) "Ajouter et envoyer la demande" else "Ajouter l’équipier",textAlign=TextAlign.Center,fontWeight=FontWeight.Bold)
                }
                if(canRequest) OutlinedButton(onClick={model.acceptInvite()},enabled=!model.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(15.dp)) {Text("Ajouter seulement")}
                TextButton({model.pendingInvite=null}) {Text("Annuler")}
            }})
    }
}

@Composable private fun MainDestination(model:AppModel,content:Content) {
    when(model.route) {
        "path"->PathScreen(model,content)
        "practice"->PracticeHubScreen(model,content)
        "resources"->ResourceLibraryScreen(model,content)
        "friends"->FriendsScreen(model)
        else->ProfileScreen(model,content)
    }
}

@Composable fun PageHeader(title: String,subtitle: String="",back: (() ->Unit)?=null) {
    if(back!=null) IconButton(back,Modifier.offset(x=(-12).dp)){Icon(Icons.Rounded.ArrowBack,"Retour")}
    BigTitle(title,subtitle)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun PathScreen(model:AppModel, content:Content) {
    val p=model.displayedProgress ?: model.progress
    val completed=p.completed
    var expanded by remember {mutableStateOf(content.chapters.indexOfFirst { c->c.lessons.any {it.id !in completed} }.coerceAtLeast(0))}
    val next=content.nextLesson(completed)
    val due=p.due(content)
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().testTag("path-list"),state=listState,contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=56.dp+LocalNavigationContentOverlap.current),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        stickyHeader {
            Row(Modifier.fillMaxWidth().stickyHeaderShadow(listState).background(Cream).padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Eyebrow("HAMIGO");Text("Salut ${p.name.split(' ').first()} !",fontSize=23.sp,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)}
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Mist.copy(alpha=.6f))
                    .clickable(role=Role.Button) {model.route="profile"}
                    .semantics(mergeDescendants=true) {contentDescription="Voir ma progression : ${p.streak} jours de série, ${p.xp} XP"}
                    .padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    Column(horizontalAlignment=Alignment.End) {Text("🔥 ${p.streak} jours",fontWeight=FontWeight.Bold,color=Color(0xFFB44D30));Text("⚡ ${p.xp} XP",fontSize=13.sp,color=Teal)}
                    Icon(Icons.Rounded.ChevronRight,null,Modifier.size(17.dp),tint=Muted)
                }
            }
        }
        item {
            Panel(color=Mist) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Eyebrow("TA MISSION DU JOUR");Spacer(Modifier.height(5.dp));Text("Une petite onde,\nun grand déclic.",fontSize=23.sp,lineHeight=27.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(5.dp));Text("${p.todayXp} / ${p.dailyGoal} XP aujourd'hui",fontSize=13.sp,color=Muted)}
                    Pico(Modifier.size(94.dp),mood=if(p.todayXp>=p.dailyGoal)MascotMood.CELEBRATE else if(p.streak>0)MascotMood.HAPPY else MascotMood.DETERMINED,pose=if(p.todayXp>=p.dailyGoal)MascotPose.DANCE else MascotPose.WAVE)
                }
                LinearProgressIndicator(progress={ (p.todayXp.toFloat()/p.dailyGoal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(8.dp),color=Teal,trackColor=Color.White)
                Action(if(next==null)"Parcours terminé · refaire un défi" else "Continuer · ${next.title}") {
                    if(next!=null)model.startLesson(next) else model.route="practice"
                }
            }
        }
        if(due.isNotEmpty()) item {
            Surface(onClick={if(due.isNotEmpty())model.startQuestions("Les ondes reviennent",due.take(12)) else model.route="resources"},shape=RoundedCornerShape(20.dp),color=Color(0xFFFFEDDC)) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh,"Réviser",tint=Color(0xFFB76D36));Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {Text(if(due.isEmpty())"Ta mémoire prend de l'avance" else "${due.size} notions à revoir",fontWeight=FontWeight.Bold);Text(if(due.isEmpty())"Découvre les fiches mémo" else "Une révision au bon moment, ça reste.",fontSize=12.sp,color=Muted)}
                    Icon(Icons.Rounded.ChevronRight,null)
                }
            }
        }
        item {BigTitle("Ton voyage radio","${completed.size} / ${content.lessons.size} leçons · à ton rythme")}
        items(content.chapters.size) { index ->
            val c=content.chapters[index];val color=ChapterColors[index%ChapterColors.size]
            val finished=c.lessons.count {it.id in completed}
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(onClick={expanded=if(expanded==index)-1 else index},modifier=Modifier.testTag("chapter-${c.id}")
                    .semantics {stateDescription=if(expanded==index)"Déplié" else "Replié"},shape=RoundedCornerShape(22.dp),color=color) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("%02d".format(index+1),Modifier.background(Color.White.copy(alpha=.18f),RoundedCornerShape(14.dp)).padding(12.dp),fontSize=21.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
                        val complete=c.lessons.isNotEmpty() && finished==c.lessons.size
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.weight(1f).testTag("chapter-content-${c.id}")) {
                            Column(Modifier.fillMaxWidth().testTag("chapter-copy-${c.id}")) {
                                Text(c.title,Modifier.fillMaxWidth(),color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("${finished}/${c.lessons.size} · ${c.subtitle}",
                                    Modifier.fillMaxWidth().padding(end=if(complete)52.dp else 0.dp),
                                    color=Color.White.copy(alpha=.85f),fontSize=12.sp,lineHeight=16.sp)
                            }
                            if(complete)Box(Modifier.matchParentSize()) {
                                ChapterCompletionSticker(c.id,Modifier.align(Alignment.CenterEnd)
                                    .wrapContentSize(Alignment.CenterEnd,unbounded=true).size(68.dp).offset(x=12.dp))
                            }
                        }
                        Icon(if(expanded==index)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null,tint=Color.White)
                    }
                }
                if(expanded==index) c.lessons.forEachIndexed { i,l ->
                    val done=l.id in completed;val active=l.id==next?.id
                    Row(Modifier.fillMaxWidth().padding(start=if(i%2==0)12.dp else 34.dp,end=if(i%2==0)34.dp else 12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(onClick={model.startLesson(l)},modifier=Modifier.size(54.dp),shape=CircleShape,color=if(done)color else if(active)Gold else Color.White,shadowElevation=if(active)5.dp else 1.dp) {
                            Box(contentAlignment=Alignment.Center) {Icon(if(done)Icons.Rounded.Check else if(active)Icons.Rounded.PlayArrow else Icons.Rounded.RadioButtonUnchecked,l.title,tint=if(done)Color.White else color)}
                        }
                        Spacer(Modifier.width(15.dp))
                        Column(Modifier.weight(1f).clickable {model.startLesson(l)}.padding(vertical=13.dp)) {
                            Text(l.title,fontWeight=if(active)FontWeight.ExtraBold else FontWeight.Bold,fontSize=15.sp)
                            Text(if(done)"Signal reçu ✓" else if(active)"À toi de jouer · 5 à 8 min" else l.summary,maxLines=2,fontSize=12.sp,color=Muted)
                        }
                    }
                }
            }
        }
        item {Text("Parcours libre : tu peux explorer une leçon à tout moment. La prochaine étape conseillée reste la même pour tous.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(vertical=8.dp))}
    }
}

@Composable fun LessonScreen(model:AppModel,lesson:Lesson) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            PageHeader(lesson.title,lesson.summary){model.lesson=null}
            Row(verticalAlignment=Alignment.CenterVertically) {Pico(Modifier.size(64.dp),mood=MascotMood.THINKING,pose=MascotPose.POINT);Spacer(Modifier.width(12.dp));Text("D'abord le déclic.\nEnsuite, à toi de jouer.",fontWeight=FontWeight.Bold,color=Teal)}
            lesson.body.forEach { MorseAwareText(it,fontSize=16.sp,lineHeight=24.sp) }
            if(lesson.formula.isNotBlank()) Panel(color=Mist){Eyebrow("À RETENIR");MorseAwareText(lesson.formula,fontSize=21.sp,fontWeight=FontWeight.Bold)}
            Text("Cours original Hamigo, adapté des ressources F6KGL. Les références sont dans les réglages.",fontSize=12.sp,color=Muted)
        }
        Action("À toi de jouer · ${lesson.questions.size} à ${lesson.questions.size+2} défis",Modifier.padding(16.dp)) {model.startQuestions(lesson.title,LessonSessionBuilder.create(lesson),lesson.id)}
    }
}
