package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.sin

internal const val SpeechEndGraceMs=250L
internal enum class SpeechCapturePhase { IDLE, PREPARING, LISTENING, ENDING, PROCESSING }

/** A released preparation can never become a late recording; old callbacks cannot complete a new turn. */
internal class SpeechCaptureCycle {
    var phase by mutableStateOf(SpeechCapturePhase.IDLE);private set
    private var generation=0
    val active get()=phase!=SpeechCapturePhase.IDLE
    fun begin():Int {generation++;phase=SpeechCapturePhase.PREPARING;return generation}
    fun accepts(request:Int)=request==generation&&active
    fun ready(request:Int):Boolean {
        if(!accepts(request)||phase!=SpeechCapturePhase.PREPARING)return false
        phase=SpeechCapturePhase.LISTENING;return true
    }
    fun ending(request:Int):Boolean {
        if(!accepts(request)||phase!=SpeechCapturePhase.LISTENING)return false
        phase=SpeechCapturePhase.ENDING;return true
    }
    fun processing(request:Int):Boolean {
        if(!accepts(request)||phase==SpeechCapturePhase.PREPARING)return false
        phase=SpeechCapturePhase.PROCESSING;return true
    }
    fun complete(request:Int):Boolean {
        if(!accepts(request))return false
        cancel();return true
    }
    fun cancel(){generation++;phase=SpeechCapturePhase.IDLE}
}

internal object SpeechEnginePolicy {
    fun mayRetryOnDevice(error:Int,phase:SpeechCapturePhase,deviceAvailable:Boolean,triedDevice:Boolean):Boolean =
        phase==SpeechCapturePhase.PREPARING&&deviceAvailable&&!triedDevice&&error in setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT,SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,SpeechRecognizer.ERROR_CLIENT)
}

/** Only recognition and its permission use the real host; diagnostic preferences stay isolated. */
internal data class SpeechInputHost(val context:Context,val permissionOwner:ActivityResultRegistryOwner?)
internal val LocalSpeechInputHost=staticCompositionLocalOf<SpeechInputHost?> {null}
internal interface AnswerRecognitionPlatform {
    fun available(context:Context):Boolean
    fun onDeviceAvailable(context:Context):Boolean
    fun create(context:Context,onDevice:Boolean):SpeechRecognizer
}
internal object AndroidAnswerRecognitionPlatform:AnswerRecognitionPlatform {
    override fun available(context:Context)=runCatching {SpeechRecognizer.isRecognitionAvailable(context)}.getOrDefault(false)
    override fun onDeviceAvailable(context:Context)=Build.VERSION.SDK_INT>=31&&runCatching {SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}.getOrDefault(false)
    override fun create(context:Context,onDevice:Boolean):SpeechRecognizer = if(onDevice&&Build.VERSION.SDK_INT>=31)SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
}
internal val LocalAnswerRecognitionPlatform=staticCompositionLocalOf<AnswerRecognitionPlatform>{AndroidAnswerRecognitionPlatform}

/** Automatic prompts run once per occasion; an explicit user action may retry after refusal. */
internal enum class MicrophonePermissionOccasion(val key:String) { ONBOARDING("onboardingRequested"), QUESTION("questionRequested") }
internal object MicrophonePermissionHistory {
    const val STORE="hamigo_microphone"
    fun requested(context:Context,occasion:MicrophonePermissionOccasion)=context.getSharedPreferences(STORE,Context.MODE_PRIVATE).getBoolean(occasion.key,false)
    fun markRequested(context:Context,occasion:MicrophonePermissionOccasion) {
        context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().putBoolean(occasion.key,true).apply()
    }
}

internal data class MicrophonePermissionState(val granted:Boolean,val requested:Boolean,val pending:Boolean,val request:()->Unit,val settings:()->Unit)

