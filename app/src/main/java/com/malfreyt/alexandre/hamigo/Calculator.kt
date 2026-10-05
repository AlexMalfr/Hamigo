package com.malfreyt.alexandre.hamigo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A floating calculator that leaves the question and its current answer intact beneath it. */
@Composable
fun FloatingCalculator(isOpen: Boolean, onDismiss: () -> Unit, onInsertResult: ((Double) -> Unit)? = null) {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<Double?>(null) }
    var previousAnswer by rememberSaveable { mutableDoubleStateOf(0.0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
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
    fun change(value: String) { expression = value.take(512); result = null; error = null }
    fun press(label: String) {
        when (label) {
            "C" -> change("")
            "⌫" -> change(expression.dropLast(1))
            "=" -> calculate()
            "x²" -> change("(${expression.ifBlank { "Ans" }})^2")
            "1/x" -> change("1/(${expression.ifBlank { "Ans" }})")
            "√" -> change(expression + "sqrt(")
            "10ˣ" -> change(expression + "10^(")
            "eˣ" -> change(expression + "exp(")
            "EXP" -> change(expression + "e")
            "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln" -> change(expression + "$label(")
            else -> change(expression + label)
        }
    }
    if (!rendered) return
    Dialog(onDismissRequest = ::requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(horizontal = 14.dp).widthIn(max = 430.dp).fillMaxWidth().graphicsLayer {
            val fraction = visibility.value
            alpha = fraction
            scaleX = .94f + .06f * fraction
            scaleY = .94f + .06f * fraction
            translationY = 16.dp.toPx() * (1f - fraction)
        }.testTag("calculator-surface"), shape = RoundedCornerShape(24.dp), color = Cream, shadowElevation = 8.dp) {
            Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Calculatrice", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Ink)
                    TextButton({ degrees = !degrees; result = null; error = null }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (degrees) "DEG" else "RAD", fontWeight = FontWeight.Bold)
                    }
                    IconButton(::requestDismiss, modifier = Modifier.size(40.dp), enabled = isOpen && !dismissalRequested) { Icon(Icons.Rounded.Close, "Fermer la calculatrice") }
                }
                OutlinedTextField(expression, { change(it) }, modifier = Modifier.fillMaxWidth().testTag("calculator-expression"), label = { Text("Calcul") },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { calculate() }))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(result?.let(CalculatorEngine::format) ?: "=", Modifier.weight(1f).testTag("calculator-result"), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Teal)
                    TextButton({ change(expression + "Ans") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Ans") }
                    TextButton({ change(expression + "%") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("%") }
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
                            Surface(onClick = { press(label) }, modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(10.dp), color = if (accent) Teal else if (rowIndex < 3) Mist else Color.White,
                                tonalElevation = if (accent) 0.dp else 1.dp) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (label == "⌫") Icon(Icons.Rounded.Backspace, "Effacer le dernier caractère", tint = Ink, modifier = Modifier.size(19.dp))
                                    else Text(label, fontSize = if (label.length > 3) 12.sp else 16.sp, color = if (accent) Color.White else Ink, fontWeight = FontWeight.Bold)
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
        }
    }
}
