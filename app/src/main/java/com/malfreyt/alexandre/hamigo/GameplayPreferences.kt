package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MorseInputSettings(val singleKey: Boolean = false, val thresholdMs: Int = 300) {
    fun symbolFor(durationMs: Long): Char = if (durationMs >= thresholdMs.coerceIn(150, 600)) '-' else '.'
}

object GameplayPreferences {
    const val SINGLE_KEY = "morseSingleKey"
    const val THRESHOLD = "morseThresholdMs"
    fun read(prefs: SharedPreferences) = MorseInputSettings(
        prefs.getBoolean(SINGLE_KEY, false), prefs.getInt(THRESHOLD, 300).coerceIn(150, 600))
    fun save(prefs: SharedPreferences, settings: MorseInputSettings) = synchronized(Progress.CLOUD_LOCK) {
        prefs.edit().putBoolean(SINGLE_KEY, settings.singleKey)
            .putInt(THRESHOLD, settings.thresholdMs.coerceIn(150, 600))
            .putLong("preferencesUpdatedAt", System.currentTimeMillis()).apply()
    }
}

@Composable internal fun rememberMorseInputSettings(): MorseInputSettings {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("hamigo", Context.MODE_PRIVATE) }
    var settings by remember(prefs) { mutableStateOf(GameplayPreferences.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == GameplayPreferences.SINGLE_KEY || key == GameplayPreferences.THRESHOLD) settings = GameplayPreferences.read(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}

/** A cancelled press never transmits; TalkBack can explicitly choose either signal. */
@Composable internal fun MorseSignalInput(enabled: Boolean = true, onSignal: (Char) -> Unit) {
    val settings = rememberMorseInputSettings()
    val send by rememberUpdatedState(onSignal)
    if (!settings.singleKey) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf('.' to "Point", '-' to "Trait").forEach { (symbol, label) ->
                Button({ send(symbol) }, Modifier.weight(1f).height(52.dp), enabled = enabled) {
                    MorseVisual(symbol.toString(), compact = true, color = Color.White)
                    Spacer(Modifier.width(8.dp)); Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        var pressed by remember { mutableStateOf(false) }
        Surface(color = if (pressed) Ink else Teal, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag("morse-single-key")
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Manipulateur Morse : appui court pour un point, appui d’au moins ${settings.thresholdMs} millisecondes pour un trait"
                    if (!enabled) disabled()
                    else {
                        onClick("Saisir un point") { send('.'); true }
                        onLongClick("Saisir un trait") { send('-'); true }
                    }
                }.pointerInput(enabled, settings.thresholdMs) {
                    if (enabled) awaitEachGesture {
                        val down = awaitFirstDown(); down.consume(); pressed = true
                        try {
                            val up = waitForUpOrCancellation()
                            if (up != null) { up.consume(); send(settings.symbolFor(up.uptimeMillis - down.uptimeMillis)) }
                        } finally { pressed = false }
                    }
                }) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (pressed) "Transmission…" else "Manipulateur Morse", color = Color.White, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MorseVisual(".", compact = true, color = Color.White)
                    Text("court", fontSize = 12.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp)); MorseVisual("-", compact = true, color = Color.White)
                    Text("≥ ${settings.thresholdMs} ms", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