@Composable internal fun rememberMicrophonePermission(occasion:MicrophonePermissionOccasion,autoRequest:Boolean):MicrophonePermissionState {
    val fallback=LocalContext.current
    val host=LocalSpeechInputHost.current
    val context=host?.context ?: fallback
    val permissionOwner=host?.permissionOwner ?: LocalActivityResultRegistryOwner.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    fun isGranted()=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    var granted by remember(context){mutableStateOf(isGranted())}
    var requested by remember(context,occasion){mutableStateOf(MicrophonePermissionHistory.requested(context,occasion))}
    var pending by rememberSaveable(occasion){mutableStateOf(false)}
    lateinit var launcher:ManagedActivityResultLauncher<String,Boolean>
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides requireNotNull(permissionOwner){"A microphone permission requires an Activity host"}) {
        launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed->granted=allowed;pending=false}
    }
    fun request() {
        if(granted||pending)return
        // Save before launching: an Activity can be recreated while Android owns the dialog.
        MicrophonePermissionHistory.markRequested(context,occasion);requested=true;pending=true
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(context,lifecycle,occasion) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME){
            granted=isGranted();requested=MicrophonePermissionHistory.requested(context,occasion)
            // Android owns any pending dialog. A restored process must not keep a stale spinner.
            pending=false
        }}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(autoRequest,granted,requested,pending){if(autoRequest&&!granted&&!requested&&!pending)request()}
    return MicrophonePermissionState(granted,requested,pending,::request,{openMicrophoneAppSettings(context)})
}

