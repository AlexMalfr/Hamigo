package com.malfreyt.alexandre.hamigo

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.random.Random

enum class QuestionAnswerPlacement(val alignment: Alignment.Vertical) {
    TOP(Alignment.Top), CENTER(Alignment.CenterVertically), BOTTOM(Alignment.Bottom)
}
// Also used by the isolated comparative boards; no player setting or persisted preference.
internal val LocalQuestionAnswerPlacement=compositionLocalOf {QuestionAnswerPlacement.TOP}
internal val LocalQuestionPromptMinimumHeight=compositionLocalOf {androidx.compose.ui.unit.Dp.Unspecified}

/** Window structure is independent of the keyboard; resizing the app window can still change it. */
@Composable internal fun questionToolsHorizontal():Boolean {
    val window=LocalWindowInfo.current.containerSize
    val height=with(LocalDensity.current){window.height.toDp()}
    return window.height>0 && height<600.dp
}

/** One page scrolls. The prompt can grow beyond its preferred share without clipping its content. */
@Composable internal fun QuestionStage(modifier:Modifier=Modifier,scroll:ScrollState,
    compact:Boolean=false,illustrated:Boolean=false,prompt:@Composable ()->Unit,
    tools:(@Composable ()->Unit)?=null,answers:@Composable ColumnScope.()->Unit) {
    val placement=LocalQuestionAnswerPlacement.current
    BoxWithConstraints(modifier.testTag("question-stage")) {
        val viewportHeight=maxHeight
        val horizontalTools=questionToolsHorizontal()
        val answerClearance=if(tools==null)0.dp else if(horizontalTools)80.dp else 144.dp
        val promptHeight=maxHeight*.40f
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("question-answers")) {
            Box(Modifier.fillMaxWidth().heightIn(min=promptHeight).testTag("question-prompt-area")
                .padding(horizontal=16.dp,vertical=4.dp),contentAlignment=Alignment.Center) {
                CompositionLocalProvider(LocalQuestionPromptMinimumHeight provides (promptHeight-8.dp).coerceAtLeast(1.dp)) {prompt()}
            }
            Column(Modifier.fillMaxWidth().heightIn(min=(viewportHeight-promptHeight).coerceAtLeast(0.dp))
                .padding(start=16.dp,end=16.dp,top=if(compact)4.dp else 10.dp,bottom=answerClearance+(if(compact)4.dp else 10.dp)).testTag("question-answer-area"),
                verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 12.dp,placement.alignment),content=answers)
        }
        if(tools!=null) {
            val toolModifier=Modifier.align(Alignment.BottomEnd).padding(start=16.dp,end=16.dp,bottom=16.dp).testTag("question-tools")
            if(horizontalTools)Row(toolModifier,horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {tools()}
            else Column(toolModifier,verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.End) {tools()}
        }
    }
}

