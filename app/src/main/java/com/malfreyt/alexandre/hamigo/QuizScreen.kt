package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import com.malfreyt.alexandre.hamigo.platform.NativeShare
import com.malfreyt.alexandre.hamigo.platform.ShareResults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import kotlin.random.Random

@Composable fun QuizScreen(model:AppModel) {
    val s=model.session ?: return
    val tick=model.revision
    if(s.done) {ResultsScreen(model,s);return}
    val timer=rememberExamClock(model,s)
    if(s.examIntroPending) {ExamIntroduction(model,s);return}
    if(s.examReviewing) {ExamPartReview(model,s,timer);return}
    val q=s.current ?: return
    val key="${s.attemptId}-${q.id}-${s.index}"
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val density=LocalDensity.current
    val imeVisible=WindowInsets.ime.getBottom(density)>0
    val horizontalTools=questionToolsHorizontal()
    val spokenBottomMargin=spokenControlsBottomMargin()
    val spokenHeight=spokenControlsHeight()
    val scroll=rememberScrollState()
    val answerVisibility=remember(key){BringIntoViewRequester()}
    var answerFocused by remember(key){mutableStateOf(false)}
    var answerSize by remember(key){mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)}
    var choice by rememberSaveable(key){mutableIntStateOf(s.responses[s.index]?.choiceIndex ?: -1)}
    val signedNumber=q.kind=="number"&&(q.value ?: 0.0)<0
    val previousNumeric=s.responses[s.index]?.display?.removeSuffix(" "+q.unit)?.trim().orEmpty()
    var numeric by rememberSaveable(key){mutableStateOf(if(signedNumber)previousNumeric.trimStart('-','−','+') else previousNumeric)}
    var numericNegative by rememberSaveable(key){mutableStateOf(signedNumber&&(previousNumeric.startsWith("-")||previousNumeric.startsWith("−")))}
    val numericAnswer=if(signedNumber&&numericNegative&&numeric.isNotBlank())"-$numeric" else numeric
    fun updateNumeric(text:String) {
        if(!signedNumber){numeric=text;return}
        val trimmed=text.trim()
        if(trimmed.startsWith("-")||trimmed.startsWith("−"))numericNegative=true
        else if(trimmed.startsWith("+"))numericNegative=false
        numeric=trimmed.trimStart('-','−','+')
    }
    var written by rememberSaveable(key){mutableStateOf(s.responses[s.index]?.display.orEmpty())}
    val writtenState=if(q.kind in setOf("text","spoken"))rememberWrittenAnswerState(key,q,{written=it},s.feedback==null) else null
    var placements by rememberSaveable(key){mutableStateOf(emptyMap<Int,String>())}
    var rowAnswers by rememberSaveable(key){mutableStateOf(emptyMap<String,String>())}
    var ordered by rememberSaveable(key){mutableStateOf(initialQuestionOrder(q))}
    var matches by rememberSaveable(key){mutableStateOf(emptyMap<Int,Int>())}
    var flipped by rememberSaveable(key){mutableStateOf(false)}
    var revealed by rememberSaveable(key){mutableStateOf(false)}
    var frequency by rememberSaveable(key){mutableFloatStateOf(144f)}
    var selectedMany by rememberSaveable(key){mutableStateOf(emptySet<Int>())}
    var morse by rememberSaveable(key){mutableStateOf("")}
    var binary by rememberSaveable(key){mutableIntStateOf(0)}
    val estimateMin=q.bands.getOrNull(0)?.toFloatOrNull() ?: 0f
    val estimateMax=(q.bands.getOrNull(1)?.toFloatOrNull() ?: 100f).coerceAtLeast(estimateMin+1f)
    var estimate by rememberSaveable(key){mutableFloatStateOf(estimateMin)}
    var quit by remember{mutableStateOf(false)}
    var enlarged by remember{mutableStateOf(false)}
    var calculatorOpen by rememberSaveable(key) {mutableStateOf(false)}
    var calculatorAnchor by remember {mutableStateOf<Rect?>(null)}
    var scratchpadOpen by rememberSaveable(key) {mutableStateOf(false)}
    var scratchpadAnchor by remember {mutableStateOf<Rect?>(null)}
    val scratchpad = remember(s,key) { s.scratchpadFor(key) }
    val view=LocalView.current
    val feedback=s.feedback
    val receiver=rememberFrequencyReceiver(q.kind=="frequency" && feedback==null && !s.exam && !LocalDiagnosticsCovered.current,key,q.value ?: 0.0)
    LaunchedEffect(receiver,key){receiver?.tune(frequency)}
    var shownQuestion by rememberSaveable {mutableStateOf<String?>(null)}
    var shownFeedback by rememberSaveable(key){mutableStateOf(false)}
    LaunchedEffect(key){if(shownQuestion!=key){shownQuestion=key;scroll.scrollTo(0);focus.clearFocus()}}
    LaunchedEffect(key,feedback) {
        if(feedback!=null&&!shownFeedback) {
            shownFeedback=true
            // Once answer widgets have laid out their feedback, make the explanation visible.
            delay(120)
            if(q.kind in setOf("match","order","sort"))scroll.scrollTo(0) else scroll.animateScrollTo(scroll.maxValue)
        }
    }
    DisposableEffect(key){onDispose{stopMorse()}}
    val artwork=rememberExamArtwork(q.image)
    val clozeDrag=rememberClozeDragState(key)
    val canAnswer=when(q.kind) {
        "number"->numeric.isNotBlank();"text","spoken"->written.isNotBlank()&&writtenState?.recognition?.active!=true;"circuit"->placements.size==q.pairs.size
        "rows"->RowAnswers.complete(q.rows,rowAnswers)
        "order","sort"->ordered.size==q.choices.size
        "match"->matches.size==q.pairs.size;"frequency","binary","estimate"->true
        "multiselect"->selectedMany.isNotEmpty();"morseEncode"->morse.isNotBlank();else->choice>=0
    }
    fun check()=when(q.kind) {
        "number"->LearningRules.numericCorrect(numericAnswer,q.value ?: 0.0,q.tolerance)
        "text","spoken"->WrittenAnswers.correct(written,q.acceptedAnswers)
        "rows"->RowAnswers.correct(q.rows,rowAnswers)
        "circuit"->q.pairs.indices.all {WrittenAnswers.correct(placements[it].orEmpty(),listOf(q.pairs[it].right))}
        "frequency"->LearningRules.sliderCorrect(frequency,q.value ?: 0.0,q.tolerance,.05)
        "estimate"->kotlin.math.abs(estimate-(q.value ?: 0.0)) <= q.tolerance
        "binary"->binary==(q.value ?: 0.0).toInt()
        "multiselect"->selectedMany==q.bands.mapNotNull{it.toIntOrNull()}.toSet()
        "morseEncode"->normalizeMorse(morse)==normalizeMorse(q.bands.firstOrNull().orEmpty())
        "order","sort"->ordered==q.choices.indices.toList()
        "match"->matches.all{it.key==it.value}
        else->choice==q.answer
    }
    fun responseText()=when(q.kind) {
        "number"->"$numericAnswer ${q.unit}".trim()
        "text","spoken"->written
        "rows"->q.rows.joinToString("\n") {"${it.prompt} → ${rowAnswers[it.id].orEmpty()}"}
        "circuit"->q.pairs.indices.joinToString("\n") {"${q.pairs[it].left} → ${placements[it].orEmpty()}"}
        "frequency"->"${formatMeasuredNumber(frequency.toDouble(),q.tolerance)} MHz"
        "estimate"->"${formatMeasuredNumber(estimate.toDouble(),q.tolerance)} ${q.unit}".trim()
        "binary"->binary.toString(2).padStart(q.unit.toIntOrNull() ?: 4,'0')
        "morseEncode"->morse
        "multiselect"->selectedMany.sorted().mapNotNull {q.choices.getOrNull(it)}.joinToString(" · ")
        "order","sort"->ordered.mapNotNull {q.choices.getOrNull(it)}.joinToString(" → ")
        "match"->matches.entries.sortedBy {it.key}.joinToString("\n") {"${q.pairs[it.key].left} → ${q.pairs[it.value].right}"}
        else->q.choices.getOrNull(choice).orEmpty()
    }
    fun saveDraft() {
        if(canAnswer)model.answer(check(),display=responseText(),choiceIndex=choice)
        else if(s.exam)model.answer(false,omitted=true)
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
    // Keep a full input line above the unchanged tools on compact screens with a real IME.
    val tightInput=imeVisible&&maxHeight<400.dp
    val wideTyping=maxWidth>=520.dp
    val compactTyping=imeVisible&&maxHeight<300.dp&&feedback==null&&q.kind in setOf("number","text","spoken")
    LaunchedEffect(key,imeVisible,answerFocused,answerSize,horizontalTools,spokenBottomMargin,compactTyping) {
        if(imeVisible&&answerFocused&&answerSize.height>0) {
            delay(180)
            val clearance=with(density){(if(compactTyping&&horizontalTools)0.dp else if(q.kind=="spoken"&&!compactTyping)maxOf(if(horizontalTools)96.dp else 160.dp,spokenBottomMargin+spokenHeight+12.dp) else if(horizontalTools)96.dp else 160.dp).toPx()}
            answerVisibility.bringIntoView(Rect(0f,0f,answerSize.width.toFloat(),answerSize.height+clearance))
        }
    }
    val hideKeyboard={focus.clearFocus();keyboard?.hide();Unit}
    val verifyInput={if(canAnswer){focus.clearFocus();keyboard?.hide();model.answer(check(),display=responseText(),choiceIndex=choice)}}
    Column(Modifier.fillMaxSize()) {
        if(!compactTyping) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=if(tightInput)0.dp else 8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(feedbackClick {if(model.diagnosticModel&&s.testing)model.leaveSession() else quit=true}){Icon(Icons.Rounded.Close,"Quitter la séance")}
            Column(Modifier.weight(1f)) {Text(s.title,maxLines=1,fontSize=13.sp,fontWeight=FontWeight.Bold,color=Muted);Spacer(Modifier.height(7.dp));LinearProgressIndicator(progress={(s.index.toFloat()/s.questions.size).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(8.dp),color=Teal,trackColor=Mist)}
            Spacer(Modifier.width(10.dp));Column(horizontalAlignment=Alignment.End) {
                Text("${s.index+1}/${s.questions.size}",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Teal)
                if(s.exam)Text(examDuration(timer),fontSize=12.sp,fontWeight=FontWeight.Bold,color=if(timer<60000)Coral else Muted)
            }
            QuestionFeedbackButton(q,s.index,s.questions.size,s.lessonId,s.title)
            SoundFeedbackButton()
        }
        }
        if(q.kind=="flash")BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            val preferred=maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                CompositionLocalProvider(LocalQuestionPromptMinimumHeight provides preferred) {
                    QuestionPrompt(q,key,feedback,stacked=true,flashFlipped=flipped) {Box(Modifier.padding(10.dp)) {FlippingFlashcard(q,flipped){flipped=!flipped;revealed=true}}}
                }
            }
        } else QuestionStage(Modifier.weight(1f).fillMaxWidth(),scroll,compact=imeVisible,illustrated=q.image!=null,
            answerEndPadding=if(compactTyping)if(!horizontalTools)96.dp else if(wideTyping)160.dp else 144.dp else 16.dp,
            promptAttachment=if(q.visual.startsWith("sstv:"))({SstvLearningVisual(q.visual)}) else null,
            prompt={QuestionPrompt(q,key,feedback,listening=writtenState?.recognition?.listening==true,reservePreferredHeight=!q.visual.startsWith("sstv:"),stacked=q.image!=null,spokenOrder=ordered.map(q.choices::get)) {picoOnLeft ->
                if(artwork!=null)ExamIllustration(artwork,{enlarged=true})
                else if(q.kind=="cloze")ClozeSentence(q,choice,feedback==null,feedback,clozeDrag,textAlign=if(picoOnLeft)androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End){choice=it}
                else QuestionText(q.prompt,towardsLeft=picoOnLeft)
            }},
            tools=if(q.kind!="flash"&&feedback==null)({
                FloatingActionButton(feedbackClick {focus.clearFocus();scratchpadOpen=true},Modifier.onGloballyPositioned{scratchpadAnchor=it.screenBounds(view)},containerColor=Mist,contentColor=Teal) {
                    Icon(Icons.Rounded.EditNote,"Ouvrir le brouillon",Modifier.size(28.dp))
                }
                FloatingActionButton(feedbackClick {focus.clearFocus();calculatorOpen=true},Modifier.onGloballyPositioned{calculatorAnchor=it.screenBounds(view)},containerColor=Teal,contentColor=Color.White) {
                    Icon(Icons.Rounded.Calculate,"Ouvrir la calculatrice",Modifier.size(28.dp))
                }
            })else null,
            bottomControls=if(q.kind=="spoken"&&feedback==null&&writtenState!=null&&!compactTyping)({SpokenAnswerControls(writtenState,true)}) else null) {
            if(q.studyToolsHint)StudyToolsHint()
            if(q.visual.isNotBlank() && !q.visual.startsWith("sstv:") && q.kind!="flash")LogicLearningVisual(q.visual,showNames=false,showCaption=false)
            if(q.kind=="resistor")Resistor(q.bands,showLabels=false)
            when(q.kind) {
                "text","spoken" -> WrittenAnswerInput(q,written,{written=it},feedback==null,writtenState!!,
                    Modifier.bringIntoViewRequester(answerVisibility).onSizeChanged {answerSize=it}.onFocusChanged {answerFocused=it.hasFocus},
                    fieldLayout={field->QuizTypedAnswerRow(compactTyping,wideTyping,canAnswer,hideKeyboard,verifyInput){field()}})
                "circuit" -> CircuitBoard(q,placements,feedback==null,feedback){placements=it}
                "rows" -> RowAnswerBoard(q,rowAnswers,feedback==null,feedback){rowAnswers=it}
                "number" -> {
                    QuizTypedAnswerRow(compactTyping,wideTyping,canAnswer,hideKeyboard,verifyInput) {
                    Row(Modifier.fillMaxWidth().testTag("question-number-answer").bringIntoViewRequester(answerVisibility).onSizeChanged {answerSize=it}.onFocusChanged {answerFocused=it.hasFocus},
                        verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                        if(signedNumber)FilledTonalIconButton(feedbackClick {numericNegative=!numericNegative},
                            Modifier.width(52.dp).height(56.dp).testTag("question-number-sign").semantics {
                                contentDescription=if(numericNegative)"Signe moins : passer au plus" else "Signe plus : passer au moins"
                                stateDescription=if(numericNegative)"Négatif" else "Positif"
                            },enabled=feedback==null,shape=RoundedCornerShape(topStart=16.dp,bottomStart=16.dp,topEnd=4.dp,bottomEnd=4.dp)) {
                            Text(if(numericNegative)"−" else "+",fontSize=27.sp,fontWeight=FontWeight.Bold)
                        }
                        OutlinedTextField(numeric,{if(feedback==null)updateNumeric(it)},label={Text(if(q.unit.isBlank())"Ta réponse" else "Ta réponse en "+q.unit)},
                            trailingIcon=if(q.unit.isNotBlank())({Text(q.unit,Modifier.padding(end=12.dp),fontWeight=FontWeight.Bold)}) else null,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,
                            shape=if(signedNumber)RoundedCornerShape(topStart=4.dp,bottomStart=4.dp,topEnd=16.dp,bottomEnd=16.dp) else MaterialTheme.shapes.extraSmall,
                            modifier=Modifier.weight(1f).testTag("question-number-input"),enabled=feedback==null)
                    }
                    }
                    Text(toleranceLabel(q.tolerance,q.unit),fontSize=12.sp,color=Muted)
                }
                "frequency" -> {
                    Panel(color=Mist){Text("${formatMeasuredNumber(frequency.toDouble(),q.tolerance)} MHz",fontSize=38.sp,fontWeight=FontWeight.ExtraBold,color=Teal)
                        FrequencyDial(frequency)
                        HapticQuestionSlider(frequency,{frequency=it;receiver?.tune(it)},143f..148f,feedback==null,key,steps=99,quantize={snapSliderValue(it,143f,148f,.05f)})
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("143 MHz",fontSize=11.sp);Text("148 MHz",fontSize=11.sp)}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(feedbackClick {frequency=snapSliderValue(frequency-.05f,143f,148f,.05f);receiver?.tune(frequency)},enabled=feedback==null){Text("− 0,05")};OutlinedButton(feedbackClick {frequency=snapSliderValue(frequency+.05f,143f,148f,.05f);receiver?.tune(frequency)},enabled=feedback==null){Text("+ 0,05")}}
                        Text(toleranceLabel(q.tolerance,"MHz"),fontSize=12.sp,color=Muted)
                    }
                }
                "estimate" -> {
                    Panel(color=Gold.copy(alpha=.16f)) {
                        val step=q.bands.getOrNull(2)?.toFloatOrNull()?.takeIf{it>0f} ?: 1f
                        Text("${formatMeasuredNumber(estimate.toDouble(),q.tolerance)} ${q.unit}",fontSize=32.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                        HapticQuestionSlider(estimate,{estimate=it},estimateMin..estimateMax,feedback==null,key,
                            intervals=((estimateMax-estimateMin)/step).toInt().coerceIn(10,100),quantize={snapSliderValue(it,estimateMin,estimateMax,step)})
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${formatMeasuredNumber(estimateMin.toDouble(),q.tolerance)} ${q.unit}",fontSize=11.sp);Text("${formatMeasuredNumber(estimateMax.toDouble(),q.tolerance)} ${q.unit}",fontSize=11.sp)}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(feedbackClick {estimate=(estimate-step).coerceAtLeast(estimateMin)},enabled=feedback==null){Text("− ${formatMeasuredNumber(step.toDouble(),q.tolerance)}")};OutlinedButton(feedbackClick {estimate=(estimate+step).coerceAtMost(estimateMax)},enabled=feedback==null){Text("+ ${formatMeasuredNumber(step.toDouble(),q.tolerance)}")}}
                        Text(toleranceLabel(q.tolerance,q.unit),fontSize=12.sp,color=Muted)
                    }
                }
                "binary" -> BinarySwitches(binary,q.unit.toIntOrNull()?.coerceIn(1,8) ?: 4,feedback==null){binary=it}
                "morseEncode" -> MorseComposer(morse,{morse=it.take(180)},feedback==null)
                "morseListen" -> {
                    Panel(color=Color(0xFFE9EFFB)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Hearing,null,tint=Color(0xFF547FCD),modifier=Modifier.size(30.dp))
                            Text("Signal Morse",fontSize=14.sp,fontWeight=FontWeight.Bold)
                        }
                        MorseListeningButton(q.bands.firstOrNull().orEmpty())
                        if(feedback!=null)MorseSymbols(q.bands.firstOrNull().orEmpty())
                    }
                    q.choices.forEachIndexed{i,text->AnswerTile(text,choice==i,feedback==null,feedback!=null&&i==q.answer){choice=i}}
                }
                "truefalse" -> TrueFalseBoard(q,choice,feedback){choice=it}
                "cloze" -> ClozeBoard(q,choice,feedback==null,feedback,showPrompt=false,state=clozeDrag){choice=it}
                "multiselect" -> {
                    q.choices.forEachIndexed{i,text->val correctIndices=q.bands.mapNotNull{it.toIntOrNull()}.toSet()
                        Surface(onClick=feedbackAction(FeedbackCue.SELECT) {selectedMany=if(i in selectedMany)selectedMany-i else selectedMany+i},enabled=feedback==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),
                            color=if(feedback!=null&&i in correctIndices)Mist else if(i in selectedMany)Gold.copy(alpha=.25f)else Color.White,
                            border=BorderStroke(if(i in selectedMany)2.dp else 1.dp,if(i in selectedMany)Teal else Color(0xFFD5DEDA))) {
                            Row(Modifier.padding(horizontal=12.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(if(i in selectedMany)Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,null,tint=Teal)
                                MorseAwareText(text,fontSize=15.sp,lineHeight=21.sp,color=Ink,fontWeight=if(i in selectedMany)FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                "waveform" -> q.choices.forEachIndexed{i,text->
                    Surface(onClick=feedbackAction(FeedbackCue.SELECT) {choice=i},enabled=feedback==null,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
                        color=if(feedback!=null&&i==q.answer)Mist else if(choice==i)Gold.copy(alpha=.25f)else Color.White,
                        border=BorderStroke(if(choice==i)2.dp else 1.dp,if(choice==i)Teal else Color(0xFFD5DEDA))) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {MorseAwareText(text,fontSize=14.sp,fontWeight=FontWeight.Bold,color=Ink);WaveformPreview(q.bands.getOrNull(i) ?: "sine")}
                    }
                }
                "order","sort" -> OrderBoard(q,ordered,feedback==null,feedback){ordered=it}
                "match" -> MatchBoard(q,key,matches,feedback==null,feedback){matches=it}
                else -> q.choices.forEachIndexed {i,text->
                    val good=feedback!=null && i==q.answer
                    AnswerTile(text,choice==i,feedback==null,good){choice=i}
                }
            }
            if(feedback!=null) {
                Panel(color=if(feedback)Mist else Color(0xFFFFE8E0)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(if(feedback)Icons.Rounded.CheckCircle else Icons.Rounded.Lightbulb,null,tint=if(feedback)Teal else Color(0xFFBA5546));Spacer(Modifier.width(10.dp));Text(if(feedback)"Signal reçu !" else "Une occasion de retenir",fontSize=20.sp,fontWeight=FontWeight.Bold)}
                    if(!feedback && q.kind!="morseEncode")MorseAwareText(solution(q),fontWeight=FontWeight.Bold,fontSize=16.sp,lineHeight=23.sp)
                    if(!feedback&&q.kind=="morseEncode")MorseSymbols(q.bands.firstOrNull().orEmpty())
                    if(q.explanation.isNotBlank())MorseAwareText(q.explanation,fontSize=14.sp,lineHeight=21.sp)
                }
            }
        }
        if(!compactTyping)Surface(color=Cream,modifier=Modifier.questionFooterShadow()) {
            Column(Modifier.navigationBarsPadding().padding(horizontal=20.dp,vertical=if(tightInput)2.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                if(q.kind=="flash") {
                    if(!revealed)Action("Retourner la carte"){flipped=true;revealed=true}
                    else {
                        Text("Comment était le rappel ?",fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf(2 to "À revoir",3 to "Difficile",4 to "Bien",5 to "Facile").forEach{(quality,label)->
                                val colors=listOf(Color(0xFFFADEDE),Color(0xFFFFE8C6),Color(0xFFDDEEDD),Color(0xFFDDEAFB))
                                Button(feedbackClick {model.answer(quality>=3,quality);model.next()},modifier=Modifier.weight(1f).heightIn(min=56.dp),colors=ButtonDefaults.buttonColors(containerColor=colors[quality-2],contentColor=Ink),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=4.dp,vertical=16.dp)){Text(label,fontSize=11.sp,fontWeight=FontWeight.Bold)}
                            }
                        }
                    }
                } else if(feedback==null) {
                    Action(if(s.exam)if(s.index+1<s.examPartEnd)"Enregistrer et continuer" else "Enregistrer et relire" else "Vérifier",enabled=canAnswer){focus.clearFocus();model.answer(check(),display=responseText(),choiceIndex=choice)}
                    if(s.exam) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            TextButton(feedbackClick {
                                val previous=s.index-1
                                focus.clearFocus()
                                saveDraft()
                                model.revisitExamQuestion(previous)
                            },enabled=s.index>s.examPartStart,contentPadding=PaddingValues(horizontal=4.dp)) {Icon(Icons.Rounded.ArrowBack,null,modifier=Modifier.size(16.dp));Text("Précédent",fontSize=12.sp)}
                            TextButton(feedbackClick {saveDraft();model.reviewExamPart()},contentPadding=PaddingValues(horizontal=4.dp)) {Text("Relire",fontSize=12.sp)}
                            TextButton(feedbackClick {focus.clearFocus();if(canAnswer)saveDraft() else model.answer(false,omitted=true)},contentPadding=PaddingValues(horizontal=4.dp)) {Text("Suivant",fontSize=12.sp);Icon(Icons.Rounded.ArrowForward,null,modifier=Modifier.size(16.dp))}
                        }
                    }
                } else Action("Continuer"){model.next()}
            }
        }
    }
    }
    FloatingCalculator(calculatorOpen,{calculatorOpen=false},if(q.kind=="number"&&feedback==null)({value:Double->numericNegative=value<0;numeric=CalculatorEngine.format(if(signedNumber)kotlin.math.abs(value) else value)}) else null,anchorBounds=calculatorAnchor,resetKey=s to key)
    key(s,key) { FloatingScratchpad(scratchpadOpen,{scratchpadOpen=false},scratchpad,scratchpadAnchor) }
    if(quit)AlertDialog(onDismissRequest={quit=false},title={Text("Faire une pause ?")},text={Text(if(s.testing)"Cette séance de test ne modifie pas la progression." else if(s.exam)"Les épreuves finalisées sont enregistrées. Les réponses de l’épreuve en cours seront perdues si tu quittes." else "Ton XP et tes révisions sont enregistrés. Pour valider une leçon, vise au moins 80 % dès le premier essai et corrige les erreurs restantes.")},confirmButton={TextButton(feedbackClick {model.leaveSession();quit=false}){Text("Quitter")}},dismissButton={TextButton(feedbackClick {quit=false}){Text("Revenir au défi")}})
    if(enlarged && artwork!=null)FullscreenExamIllustration(artwork.original){enlarged=false}
}