internal fun openMicrophoneAppSettings(context:Context) {
    openMicrophoneSettingsIntent(context,listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")),Intent(Settings.ACTION_APPLICATION_SETTINGS)))
}
private fun openMicrophoneSettingsIntent(context:Context,intents:List<Intent>) {
    for(intent in intents)try {context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return}
    catch(_:android.content.ActivityNotFoundException){} catch(_:SecurityException){}
    Toast.makeText(context,"Impossible d’ouvrir les réglages système.",Toast.LENGTH_SHORT).show()
}

/** Audio is requested by a live user gesture only; Hamigo stores neither audio nor partial transcripts. */
internal class AnswerRecognition(private val context:Context,private val vocabulary:List<String> = emptyList(),private val platform:AnswerRecognitionPlatform=AndroidAnswerRecognitionPlatform,private val result:(String)->Unit):AutoCloseable {
    private val cycle=SpeechCaptureCycle()
    val phase get()=cycle.phase
    val listening get()=phase==SpeechCapturePhase.LISTENING||phase==SpeechCapturePhase.ENDING
    val preparing get()=phase==SpeechCapturePhase.PREPARING
    val waiting get()=preparing||phase==SpeechCapturePhase.PROCESSING
    val active get()=cycle.active
    var notice by mutableStateOf("");private set
    var partialText by mutableStateOf("");private set
    var rmsDb by mutableStateOf<Float?>(null);private set
    private val manager=context.getSystemService(AudioManager::class.java)
    // Playback focus silences narration. Recording lifetime follows the gesture and recognizer,
    // not playback-focus losses that may be caused by the recognition service itself.
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build()
    private val main=Handler(Looper.getMainLooper())
    private var timeout:Runnable?=null
    private var captureTail:Runnable?=null
    private var recognizer:SpeechRecognizer?=null
    private var closed=false
    private var focused=false
    private var triedDevice=false
    private var microphoneOpen=false
    private fun opened(){if(!microphoneOpen){microphoneOpen=true;AppFeedback.active?.microphone(FeedbackCue.MIC_OPEN,this)}}
    private fun ended(){if(microphoneOpen){microphoneOpen=false;AppFeedback.active?.microphone(FeedbackCue.MIC_CLOSE,this)}}
    private val deviceAvailable get()=platform.onDeviceAvailable(context)
    val available get()=platform.available(context)||deviceAvailable

    fun start():Boolean {
        if(closed)return false
        if(active)return false
        if(!available){notice="La saisie vocale est indisponible. Tu peux écrire ta réponse.";return false}
        stopMorse()
        focused=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        FeedbackAudioGate.reserve(this)
        notice="";partialText="";rmsDb=null;triedDevice=false
        beginEngine(!platform.available(context))
        return active
    }
    private fun watch(request:Int,milliseconds:Long,message:String) {
        clearTimeout()
        timeout=Runnable {if(cycle.accepts(request)){cancel();notice=message}}.also {main.postDelayed(it,milliseconds)}
    }
    private fun clearCaptureTail(){captureTail?.let(main::removeCallbacks);captureTail=null}
    private fun clearTimeout(){timeout?.let(main::removeCallbacks);timeout=null}
    private fun destroyEngine(){recognizer?.let {runCatching {it.cancel()};runCatching {it.destroy()}};recognizer=null}
    private fun releaseAudio(){FeedbackAudioGate.release(this);if(focused)manager.abandonAudioFocusRequest(focus);focused=false}
    private fun finish(request:Int):Boolean {
        if(!cycle.complete(request))return false
        clearCaptureTail();clearTimeout();destroyEngine();releaseAudio();ended();partialText="";rmsDb=null
        return true
    }
    private fun beginEngine(onDevice:Boolean) {
        if(closed)return
        val request=cycle.begin()
        lastRequest=request
        destroyEngine()
        triedDevice=triedDevice||onDevice
        watch(request,12_000,"Le service vocal ne devient pas prêt. Réessaie ou utilise le clavier.")
        try {
            val engine=platform.create(context,onDevice)
            recognizer=engine
            engine.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?) {
                    if(cycle.ready(request)){clearTimeout();notice="";rmsDb=null;opened()}
                }
                override fun onBeginningOfSpeech()=Unit
                override fun onRmsChanged(rmsdB:Float) {
                    if(cycle.accepts(request)&&listening&&rmsdB.isFinite())rmsDb=rmsdB
                }
                override fun onBufferReceived(buffer:ByteArray?)=Unit
                override fun onEndOfSpeech() {
                    if(cycle.processing(request)) {
                        clearCaptureTail();ended();rmsDb=null
                        watch(request,15_000,"Le service vocal ne renvoie pas de résultat. Réessaie ou utilise le clavier.")
                    }
                }
                override fun onError(error:Int) {
                    if(!cycle.accepts(request)||closed)return
                    if(SpeechEnginePolicy.mayRetryOnDevice(error,phase,deviceAvailable,triedDevice)) {
                        notice="Essai de l’autre service vocal…"
                        beginEngine(true)
                        return
                    }
                    if(!finish(request))return
                    notice=when(error) {
                        SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->
                            "Aucun mot reconnu. Parle lorsque le micro indique l’écoute, ou utilise le clavier."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->
                            "Le microphone n’est pas autorisé. Le clavier reste disponible."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE->
                            "Le français n’est pas disponible dans ce service pour le moment. Vérifie le service vocal Android ou utilise le clavier."
                        SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->
                            "Le service vocal demande une connexion qui ne répond pas. Réessaie ou utilise le clavier."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY->
                            "Le service vocal est occupé. Attends un instant ou utilise le clavier."
                        SpeechRecognizer.ERROR_AUDIO->
                            "Une erreur audio a interrompu l’écoute. Réessaie ou utilise le clavier."
                        else->"La reconnaissance a été interrompue (code $error). Réessaie ou utilise le clavier."
                    }
                }
                override fun onResults(results:Bundle?) {
                    if(!cycle.accepts(request)||closed)return
                    val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if(!finish(request))return
                    if(text.isNotBlank()){notice="";result(text)}
                    else notice="Aucun mot reconnu. Utilise le clavier ou réessaie."
                }
                override fun onPartialResults(partialResults:Bundle?) {
                    if(cycle.accepts(request))partialText=
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                }
                override fun onEvent(eventType:Int,params:Bundle?)=Unit
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"fr-FR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            if(Build.VERSION.SDK_INT>=33&&vocabulary.isNotEmpty())intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS,ArrayList(vocabulary))
            engine.startListening(intent)
        } catch(error:Exception) {
            if(cycle.accepts(request)&&!onDevice&&deviceAvailable&&!triedDevice) {
                notice="Essai de l’autre service vocal…";beginEngine(true)
            } else if(finish(request))notice="Le service vocal est indisponible. Utilise le clavier."
        }
    }
    fun stop() {
        if(preparing){cancel();notice="Le micro n’était pas encore prêt. Maintiens jusqu’au passage en écoute.";return}
        val requestToken=cycleRequest()
        if(!cycle.ending(requestToken))return
        // Keep the real listening indicator and audio open for the last syllable, then drain
        // the recognizer's final result. Cancel/leave always closes this tail immediately.
        captureTail=Runnable {
            captureTail=null
            if(cycle.accepts(requestToken)&&phase==SpeechCapturePhase.ENDING)stopEngine(requestToken)
        }.also {main.postDelayed(it,SpeechEndGraceMs)}
    }
    private fun stopEngine(requestToken:Int) {
        if(!cycle.processing(requestToken))return
        ended();rmsDb=null
        watch(requestToken,15_000,"Le service vocal ne renvoie pas de résultat. Réessaie ou utilise le clavier.")
        runCatching {recognizer?.stopListening()}.onFailure {if(finish(requestToken))notice="Le service vocal a été interrompu. Utilise le clavier."}
    }
    private var lastRequest=0
    private fun cycleRequest()=lastRequest
    fun cancel(){cycle.cancel();clearCaptureTail();clearTimeout();destroyEngine();releaseAudio();ended();partialText="";rmsDb=null}
    override fun close(){closed=true;cancel()}
}

