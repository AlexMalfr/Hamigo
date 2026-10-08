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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Calculate
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** A floating calculator that leaves the question and its current answer intact beneath it. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FloatingCalculator(isOpen: Boolean, onDismiss: () -> Unit, onInsertResult: ((Double) -> Unit)? = null, anchorBounds: Rect? = null, resetKey: Any? = null) {
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
    if (!rendered) return
    Dialog(onDismissRequest = ::requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val calculatorFocus=LocalFocusManager.current
        val keyboard=LocalSoftwareKeyboardController.current
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
            Column(Modifier.graphicsLayer { alpha=((visibility.value-.15f)/.45f).coerceIn(0f,1f) }.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Calculatrice", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Ink)
                    TextButton({ degrees = !degrees; result = null; error = null }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (degrees) "DEG" else "RAD", fontWeight = FontWeight.Bold)
                    }
                    IconButton(::requestDismiss, modifier = Modifier.size(40.dp), enabled = isOpen && !dismissalRequested) { Icon(Icons.Rounded.Close, "Fermer la calculatrice") }
                }
                OutlinedTextField(input, { change(it) }, modifier = Modifier.fillMaxWidth().testTag("calculator-expression"), label = { Text("Calcul") },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { calculate();calculatorFocus.clearFocus();keyboard?.hide() }))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(result?.let(CalculatorEngine::format) ?: "=", Modifier.weight(1f).testTag("calculator-result"), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
                    TextButton({ keyboard?.hide(); press("Ans") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Ans") }
                    TextButton({ keyboard?.hide(); press("%") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("%") }
                }
                error?.let { Text(it, color = Coral, fontSize = 12.sp, lineHeight = 16.sp) }
                val rows = listOf(
                    listOf("sin", "cos", "tan", "(", ")"),
                    listOf("asin", "acos", "atan", "x²", "√"),
                    listOf("log", "ln", "10ˣ", "eˣ", "1/x"),
                    listOf("7", "8", "9", "÷", "⌫"),
                    listOf("4", "5", "6", "×", "C"),
                    listOf("1", "2", "3", "−", "π"),
                    listOf("0", ",", "EXP", "+", "=")
                )
                rows.forEachIndexed { rowIndex, labels ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        labels.forEach { label ->
                            val accent = label == "="
                            val erases = label in listOf("C", "⌫")
                            Surface(modifier = Modifier.weight(1f).height(42.dp).testTag("calculator-key-$label").clip(RoundedCornerShape(10.dp)).combinedClickable(role=Role.Button,
                                onClick = { keyboard?.hide();press(label) },
                                onLongClick = if(label=="⌫") ({ keyboard?.hide();press("C") }) else null,
                                onLongClickLabel = if(label=="⌫") "Tout effacer" else null),
                                shape = RoundedCornerShape(10.dp), color = if (accent) Teal else if (erases) Color(0xFFFFDFE5) else if (rowIndex < 3) Mist else Color.White,
                                tonalElevation = if (accent) 0.dp else 1.dp) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (label == "⌫") Icon(Icons.Rounded.Backspace, "Effacer le dernier caractère", tint = Color(0xFF963E54), modifier = Modifier.size(19.dp))
                                    else Text(label, fontSize = if (label.length > 3) 12.sp else 16.sp, color = if (accent) Color.White else if(erases) Color(0xFF963E54) else Ink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Text("Angles en ${if (degrees) "degrés" else "radians"} · log = base 10 · EXP = ×10ⁿ", fontSize = 10.sp, color = Muted)
                if (onInsertResult != null) {
                    Button({
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
