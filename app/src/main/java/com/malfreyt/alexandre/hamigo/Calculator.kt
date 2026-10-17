package com.malfreyt.alexandre.hamigo

import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ArrowLeft
import androidx.compose.material.icons.rounded.ArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** A floating calculator that leaves the question and its current answer intact beneath it. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FloatingCalculator(isOpen: Boolean, onDismiss: () -> Unit, onInsertResult: ((Double) -> Unit)? = null, anchorBounds: Rect? = null, resetKey: Any? = null) {
    val compact=with(LocalDensity.current){LocalWindowInfo.current.containerSize.height.toDp()}<700.dp
    val numberHeight=if(compact)48.dp else 50.dp
    var input by rememberSaveable(resetKey, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val expression = input.text
    var result by rememberSaveable(resetKey) { mutableStateOf<Double?>(null) }
    var previousAnswer by rememberSaveable(resetKey) { mutableDoubleStateOf(0.0) }
    var error by rememberSaveable(resetKey) { mutableStateOf<String?>(null) }
    var degrees by rememberSaveable { mutableStateOf(true) }
    val visibility = remember { Animatable(0f) }
    var rendered by remember { mutableStateOf(false) }
    var dismissalRequested by remember { mutableStateOf(false) }
    val currentOpen by rememberUpdatedState(isOpen)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentInsert by rememberUpdatedState(onInsertResult)
    val openDiagnostics=LocalOpenDiagnostics.current
    LaunchedEffect(isOpen) {
        if (isOpen) {
            rendered = true
            dismissalRequested = false
        }
        val target = if (isOpen) 1f else 0f
        if (visibility.value != target) visibility.animateTo(target, tween(200, easing = FastOutSlowInEasing))
        // This effect is cancelled on a quick reopen, so an old exit cannot remove the new dialog.
        if (!isOpen) rendered = false
    }
    fun requestDismiss() {
        if (currentOpen && !dismissalRequested) {
            dismissalRequested = true
            currentDismiss()
        }
    }
    fun calculate(): Double? {
        if(openDiagnostics!=null&&DiagnosticAccess.matches(expression)) {
            requestDismiss();openDiagnostics();input=TextFieldValue("");result=null;error=null;return null
        }
        return runCatching {
            CalculatorEngine.evaluate(expression, if (degrees) CalculatorAngleMode.DEGREES else CalculatorAngleMode.RADIANS, previousAnswer)
        }.fold(onSuccess = { value -> result = value; previousAnswer = value; error = null; value }, onFailure = {
            result = null; error = it.message ?: "Vérifie le calcul."; null
        })
    }
    fun change(value: TextFieldValue) {
        if (value.text.length > 512) return
        if (value.text != input.text) { result = null; error = null }
        input = value
    }
    fun press(label: String) {
        if (label == "=") { calculate(); return }
        val edited = CalculatorEditing.press(CalculatorEdit(input.text, input.selection.start, input.selection.end), label)
        change(TextFieldValue(edited.text, TextRange(edited.start, edited.end)))
    }
    val currentInput by rememberUpdatedState(input)
    @Composable fun key(label:String,modifier:Modifier=Modifier,small:Boolean=false) {
        val erases=label in listOf("C","⌫")
        val accent=label=="="
        val math=small
        val drag=if(label!="⌫")Modifier else Modifier.pointerInput(Unit) {
            var initial=TextFieldValue("");var selected=initial;var distance=0f
            detectHorizontalDragGestures(onDragStart={initial=currentInput;selected=initial;distance=0f},onHorizontalDrag={event,amount->
                event.consume();distance+=amount
                val range=CalculatorEditing.selectBackwards(CalculatorEdit(initial.text,initial.selection.start,initial.selection.end),(-distance/20.dp.toPx()).toInt().coerceAtLeast(0))
                selected=TextFieldValue(range.text,TextRange(range.start,range.end));change(selected)
            },onDragEnd={
                if(selected.selection.collapsed)change(initial) else {
                    val edit=CalculatorEditing.press(CalculatorEdit(selected.text,selected.selection.start,selected.selection.end),"⌫")
                    change(TextFieldValue(edit.text,TextRange(edit.start,edit.end)))
                }
            },onDragCancel={change(initial)})
        }
        Surface(modifier=modifier.testTag("calculator-key-$label").clip(RoundedCornerShape(10.dp)).then(drag).combinedClickable(role=Role.Button,
            onClick=feedbackClick {if(label=="DEG"||label=="RAD"){degrees=!degrees;result=null;error=null}else press(label)},
            onLongClick=if(label=="⌫")({press("C")}) else null,onLongClickLabel=if(label=="⌫")"Tout effacer" else null),
            shape=RoundedCornerShape(10.dp),color=when {accent->Teal;erases->Color(0xFFFFDFE5);label in listOf("÷","×","−","+")->Color(0xFFF1F8F6);math->Mist;else->Color.White}) {
            Box(contentAlignment=Alignment.Center) {
                if(label=="⌫")Icon(Icons.Rounded.Backspace,"Effacer",Modifier.size(20.dp),tint=Color(0xFF963E54))
                else Text(label,fontSize=if(small)13.sp else 19.sp,fontWeight=FontWeight.Bold,color=if(accent)Color.White else if(erases)Color(0xFF963E54) else Ink)
            }
        }
    }
    if (!rendered) return
    Dialog(onDismissRequest = ::requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) { (view.parent as? DialogWindowProvider)?.window?.let { window ->
            window.setDimAmount(0f)
            // The activity theme uses a cream navigation bar. A dialog over the IME
            // must instead let our translucent backdrop reach its bottom edge.
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            @Suppress("DEPRECATION")
            window.navigationBarColor=android.graphics.Color.TRANSPARENT
            if(Build.VERSION.SDK_INT>=28) window.navigationBarDividerColor=android.graphics.Color.TRANSPARENT
            if(Build.VERSION.SDK_INT>=29) window.isNavigationBarContrastEnforced=false
        }; onDispose {} }
        var panelBounds by remember { mutableStateOf(Rect.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.32f*visibility.value)).pointerInput(Unit) { detectTapGestures { requestDismiss() } }.testTag("calculator-backdrop").imePadding().safeDrawingPadding(),contentAlignment=Alignment.Center) {
        Box(Modifier.padding(horizontal=14.dp).widthIn(max=430.dp).fillMaxWidth()
            .onGloballyPositioned { panelBounds=it.screenBounds(view) }) {
        Surface(modifier=Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures {} }.graphicsLayer {
            val fraction = visibility.value
            val target = anchorBounds ?: Rect(panelBounds.center+Offset(0f,panelBounds.height*.5f), androidx.compose.ui.geometry.Size(56.dp.toPx(),56.dp.toPx()))
            val targetScaleX=(target.width/panelBounds.width.coerceAtLeast(1f)).coerceIn(.01f,1f)
            val targetScaleY=(target.height/panelBounds.height.coerceAtLeast(1f)).coerceIn(.01f,1f)
            scaleX = targetScaleX+(1f-targetScaleX)*fraction
            scaleY = targetScaleY+(1f-targetScaleY)*fraction
            translationX = (target.center.x-panelBounds.center.x)*(1f-fraction)
            translationY = (target.center.y-panelBounds.center.y)*(1f-fraction)
            alpha = (fraction/.12f).coerceIn(0f,1f)
            shape=RoundedCornerShape((24f+80f*(1f-fraction)).dp);clip=true
        }.testTag("calculator-surface"), shape = RoundedCornerShape(24.dp), color = Cream, shadowElevation = 8.dp) {
            Box {
            // Insets belong to the scrollable content, so a shortened IME viewport
            // does not retain a fixed cream band that masks the bottom key row.
            Column(Modifier.graphicsLayer { alpha=((visibility.value-.15f)/.45f).coerceIn(0f,1f) }.verticalScroll(rememberScrollState()).padding(if(compact)12.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(if(compact)5.dp else 7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Calculatrice",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                    IconButton(::requestDismiss, modifier = Modifier.size(40.dp), enabled = isOpen && !dismissalRequested) { Icon(Icons.Rounded.Close, "Fermer la calculatrice") }
                }
                Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Muted.copy(alpha=.4f)),color=Color.White) {
                    CalculatorExpressionField(input,::change,Modifier.fillMaxWidth().height(54.dp).testTag("calculator-expression"))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(result?.let(CalculatorEngine::format) ?: "=", Modifier.weight(1f).testTag("calculator-result"), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
                    IconButton(feedbackClick {val next=CalculatorEditing.move(CalculatorEdit(input.text,input.selection.start,input.selection.end),-1);change(TextFieldValue(next.text,TextRange(next.start)))},Modifier.size(40.dp)){Icon(Icons.Rounded.ArrowLeft,"Curseur à gauche")}
                    IconButton(feedbackClick {val next=CalculatorEditing.move(CalculatorEdit(input.text,input.selection.start,input.selection.end),1);change(TextFieldValue(next.text,TextRange(next.start)))},Modifier.size(40.dp)){Icon(Icons.Rounded.ArrowRight,"Curseur à droite")}
                }
                error?.let { Text(it, color = Coral, fontSize = 12.sp, lineHeight = 16.sp) }
                val rows = listOf(
                    listOf("sin", "cos", "tan", "(", ")"),
                    listOf("asin", "acos", "atan", "x²", "√"),
                    listOf("log", "ln", "10ˣ", "eˣ", "1/x"),
                    listOf("π", "EXP", if(degrees)"DEG" else "RAD", "Ans", "%")
                )
                rows.forEachIndexed { rowIndex, labels ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        labels.forEach { label ->
                            key(label,Modifier.weight(1f).height(if(compact)32.dp else 36.dp),small=true)
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell=(maxWidth-20.dp)/5
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            listOf(listOf("7","8","9","÷"),listOf("4","5","6","×"),listOf("1","2","3","−"),listOf("0",",","+")).forEach {digits->
                                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {digits.forEach {label->key(label,if(label=="0")Modifier.width(cell*2+5.dp).height(numberHeight) else Modifier.width(cell).height(numberHeight))}}
                            }
                        }
                        Column(Modifier.width(cell),verticalArrangement=Arrangement.spacedBy(5.dp)) {key("⌫",Modifier.fillMaxWidth().height(numberHeight));key("C",Modifier.fillMaxWidth().height(numberHeight));key("=",Modifier.fillMaxWidth().height(numberHeight*2+5.dp))}
                    }
                }
                if (onInsertResult != null) {
                    Button(feedbackClick {
                        if (currentOpen && !dismissalRequested) (result ?: calculate())?.let { value ->
                            // Lock before the callback: two taps in the same frame still insert only once.
                            dismissalRequested = true
                            currentInsert?.invoke(value)
                            currentDismiss()
                        }
                    }, enabled = isOpen && !dismissalRequested && expression.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("Utiliser dans ma réponse")
                    }
                }
            }
            Box(Modifier.matchParentSize().graphicsLayer { alpha=((.4f-visibility.value)/.25f).coerceIn(0f,1f) }.background(Teal),contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.Calculate,null,Modifier.size(42.dp),tint=Color.White)
            }
            }
        }
        }
        }
    }
}