internal class WrittenAnswerState(val recognition:AnswerRecognition,val focus:FocusRequester,val mode:SpeechInputMode,
    val start:()->Boolean,val endPress:(Boolean)->Unit,val toggle:()->Unit,val useKeyboard:()->Unit,val permission:MicrophonePermissionState) {
    val permissionNotice get()=when {permission.granted->"";permission.pending->"Autorisation du microphone en cours…";else->"Le microphone n’est pas autorisé. Tu peux écrire ta réponse ou l’activer dans les réglages système."}
}

@Composable internal fun rememberWrittenAnswerState(key:String,question:Question,onChange:(String)->Unit,enabled:Boolean,
    permissionOccasion:MicrophonePermissionOccasion=MicrophonePermissionOccasion.QUESTION,requestPermissionOnOpen:Boolean=true):WrittenAnswerState {
    val uiContext=LocalContext.current
    val host=LocalSpeechInputHost.current
    val context=host?.context ?: uiContext
    val platform=LocalAnswerRecognitionPlatform.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val covered=LocalDiagnosticsCovered.current
    val keyboard=LocalSoftwareKeyboardController.current
    val mode=rememberSpeechInputMode()
    val focus=remember(key){FocusRequester()}
    var keyboardRequest by remember(key){mutableIntStateOf(0)}
    var held by remember(key){mutableStateOf(false)}
    var toggleWanted by remember(key){mutableStateOf(false)}
    val currentChange by rememberUpdatedState(onChange)
    val currentQuestion by rememberUpdatedState(question)
    val canStart by rememberUpdatedState(enabled&&!covered)
    val recognition=remember(context,key,platform){AnswerRecognition(context,if(NatoSpeechCanonicalizer.applies(question))NatoSpeechCanonicalizer.vocabulary else emptyList(),platform){text->
        if(canStart)currentChange(if(NatoSpeechCanonicalizer.applies(currentQuestion))NatoSpeechCanonicalizer.canonicalize(text) else text)
    }}
    val microphonePermission=rememberMicrophonePermission(permissionOccasion,requestPermissionOnOpen&&question.kind=="spoken"&&enabled&&!covered&&(recognition.available||permissionOccasion==MicrophonePermissionOccasion.ONBOARDING))
    fun startRecognizing():Boolean {
        if(!canStart||!microphonePermission.granted||!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))return false
        keyboard?.hide()
        return recognition.start()
    }
    DisposableEffect(recognition,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP){
            held=false;toggleWanted=false;recognition.cancel()
        }}
        lifecycle.addObserver(observer)
        onDispose {held=false;toggleWanted=false;lifecycle.removeObserver(observer);recognition.close()}
    }
    LaunchedEffect(enabled,covered,mode,microphonePermission.granted){if(!enabled||covered||!microphonePermission.granted){held=false;toggleWanted=false;recognition.cancel()}}
    DisposableEffect(recognition,mode) {onDispose {held=false;toggleWanted=false;recognition.cancel()}}
    LaunchedEffect(keyboardRequest){if(keyboardRequest>0){focus.requestFocus();keyboard?.show()}}
    fun requestStart():Boolean {
        if(!canStart||!recognition.available||ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return false
        return startRecognizing()
    }
    fun toggle() {
        when {
            recognition.phase in setOf(SpeechCapturePhase.ENDING,SpeechCapturePhase.PROCESSING)->Unit
            recognition.listening->{toggleWanted=false;held=false;recognition.stop()}
            recognition.active->{toggleWanted=false;held=false;recognition.cancel()}
            else->{held=false;toggleWanted=true;requestStart()}
        }
    }
    return WrittenAnswerState(recognition,focus,mode,{
        held=true;toggleWanted=false
        requestStart()
    },{released->
        held=false
        if(!released){toggleWanted=false;recognition.cancel()}
        else if(!toggleWanted)recognition.stop()
    },::toggle,{
        held=false;toggleWanted=false;recognition.cancel();keyboardRequest++
    },microphonePermission)
}