/** On a very short IME viewport the input stays beside the unchanged right-hand tool row. */
@Composable private fun QuizTypedAnswerRow(compact:Boolean,wide:Boolean,enabled:Boolean,hideKeyboard:()->Unit,verify:()->Unit,content:@Composable ()->Unit) {
    // Keep the editor at the same composition position while the keyboard resizes its host.
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=if(compact&&!wide)Alignment.Top else Alignment.CenterVertically) {
        Box(Modifier.weight(1f)){content()}
        if(compact) {
            @Composable fun Actions() {
                Button(verify,Modifier.heightIn(min=48.dp),enabled=enabled,contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {Text("Vérifier",fontSize=13.sp)}
                IconButton(hideKeyboard,Modifier.size(48.dp)){Icon(Icons.Rounded.KeyboardArrowDown,"Masquer le clavier")}
            }
            if(wide)Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){Actions()}
            else Column(horizontalAlignment=Alignment.CenterHorizontally){Actions()}
        }
    }
}

fun solution(q:Question):String=when(q.kind){
    "text","spoken"->"Réponse : ${q.acceptedAnswers.firstOrNull().orEmpty()}"
    "circuit"->q.pairs.joinToString("\n"){"${it.left} → ${it.right}"}
    "rows"->q.rows.joinToString("\n"){"${it.prompt} → ${RowAnswers.solution(it)}"}
    "number","frequency","estimate"->"Réponse : ${formatMeasuredNumber(q.value ?: 0.0,q.tolerance)} ${q.unit}"
    "binary"->"Réponse : ${(q.value ?: 0.0).toInt().toString(2).padStart(q.unit.toIntOrNull() ?: 4,'0')} en binaire = ${(q.value ?: 0.0).toInt()} en décimal"
    "morseEncode"->"Réponse : ${normalizeMorse(q.bands.firstOrNull().orEmpty()).replace(".","●").replace("-","━")}"
    "multiselect"->"Réponses : "+q.bands.mapNotNull{it.toIntOrNull()?.let(q.choices::getOrNull)}.joinToString(" · ")
    "match"->q.pairs.joinToString("\n"){"${it.left} → ${it.right}"}
    "order","sort"->q.choices.mapIndexed{i,t->"${i+1}. $t"}.joinToString("\n")
    else->"Réponse : ${q.choices.getOrNull(q.answer).orEmpty()}"
}
@Composable fun AnswerTile(text:String,selected:Boolean,enabled:Boolean,good:Boolean=false,onClick:()->Unit) {
    Surface(onClick=feedbackAction(FeedbackCue.SELECT,onClick),enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),
        color=if(good)Mist else if(selected)Color(0xFFFFE9CC) else Color.White,
        border=BorderStroke(if(selected||good)2.dp else 1.dp,if(good)Teal else if(selected)Color(0xFFE5A246) else Color(0xFFD5DEDA))) {
        FormulaAwareAnswer(text,Modifier.padding(horizontal=14.dp,vertical=12.dp).heightIn(min=24.dp),bold=selected||good)
    }
}
@Composable fun Resistor(bands:List<String>,showLabels:Boolean=true) {
    val colors=mapOf("noir" to Color.Black,"brun" to Color(0xFF865239),"marron" to Color(0xFF865239),"rouge" to Color(0xFFE54546),"orange" to Color(0xFFFF953E),"jaune" to Color(0xFFF7D345),"vert" to Color(0xFF43A77B),"bleu" to Color(0xFF4276CA),"violet" to Purple,"gris" to Color.Gray,"blanc" to Color.White,"or" to Gold,"argent" to Color.LightGray)
    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(115.dp)) {
            val w=size.width;val h=size.height
            drawLine(Color.Gray,Offset(0f,h/2),Offset(w,h/2),8f,StrokeCap.Round)
            drawRoundRect(Color(0xFFF0D3A5),Offset(w*.14f,h*.2f),Size(w*.72f,h*.6f),CornerRadius(25f))
            bands.forEachIndexed{i,color->val x=if(i==bands.size-1)w*.74f else w*(.25f+i*.12f);drawRect(colors[color.lowercase()]?:Teal,Offset(x,h*.2f),Size(w*.045f,h*.6f))}
        }
        if(showLabels)Text(bands.joinToString(" · "),fontSize=12.sp,color=Muted)
    }
}
@Composable fun FrequencyDial(frequency:Float) {
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        repeat(51){i->val x=size.width*i/50;drawLine(Teal.copy(alpha=.45f),Offset(x,if(i%10==0)5f else 25f),Offset(x,55f),if(i%10==0)3f else 1f)}
        val x=(frequency-143)/5*size.width
        drawLine(Coral,Offset(x,0f),Offset(x,size.height),5f,StrokeCap.Round)
    }
}
@Composable fun ResultsScreen(model:AppModel,s:Session) {
    val context=LocalContext.current
    val passed=if(s.exam)LearningRules.examPassed(s.regulationScore,s.techniqueScore) else if(s.lessonId!=null)s.lessonPassed else s.unresolved.isEmpty()
    val showRecap=s.exam || (s.lessonId==null && s.questions.any {it.kind!="flash"})
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("results-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        item {
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Pico(Modifier.size(108.dp),happy=passed,mood=if(passed)MascotMood.CELEBRATE else MascotMood.DETERMINED,pose=if(passed)MascotPose.DANCE else MascotPose.HUG)
                BigTitle(if(passed)if(s.exam)"Prêt pour le grand contact !" else "Bien joué, Hamigo !" else "Ton signal progresse",if(s.exam)"Examen blanc terminé · ${examDuration(s.elapsedMillis)}" else "Chaque rappel renforce ta mémoire.")
            }
        }
        item {
            Surface(color=if(passed)Mist else Color(0xFFFFE8E0),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(s.exam) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            listOf("Réglementation" to s.regulationScore,"Technique" to s.techniqueScore).forEach {entry ->
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(entry.first,fontSize=13.sp,fontWeight=FontWeight.Bold,color=Ink)
                                    Text("${entry.second} / 20",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=if(entry.second>=10)Teal else Color(0xFFBA5546))
                                    LinearProgressIndicator(progress={entry.second/20f},modifier=Modifier.fillMaxWidth().height(6.dp),color=if(entry.second>=10)Teal else Coral,trackColor=Color.White)
                                }
                            }
                        }
                        Text(if(passed)"Les deux seuils de 10/20 sont atteints." else "Il faut 10/20 dans chacune des deux parties.",fontSize=13.sp,lineHeight=18.sp,color=Muted)
                        Text("+ ${s.gain} XP · ${s.unanswered} sans réponse",fontSize=12.sp,color=Muted)
                    } else {
                        Text("+ ${s.gain} XP",fontSize=35.sp,fontWeight=FontWeight.ExtraBold,color=Teal)
                        Text("${s.correct} réponses réussies sur ${s.questions.size} essais · ${dayCount(model.progress.streak)} de série",fontSize=13.sp,color=Muted)
                    }
                    if(!s.exam&&s.lessonId!=null) {
                        Text("${s.firstCorrect}/${s.firstCount} réponses justes au premier essai",fontWeight=FontWeight.Bold)
                        LinearProgressIndicator(progress={s.firstCorrect.toFloat()/s.firstCount.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().height(8.dp),color=if(passed)Teal else Coral,trackColor=Color.White)
                        Text(if(passed)"Leçon validée !" else "Il faut au moins 80 % au premier essai et aucune erreur restante. Cette leçon reste à consolider.",fontSize=13.sp,lineHeight=19.sp,color=Muted)
                    }
                }
            }
        }
        item(key="results-goal") {val p=model.displayedProgress ?: model.progress;DailyGoalSummary(p.todayXp,p.dailyGoal)}
        item {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Action(when(s.returnRoute) { "practice" -> "Revenir aux défis"; "exam-history"->"Revenir à l’historique"; "resources" -> "Revenir aux mémos"; else -> "Revenir au parcours" },Modifier.hamigoTourTarget("tour-return-path")){model.leaveSession()}
                if(!s.exam&&s.lessonId!=null&&!passed)model.content?.lessons?.firstOrNull{it.id==s.lessonId}?.let{lesson->Action("Reprendre le cours"){model.startLesson(lesson)}}
                if(s.missed.isNotEmpty())OutlinedButton(feedbackClick {model.startQuestions("On consolide le signal",s.missed.values.toList())},Modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=9.dp,horizontal=12.dp)){Text("Revoir les ${s.missed.size} questions manquées")}
                OutlinedButton(feedbackClick {
                    NativeShare.resultsImage(context,ShareResults(model.progress.name,s.title,s.firstCorrect,s.firstCount,s.elapsedMillis,s.unanswered,s.gain,
                        if(s.exam)s.regulationScore else null,if(s.exam)s.techniqueScore else null),model.sync.accountIdentity)
                },Modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=9.dp,horizontal=12.dp)) {
                    Icon(Icons.Rounded.Share,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Partager mes résultats")
                }
            }
        }
        if(showRecap) {
            item {
                Column(Modifier.fillMaxWidth().padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    HorizontalDivider(color=Teal.copy(alpha=.25f))
                    Text("Récap ⬇️",fontSize=23.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                    Text("Tes réponses et les solutions, question par question.",fontSize=13.sp,color=Muted)
                }
            }
            itemsIndexed(s.questions.take(s.firstCount)) {index,q ->ResultQuestionReview(q,s.responses[index],index+1)}
        }
    }
    SessionCelebration(s,passed)
    }
}

