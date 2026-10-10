package com.malfreyt.alexandre.hamigo.platform

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.malfreyt.alexandre.hamigo.*
import com.malfreyt.alexandre.hamigo.R

/** Standard launcher configuration, including reconfiguration of an existing widget. */
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)
        val manager = AppWidgetManager.getInstance(this)
        val kind = when (manager.getAppWidgetInfo(id)?.provider?.className) {
            StreakWidgetProvider::class.java.name -> HomeWidgetKind.STREAK
            GoalWidgetProvider::class.java.name -> HomeWidgetKind.GOAL
            WeekWidgetProvider::class.java.name -> HomeWidgetKind.WEEK
            else -> { finish(); return }
        }
        enableEdgeToEdge()
        setContent {
            HamigoTheme {
                var transparent by remember { mutableStateOf(WidgetSettings.transparent(this, id)) }
                var lightText by remember { mutableStateOf(WidgetSettings.lightText(this,id)) }
                var error by remember { mutableStateOf(false) }
                val snapshot = remember { WidgetSnapshot.read(this) }
                Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ finish() }) { Icon(Icons.Rounded.ArrowBack, "Annuler",tint=Teal) }
                        Text("Paramétrer le widget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,color=Ink)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        // Neutral backdrop makes the transparency visible without assuming the wallpaper colour.
                        Surface(shape = RoundedCornerShape(24.dp), color = if(transparent&&!lightText)androidx.compose.ui.graphics.Color(0xFFEEEAE2) else androidx.compose.ui.graphics.Color(0xFF53636D)) {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                AndroidView(factory = { FrameLayout(it) }, modifier = Modifier.size(240.dp, 180.dp), update = { parent ->
                                    parent.removeAllViews()
                                    val views = WidgetPresentation.views(this@WidgetConfigurationActivity, kind, snapshot,
                                        SizeF(240f, 180f), transparent = transparent, lightText = lightText)
                                    parent.addView(views.apply(this@WidgetConfigurationActivity, parent).also {
                                        it.findViewById<android.view.View>(R.id.widget_root).setOnClickListener(null)
                                    })
                                })
                            }
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = Mist) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Fond transparent", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Switch(transparent, { transparent = it }, Modifier.testTag("widget-transparent"))
                            }
                        }
                        if(transparent)Surface(shape=RoundedCornerShape(20.dp),color=Mist) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if(lightText)"Texte clair" else "Texte sombre",fontWeight=FontWeight.Bold)
                                    Text(if(lightText)"Pour un fond d’écran sombre" else "Pour un fond d’écran clair",color=Muted,style=MaterialTheme.typography.bodySmall)
                                }
                                Switch(lightText,{lightText=it},Modifier.testTag("widget-light-text"))
                            }
                        }
                        Text("Ce réglage s’applique uniquement à ce widget.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (error) Text("Le réglage n’a pas pu être enregistré. Réessaie.", color = MaterialTheme.colorScheme.error)
                    }
                    Button({
                        if (manager.getAppWidgetInfo(id) != null && WidgetSettings.save(this@WidgetConfigurationActivity, id, transparent, lightText)) {
                            HomeWidgets.update(this@WidgetConfigurationActivity, manager, intArrayOf(id), kind)
                            setResult(RESULT_OK, result)
                            finish()
                        } else error = true
                    }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp).testTag("widget-config-save")) {
                        Text("Appliquer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