@Composable internal fun SpokenAnswerControls(state:WrittenAnswerState,enabled:Boolean) {
    val recognition=state.recognition
    val microphoneEnabled=enabled&&recognition.available&&state.permission.granted&&recognition.phase !in setOf(SpeechCapturePhase.ENDING,SpeechCapturePhase.PROCESSING)
    val compact=questionToolsHorizontal()
    val microphoneSize=if(compact)80.dp else 108.dp
    val bottomGap=if(compact)12.dp else 20.dp
    val wavesHeight=if(compact)24.dp else 28.dp
    val animationEnabled=ValueAnimator.areAnimatorsEnabled()
    val breathing=if(animationEnabled&&recognition.listening) {
        val transition=rememberInfiniteTransition(label="speech-breathing")
        val scale by transition.animateFloat(1f,1.035f,infiniteRepeatable(tween(1100),RepeatMode.Reverse),label="speech-microphone-scale")
        scale
    } else 1f
    val microphoneColor by animateColorAsState(
        if(recognition.listening)Coral else if(!microphoneEnabled)Teal.copy(alpha=.38f) else Teal,
        if(animationEnabled)tween(160) else snap(),label="speech-microphone-color")
    val currentState by rememberUpdatedState(state)
    Box(Modifier.fillMaxWidth().height(microphoneSize+bottomGap+wavesHeight+12.dp).testTag("question-spoken-controls")) {
        Canvas(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(wavesHeight).testTag("question-voice-level")
            .semantics {contentDescription="Niveau de la voix"}) {
            val level=recognition.rmsDb?.let {((it+2f)/12f).coerceIn(0f,1f)} ?: 0f
            val amplitude=if(recognition.listening)level*size.height*.38f else 0f
            val phase=if(animationEnabled)(breathing-1f)/.035f else 0f
            val path=Path()
            repeat(65) {index->
                val x=size.width*index/64f
                val envelope=sin(PI*index/64).toFloat()
                val y=size.height/2+sin(index/64f*PI*6+phase*PI).toFloat()*amplitude*envelope
                if(index==0)path.moveTo(x,y) else path.lineTo(x,y)
            }
            drawPath(path,if(recognition.listening)Coral else Teal.copy(alpha=.28f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom=bottomGap).size(microphoneSize)
            .graphicsLayer {scaleX=breathing;scaleY=breathing}
            .background(microphoneColor,CircleShape).testTag("question-microphone").semantics {
                role=Role.Button
                contentDescription=when {
                    !recognition.available->"Microphone indisponible. Le clavier reste disponible."
                    !state.permission.granted->"Microphone non autorisé. Le clavier reste disponible."
                    recognition.phase==SpeechCapturePhase.ENDING->"Fin de l’écoute"
                    recognition.phase==SpeechCapturePhase.PROCESSING->"Analyse de la réponse vocale"
                    recognition.listening->"Terminer la réponse vocale"
                    recognition.active->"Annuler la reconnaissance"
                    state.mode==SpeechInputMode.TOGGLE->"Appuyer pour donner ma réponse au microphone"
                    else->"Maintenir pour donner ma réponse au microphone"
                }
                stateDescription=when(recognition.phase) {
                    SpeechCapturePhase.PREPARING->"Préparation du microphone"
                    SpeechCapturePhase.LISTENING->"Écoute en cours"
                    SpeechCapturePhase.ENDING->"Fin de l’écoute"
                    SpeechCapturePhase.PROCESSING->"Reconnaissance en cours"
                    SpeechCapturePhase.IDLE->"Microphone arrêté"
                }
                if(!microphoneEnabled)disabled()
                onClick {if(microphoneEnabled)currentState.toggle();true}
            }.pointerInput(microphoneEnabled,recognition,state.mode) {
                if(microphoneEnabled) {
                    if(state.mode==SpeechInputMode.TOGGLE)detectTapGestures(onTap={currentState.toggle()})
                    else detectTapGestures(onPress={
                        currentState.start()
                        val released=tryAwaitRelease()
                        currentState.endPress(released)
                    })
                }
            },contentAlignment=Alignment.Center) {
            if(recognition.waiting)CircularProgressIndicator(Modifier.size(32.dp),color=Color.White,strokeWidth=3.dp)
            else Icon(if(recognition.listening)Icons.Rounded.GraphicEq else Icons.Rounded.Mic,null,Modifier.size(44.dp),tint=Color.White)
        }
        FilledTonalIconButton(state.useKeyboard,Modifier.align(Alignment.BottomCenter)
            .padding(bottom=bottomGap+(microphoneSize-48.dp)/2)
            .offset(x=if(compact)(-86).dp else (-104).dp).size(48.dp).testTag("question-use-keyboard"),enabled=enabled) {
            Icon(Icons.Rounded.Keyboard,"Écrire ma réponse au clavier")
        }
    }
}

@Composable internal fun WrittenAnswerInput(q:Question,value:String,onChange:(String)->Unit,enabled:Boolean,state:WrittenAnswerState,inputModifier:Modifier=Modifier,
    fieldLayout:@Composable (@Composable ()->Unit)->Unit={it()}) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(q.template.contains("morse-")&&q.bands.isNotEmpty()) {
            MorseListeningButton(q.bands.first(),outlined=true)
        }
        if(q.kind=="spoken")(state.permissionNotice.ifBlank {state.recognition.notice}).takeIf {it.isNotBlank()}?.let {
            Text(it,Modifier.semantics {liveRegion=LiveRegionMode.Polite},fontSize=13.sp,color=Teal)
        }
        if(q.kind=="spoken"&&!state.permission.granted&&!state.permission.pending)TextButton(state.permission.settings,Modifier.testTag("question-microphone-settings")) {
            Icon(Icons.Rounded.Settings,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Autoriser le microphone")
        }
        if(q.kind=="spoken"&&!state.recognition.available)Text("Aucun service de reconnaissance vocale n’est disponible. Le clavier reste disponible.",fontSize=13.sp,lineHeight=18.sp,color=Muted)
        if(q.kind=="spoken"&&state.recognition.active) {
            Text(when(state.recognition.phase) {
                SpeechCapturePhase.PREPARING->"Préparation du micro…"
                SpeechCapturePhase.LISTENING->"Écoute : parle maintenant."
                SpeechCapturePhase.ENDING->"Fin de l’écoute…"
                SpeechCapturePhase.PROCESSING->"Analyse de la réponse…"
                SpeechCapturePhase.IDLE->""
            },Modifier.testTag("question-speech-status").semantics {liveRegion=LiveRegionMode.Polite},fontSize=13.sp,color=Teal)
            state.recognition.partialText.takeIf {it.isNotBlank()}?.let {
                Text(it,Modifier.testTag("question-speech-partial"),fontSize=14.sp,color=Muted,maxLines=3)
            }
        }
        fieldLayout { BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density=androidx.compose.ui.platform.LocalDensity.current
            val measurer=rememberTextMeasurer()
            val style=MaterialTheme.typography.bodyLarge
            val width=with(density){(maxWidth-32.dp).roundToPx().coerceAtLeast(1)}
            val expectedLines=remember(q.acceptedAnswers,width,style) {
                q.acceptedAnswers.maxOfOrNull {measurer.measure(AnnotatedString(it),style=style,constraints=Constraints(maxWidth=width)).lineCount} ?: 1
            }
            val maximumHeight=LocalQuestionInputMaximumHeight.current
            val heightLimit=if(maximumHeight.value.isNaN())Modifier else Modifier.heightIn(max=maximumHeight)
            OutlinedTextField(value,{if(enabled)onChange(it.take(600))},label={Text("Ta réponse")},
                modifier=inputModifier.fillMaxWidth().then(heightLimit).focusRequester(state.focus).testTag("question-text-input"),enabled=enabled,textStyle=style,
                singleLine=expectedLines==1,minLines=expectedLines.coerceIn(1,4),maxLines=if(expectedLines==1)1 else 6,
                keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences))
        }}
        if(enabled&&q.kind=="text")Text(if(q.acceptedAnswers.any(WrittenAnswers::containsUnitSymbol))
            "Respecte les symboles d’unités : mW et MW diffèrent."
            else "Accents, majuscules et ponctuation ne changent pas la réponse.",fontSize=12.sp,color=Muted)
    }
}