@Composable internal fun QuestionPrompt(q:Question,questionKey:String,feedback:Boolean?,stacked:Boolean=false,flashFlipped:Boolean=false,
    content:@Composable (Boolean)->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val speech=remember(context) {LessonSpeech(context,eager=false)}
    val onLeft=remember(questionKey) {Random.nextBoolean()}
    val expression=remember(questionKey) {listOf(MascotMood.HAPPY,MascotMood.GOOFY,MascotMood.DETERMINED,MascotMood.THINKING).random()}
    val flash=q.kind=="flash"
    val companion=rememberPicoCompanion(questionKey,enabled=flash)
    var previousFlip by remember(questionKey) {mutableStateOf(flashFlipped)}
    LaunchedEffect(flashFlipped) {
        if(flash&&previousFlip!=flashFlipped)companion.react(if(flashFlipped)PicoReaction.ANSWER else PicoReaction.STUDY)
        previousFlip=flashFlipped
    }
    val touch=rememberPicoNarrationTap(questionKey)
    LaunchedEffect(questionKey) {speech.stop()}
    DisposableEffect(speech,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)speech.stop()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);speech.close()}
    }
    val reading=speech.reading
    val talking=speech.speaking
    val mood=if(touch.playful)touch.mood else if(reading)MascotMood.HAPPY else if(feedback==true)MascotMood.CELEBRATE else if(feedback==false)MascotMood.THINKING else if(flash)companion.mood else expression
    val textMeasurer=rememberTextMeasurer()
    val textStyle=LocalTextStyle.current
    val density=LocalDensity.current
    val requestedHeight=LocalQuestionPromptMinimumHeight.current
    val narrationModifier=if(q.image==null&&q.kind!="flash")Modifier.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button,
        onClickLabel="Écouter la question") {touch.audio {speech.toggleText(q.prompt)}} else Modifier
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val preferredHeight=if(requestedHeight.value.isFinite())requestedHeight else if(constraints.hasBoundedHeight)maxHeight else 220.dp
        val baseMascotSize=if(flash)(preferredHeight*.32f).coerceIn(96.dp,160.dp).coerceAtMost(maxWidth*.46f)
            else if(stacked)(preferredHeight*.40f).coerceIn(76.dp,116.dp) else (preferredHeight*.76f).coerceIn(76.dp,132.dp).coerceAtMost(maxWidth*.31f)
        // Measure with the regular layout first, avoiding a resize/line-wrap feedback loop.
        val regularTextWidth=with(density){(maxWidth-baseMascotSize-10.dp).roundToPx().coerceAtLeast(1)}
        val regularLines=remember(q.prompt,regularTextWidth,textStyle,textMeasurer) {
            textMeasurer.measure(AnnotatedString(q.prompt),style=textStyle.copy(fontSize=24.sp,lineHeight=31.68.sp,fontWeight=FontWeight.ExtraBold),
                constraints=Constraints(maxWidth=regularTextWidth)).lineCount
        }
        val textPressure=((regularLines-4)/5f).coerceIn(0f,1f)
        val hasMath=remember(q.prompt){MathFormula.fragments(q.prompt).any {it.formula!=null}}
        val autoStack=!stacked&&(regularLines>=6||(hasMath&&regularLines>=4))
        val above=stacked||autoStack
        val mascotSize=if(autoStack)(preferredHeight*.33f).coerceIn(64.dp,96.dp)
            else if(stacked)baseMascotSize else baseMascotSize*(1f-textPressure*.30f)
        val gap=when {regularLines<=2->24.dp;regularLines==3->18.dp;else->10.dp}
        @Composable fun Mascot(modifier:Modifier=Modifier) {
            Box(modifier.size(mascotSize).testTag("question-pico")
                .semantics {
                    contentDescription=if(reading)"Arrêter la lecture de la question" else "Écouter la question avec Pico"
                    stateDescription=if(touch.closedEyes)"Pico ferme les yeux" else if(touch.playful)"Pico fait le clown" else if(talking)"Pico parle" else if(reading)"Lecture audio en préparation" else if(flash)companion.mood.description else "Pico présente la question"
                }.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button) {
                    touch.tap(SystemClock.uptimeMillis(),{speech.toggleText(q.prompt)},speech::stop)
                }) {
                TalkingPico(Modifier.fillMaxSize(),talking=talking,mood=mood,pose=if(touch.playful)MascotPose.HUG else if(flash)companion.pose else if(above)MascotPose.WAVE else MascotPose.POINT,
                    mirrored=onLeft&&!above,pointLeft=!above,eyesClosed=touch.closedEyes,idleMotion=flash&&companion.active,reaction=if(touch.playful)touch.burst else if(flash)companion.reaction else 0)
                Box(Modifier.align(if(onLeft&&!above)Alignment.BottomStart else Alignment.BottomEnd)
                    .offset(x=if(onLeft&&!above)(-8).dp else 8.dp).size(24.dp).background(Cream,CircleShape).testTag("question-pico-audio-hint")
                    .clickable(interactionSource=remember {MutableInteractionSource()},indication=null,role=Role.Button) {touch.audio {speech.toggleText(q.prompt)}},contentAlignment=Alignment.Center) {
                    Icon(if(reading)Icons.Rounded.Stop else Icons.Rounded.VolumeUp,if(reading)"Arrêter la lecture de la question" else "Écouter la question",Modifier.size(16.dp),tint=Muted.copy(alpha=.68f))
                }
            }
        }
        // Keep the intrinsically measured group centred in the preferred prompt area.
        // A minimum on the aligned Row itself would leave its alignment-line group at the top.
        Box(Modifier.fillMaxWidth().heightIn(min=preferredHeight),contentAlignment=Alignment.Center) {
            if(above)Column(Modifier.fillMaxWidth().testTag("question-prompt-group"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Mascot()
                Box(Modifier.fillMaxWidth().then(narrationModifier).testTag("question-text-region"),contentAlignment=Alignment.Center) {
                    content(true)
                }
            } else Row(Modifier.fillMaxWidth().testTag("question-prompt-group"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(gap)) {
                if(onLeft)Mascot(Modifier.alignBy(PicoBodyCenter))
                Box(Modifier.weight(1f).alignBy {it.measuredHeight/2}.then(narrationModifier).testTag("question-text-region"),contentAlignment=if(onLeft)Alignment.CenterStart else Alignment.CenterEnd) {
                    content(onLeft)
                }
                if(!onLeft)Mascot(Modifier.alignBy(PicoBodyCenter))
            }
        }
    }
}
