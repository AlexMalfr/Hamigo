package com.malfreyt.alexandre.hamigo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val callsignPlaces = mapOf(
    "F" to "France métropolitaine", "TK" to "Corse", "FG" to "Guadeloupe", "FM" to "Martinique",
    "FR" to "La Réunion", "FH" to "Mayotte", "FK" to "Nouvelle-Calédonie", "FO" to "Polynésie française",
    "FW" to "Wallis-et-Futuna", "FP" to "Saint-Pierre-et-Miquelon", "FS" to "Saint-Martin, Antilles",
    "FJ" to "Saint-Barthélemy", "FY" to "Guyane française", "FT" to "Terres australes et antarctiques françaises",
    "DL" to "Allemagne", "ON" to "Belgique", "HB" to "Suisse", "I" to "Italie", "EA" to "Espagne",
    "G · M · 2" to "Royaume-Uni", "EI" to "Irlande", "PA" to "Pays-Bas", "CT" to "Portugal",
    "LX" to "Luxembourg", "OE" to "Autriche", "LA" to "Norvège", "SM" to "Suède", "OH" to "Finlande",
    "K · N · W" to "États-Unis", "VE · VA" to "Canada", "JA" to "Japon", "VK" to "Australie",
    "ZL" to "Nouvelle-Zélande", "CN" to "Maroc", "3A" to "Monaco"
)
fun callsignMapQuery(term: String): String? = callsignPlaces[term]

@Composable fun ItuRegionsMap() {
    val context = LocalContext.current
    val bitmap = remember(context) { context.assets.open("reference/itu-regions.jpg").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }
    var expanded by remember { mutableStateOf(false) }
    Panel {
        Text("Les trois régions UIT", color = Teal)
        Image(bitmap, "Carte officielle du monde : frontières des régions UIT 1, 2 et 3", Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).clickable { expanded = true }, contentScale = ContentScale.Fit)
        Text("La France métropolitaine est en région 1. Les limites suivent des lignes définies par le Règlement des radiocommunications : elles ne sont pas de simples méridiens.", fontSize = 12.sp, color = Muted)
        Text("Touche la carte pour agrandir ; pince pour zoomer.", fontSize = 11.sp, color = Muted)
        TextButton({ openLink(context, "https://www.itu.int/ITU-R/information/docs/emergency-regions.jpg") }, contentPadding = PaddingValues(0.dp)) {
            Text("Source : UIT · régions de radiocommunication", fontSize = 11.sp)
        }
    }
    if (expanded) Dialog({ expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var x by remember { mutableFloatStateOf(0f) }
        var y by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxWidth().fillMaxHeight(.85f).pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                x = if (scale == 1f) 0f else x + pan.x
                y = if (scale == 1f) 0f else y + pan.y
            }
        }, contentAlignment = Alignment.Center) {
            Image(bitmap, "Carte agrandie des régions UIT", Modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale, translationX = x, translationY = y), contentScale = ContentScale.Fit)
        }
    }
}