@Composable private fun ResultQuestionReview(q:Question,response:SessionResponse?,number:Int) {
    val context=LocalContext.current
    val artwork=rememberExamArtwork(q.image)
    var enlarged by remember {mutableStateOf(false)}
    Surface(color=Color.White,shape=RoundedCornerShape(17.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Eyebrow("QUESTION $number",if(response?.correct==true)Teal else Coral)
                Spacer(Modifier.weight(1f))
                Icon(if(response?.correct==true)Icons.Rounded.CheckCircle else Icons.Rounded.Lightbulb,null,tint=if(response?.correct==true)Teal else Coral,modifier=Modifier.size(20.dp))
            }
            if(q.image==null)MorseAwareText(q.prompt,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=21.sp)
            if(q.visual.isNotBlank()) LogicLearningVisual(q.visual, showNames=false, showCaption=false)
            if(artwork!=null)ExamIllustration(artwork,{enlarged=true})
            val answer=when {
                response==null->"Réponse non enregistrée"
                response.omitted->"Sans réponse"
                response.display.isNotBlank()->response.display
                response.choiceIndex>=0->q.choices.getOrNull(response.choiceIndex).orEmpty()
                else->if(response.correct)q.choices.getOrNull(q.answer).orEmpty() else "Réponse non enregistrée"
            }
            Text("Ta réponse",fontSize=11.sp,fontWeight=FontWeight.Bold,color=Muted)
            MorseAwareText(answer,fontSize=14.sp,color=if(response?.correct==true)Teal else Color(0xFFBA5546),fontWeight=FontWeight.Bold,lineHeight=20.sp)
            if(q.kind=="morseEncode") {
                Text("Bonne réponse",fontSize=11.sp,fontWeight=FontWeight.Bold,color=Muted)
                MorseVisual(q.bands.firstOrNull().orEmpty(),compact=true)
            } else MorseAwareText(solution(q),color=Teal,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
            if(q.explanation.isNotBlank())MorseAwareText(q.explanation,fontSize=13.sp,lineHeight=18.sp,color=Muted)
        }
    }
    if(enlarged && artwork!=null)FullscreenExamIllustration(artwork.original){enlarged=false}
}
