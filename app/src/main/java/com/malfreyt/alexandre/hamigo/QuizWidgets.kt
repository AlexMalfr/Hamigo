package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class ClozeDragState {
    var slot by mutableStateOf(Rect.Zero)
    val chips=mutableStateMapOf<Int,Rect>()
    var dragged by mutableIntStateOf(-1)
    var position by mutableStateOf(Offset.Zero)
}
@Composable fun rememberClozeDragState(key:String)=remember(key) {ClozeDragState()}

@Composable fun ClozeSentence(q:Question,choice:Int,enabled:Boolean,feedback:Boolean?,state:ClozeDragState,textAlign:androidx.compose.ui.text.style.TextAlign=androidx.compose.ui.text.style.TextAlign.Start,onChoice:(Int)->Unit) {
    val sentence=remember(q.prompt) {buildAnnotatedString {
        val parts=q.prompt.split("___",limit=2)
        append(parts.firstOrNull().orEmpty());appendInlineContent("answer","[mot à compléter]")
        if(parts.size>1)append(parts[1])
    }}
    val hover=state.dragged>=0&&state.slot.contains(state.position)
    val accent=if(feedback==null)Purple else if(choice==q.answer)Teal else Color(0xFFB65049)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val start=q.prompt.substringBefore("___").length
    val font=questionTextSize(sentence,constraints.maxWidth,listOf(androidx.compose.ui.text.AnnotatedString.Range(
        Placeholder(108.sp,28.sp,PlaceholderVerticalAlign.Center),start,start+"[mot à compléter]".length)))
    Text(sentence,color=Ink,fontWeight=FontWeight.ExtraBold,fontSize=font,lineHeight=(font.value*1.32f).sp,textAlign=textAlign,
        inlineContent=mapOf("answer" to InlineTextContent(Placeholder(108.sp,28.sp,PlaceholderVerticalAlign.Center)) {
            Surface(onClick=feedbackClick {onChoice(-1)},enabled=enabled&&choice>=0,shape=RoundedCornerShape(10.dp),
                modifier=Modifier.fillMaxSize().padding(2.dp).onGloballyPositioned {state.slot=it.boundsInWindow()}
                    .semantics {contentDescription=if(choice<0)"Emplacement pour le mot à compléter" else "Mot choisi : ${q.choices.getOrNull(choice).orEmpty()}. Toucher pour enlever."},
                color=if(hover)Gold.copy(alpha=.4f) else accent.copy(alpha=.10f),border=BorderStroke(2.dp,accent)) {
                Box(contentAlignment=Alignment.Center) {
                    if(choice>=0)MorseAwareText(q.choices[choice],Modifier.padding(horizontal=3.dp),fontSize=14.sp,fontWeight=FontWeight.Bold,color=accent,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    else Icon(Icons.Rounded.Add,null,tint=accent,modifier=Modifier.size(22.dp))
                }
            }
        }))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ClozeBoard(q:Question,choice:Int,enabled:Boolean,feedback:Boolean?=null,
    showPrompt:Boolean=true,state:ClozeDragState=rememberClozeDragState(q.id),onChoice:(Int)->Unit) {
    val currentChoice by rememberUpdatedState(onChoice)
    val feedbackController=LocalAppFeedback.current
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(showPrompt)ClozeSentence(q,choice,enabled,feedback,state,onChoice=onChoice)
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            q.choices.forEachIndexed {index,word->
                Surface(onClick=feedbackAction(FeedbackCue.SELECT) {currentChoice(index)},enabled=enabled,shape=RoundedCornerShape(10.dp),
                    color=if(choice==index)Purple else Color.White,border=BorderStroke(1.dp,Purple.copy(alpha=.4f)),
                    modifier=Modifier.onGloballyPositioned {state.chips[index]=it.boundsInWindow()}
                        .graphicsLayer {alpha=if(state.dragged==index).35f else 1f}
                        .pointerInput(q.id,index,enabled) {
                            if(enabled)detectDragGestures(onDragStart={feedbackController?.event(FeedbackCue.DRAG);state.dragged=index;state.position=(state.chips[index]?.topLeft ?: Offset.Zero)+it},
                                onDrag={change,delta->change.consume();state.position+=delta},
                                onDragEnd={if(state.slot.contains(state.position)){currentChoice(index);feedbackController?.event(FeedbackCue.SNAP)};state.dragged=-1},onDragCancel={state.dragged=-1})
                        }) {
                    MorseAwareText(word,Modifier.padding(horizontal=17.dp,vertical=13.dp),fontSize=16.sp,fontWeight=FontWeight.Bold,color=if(choice==index)Color.White else Purple)
                }
            }
        }
    }
    val draggedWord=q.choices.getOrNull(state.dragged)
    if(draggedWord!=null) {
        val density=androidx.compose.ui.platform.LocalDensity.current
        val position=state.position
        val provider=remember(position,density) {object:androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds:androidx.compose.ui.unit.IntRect,windowSize:androidx.compose.ui.unit.IntSize,layoutDirection:androidx.compose.ui.unit.LayoutDirection,popupContentSize:androidx.compose.ui.unit.IntSize)=
                androidx.compose.ui.unit.IntOffset((position.x-popupContentSize.width/2).toInt(),(position.y-popupContentSize.height/2).toInt())
        }}
        androidx.compose.ui.window.Popup(popupPositionProvider=provider,properties=androidx.compose.ui.window.PopupProperties(focusable=false,clippingEnabled=false)) {
            Surface(shape=RoundedCornerShape(10.dp),color=Purple,shadowElevation=6.dp) {
                MorseAwareText(draggedWord,Modifier.padding(horizontal=17.dp,vertical=13.dp),fontSize=16.sp,fontWeight=FontWeight.Bold,color=Color.White)
            }
        }
    }
}

@Composable fun TrueFalseBoard(q: Question, choice: Int, feedback: Boolean?, onChoice: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        q.choices.forEachIndexed { index, text ->
            Surface(onClick=feedbackAction(FeedbackCue.TRUE_FALSE) { onChoice(index) }, enabled = feedback == null, modifier = Modifier.weight(1f).height(108.dp), shape = RoundedCornerShape(18.dp),
                color = if (feedback != null && index == q.answer) Mist else if (choice == index) Gold.copy(alpha = .35f) else Color.White,
                border = BorderStroke(if (choice == index) 2.dp else 1.dp, if (choice == index) Teal else Color(0xFFD5DEDA))) {
                // Center the icon and label as a single group in the complete tile, not at its top.
                Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                    val isTrue = text.trim().equals("Vrai", ignoreCase = true)
                    Icon(if (isTrue) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = if (isTrue) Teal else Coral, modifier = Modifier.size(30.dp))
                    Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
                }
            }
        }
    }
}

@Composable fun MorseSymbols(code:String,modifier:Modifier=Modifier,color:Color=Teal) {
    MorseVisual(code,modifier=modifier,compact=true,color=color)
}

fun normalizeMorse(code:String):String=code.replace('·','.').replace('•','.').replace('−','-').replace('–','-')
    .filter{it=='.'||it=='-'||it=='/'||it.isWhitespace()}.trim().replace(Regex("\\s+")," ").replace(Regex("\\s*/\\s*")," / ")

@Composable fun MorseComposer(code:String,onChange:(String)->Unit,enabled:Boolean) {
    Panel(color=Mist) {
        if(code.isBlank())Text("Ton message attend son premier point…",fontSize=13.sp,color=Muted)
        else MorseSymbols(code)
        val feedback=LocalAppFeedback.current
        MorseSignalInput(enabled) { feedback?.event(if(it=='.')FeedbackCue.MORSE_DOT else if(it=='-')FeedbackCue.MORSE_DASH else FeedbackCue.SNAP);onChange(code + it) }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            OutlinedButton(feedbackClick {if(code.isNotBlank()&&!code.endsWith(" "))onChange(code+" ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(6.dp)){Text("Lettre suivante",fontSize=11.sp)}
            OutlinedButton(feedbackClick {if(code.isNotBlank()&&!code.endsWith("/ "))onChange(code.trimEnd()+" / ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(6.dp)){Text("Mot suivant",fontSize=11.sp)}
            IconButton(feedbackClick {onChange(code.dropLast(1))},enabled=enabled&&code.isNotBlank()){Icon(Icons.Rounded.Backspace,"Effacer le dernier symbole")}
        }
        OutlinedButton(feedbackClick {playMorse(code)},enabled=code.isNotBlank(),modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.VolumeUp,null);Spacer(Modifier.width(8.dp));Text("Écouter ma transmission")}
    }
}

@Composable fun BinarySwitches(value:Int,bits:Int,enabled:Boolean,onChange:(Int)->Unit) {
    Panel(color=Purple.copy(alpha=.10f)) {
        Text("${bits} bits · de gauche à droite, les poids diminuent",fontSize=12.sp,color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth()) {
            (bits-1 downTo 0).forEach{bit -> val active=value and (1 shl bit)!=0
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("${1 shl bit}",fontSize=11.sp,color=Muted)
                    Surface(onClick=feedbackAction(if(active)FeedbackCue.BINARY_OFF else FeedbackCue.BINARY_ON) {onChange(value xor (1 shl bit))},enabled=enabled,shape=RoundedCornerShape(10.dp),color=if(active)Purple else Color.White,
                        border=BorderStroke(1.dp,Purple.copy(alpha=.4f)),modifier=Modifier.fillMaxWidth().height(52.dp).semantics {contentDescription="Bit de poids ${1 shl bit} : ${if(active)1 else 0}"}) {
                        Box(contentAlignment=Alignment.Center){Text(if(active)"1"else"0",color=if(active)Color.White else Purple,fontWeight=FontWeight.ExtraBold,fontSize=22.sp)}
                    }
                }
            }
        }
        Text("Lis chaque interrupteur comme un 0 ou un 1.",fontSize=11.sp,color=Muted)
    }
}

@Composable fun WaveformPreview(kind:String,modifier:Modifier=Modifier,color:Color=Teal) {
    Canvas(modifier.fillMaxWidth().height(68.dp).semantics {contentDescription=when(kind){"am"->"Oscillation dont l'amplitude varie";"fm"->"Oscillation dont la fréquence varie";"square"->"Signal carré";"dc"->"Signal constant";"noise"->"Signal irrégulier";else->"Oscillation sinusoïdale"}}) {
        drawLine(Muted.copy(alpha=.18f),Offset(0f,size.height/2f),Offset(size.width,size.height/2f),1.dp.toPx())
        val p=Path()
        repeat(240){i ->
            val t=i/239.0
            val y=when(kind) {
                "am"->sin(t*PI*26)*(.35+.65*(sin(t*PI*2)+1)/2)
                "fm"->sin(t*PI*18+4*sin(t*PI*2))
                "square"->if(sin(t*PI*8)>=0).75 else -.75
                "dc"->.45
                "noise"->sin(t*513)*.35+sin(t*173)*.3+sin(t*61)*.2
                else -> sin(t*PI*8)*.85
            }
            val x=t.toFloat()*size.width;val cy=size.height/2f-y.toFloat()*size.height*.36f
            if(i==0)p.moveTo(x,cy)else p.lineTo(x,cy)
        }
        drawPath(p,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
    }
}
