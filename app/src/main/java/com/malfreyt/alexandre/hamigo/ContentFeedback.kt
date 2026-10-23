package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Only content and environment metadata; no answers, account or backup identifiers. */
object ContentFeedback {
    const val FORM_URL = "https://tally.so/r/9NqlpK"
    const val EXAM1_CONTACT_PAGE = "https://f6kgl-f5kff.fr/exam1/"

    internal fun formUrl(includeDiagnostics: Boolean, diagnosticUrl: () -> String): String =
        if (includeDiagnostics) diagnosticUrl() else FORM_URL

    private fun questionMetadata(q: Question, index: Int, count: Int, lessonId: String?, sessionTitle: String?) = linkedMapOf(
        "content" to "question", "question_id" to q.id, "question_number" to (index + 1).toString(),
        "question_count" to count.toString(), "question_type" to q.kind, "topic" to q.topic,
        "section" to q.section, "source" to q.source, "lesson_id" to lessonId.orEmpty(),
        "image" to q.image.orEmpty(), "visual" to q.visual,
        "question_variant" to QuestionUiVariants.ids(q).joinToString(","), "source_kind" to if(isExam1(q))"exam1" else "hamigo",
        "title" to reportTitle(q), "page" to "question", "entry_point" to "question_flag", "session_title" to sessionTitle.orEmpty()
    )

    private fun reportTitle(q:Question):String {
        val template=runCatching {org.json.JSONObject(q.template)}.getOrNull()
        val source=template?.optString("sourceText").orEmpty()
        return if(source.contains("{{name}}")||template?.optString("type")?.endsWith("name")==true)
            source.ifBlank {runCatching {org.json.JSONObject(q.rawJson).optString("prompt")}.getOrDefault("Question personnalisée")}
                .replace("{{name}}","une personne").replace("{{value}}","le groupe à retrouver").replace("{{call}}","un indicatif") else q.prompt
    }
    fun question(q: Question, index: Int, count: Int, lessonId: String?, sessionTitle: String? = null,context:Context?=null,viewport:Map<String,String> = emptyMap()): String =
        url(questionMetadata(q, index, count, lessonId, sessionTitle),context=context,viewport=viewport)
    fun app(context:Context?,page:String="app",viewport:Map<String,String> = emptyMap()):String=url(mapOf("content" to "app","page" to page,"entry_point" to "contact"),context=context,viewport=viewport)
    fun lesson(lesson:Lesson,context:Context?,viewport:Map<String,String> = emptyMap()):String=url(mapOf("content" to "lesson","lesson_id" to lesson.id,"title" to lesson.title,"topic" to lesson.topic,"page" to "lesson","entry_point" to "lesson_flag"),context=context,viewport=viewport)
    private fun environment(context:Context?):Map<String,String> {
        if(context==null)return emptyMap()
        val resources=context.resources;val metrics=resources.displayMetrics;val config=resources.configuration
        val bounds=if(Build.VERSION.SDK_INT>=30)runCatching {context.getSystemService(android.view.WindowManager::class.java).maximumWindowMetrics.bounds}.getOrNull() else null
        return mapOf("screen_width_px" to (bounds?.width() ?: metrics.widthPixels).toString(),"screen_height_px" to (bounds?.height() ?: metrics.heightPixels).toString(),
            "window_width_dp" to config.screenWidthDp.toString(),"window_height_dp" to config.screenHeightDp.toString(),"density_dpi" to metrics.densityDpi.toString(),
            "font_scale" to config.fontScale.toString(),"orientation" to if(config.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)"landscape" else "portrait",
            "diagnostic_mode" to (context is DiagnosticContext).toString())
    }

    fun memo(cat: RefCategory,context:Context?=null,viewport:Map<String,String> = emptyMap()): String = url(linkedMapOf(
        "content" to "memo", "memo_id" to cat.id, "title" to cat.title, "source" to cat.source,"page" to "memo","entry_point" to "memo_flag"
    ),context=context,viewport=viewport)

    private fun url(metadata: Map<String, String>, base: String = FORM_URL,context:Context?=null,viewport:Map<String,String> = emptyMap()): String = Uri.parse(base).buildUpon().apply {
        (metadata + environment(context) + viewport + linkedMapOf("app_version" to BuildConfig.VERSION_NAME,
            "app_version_code" to BuildConfig.VERSION_CODE.toString(), "android_api" to Build.VERSION.SDK_INT.toString(),
            "device_model" to Build.MODEL,"device_manufacturer" to Build.MANUFACTURER,"android_version" to Build.VERSION.RELEASE, "locale" to Locale.getDefault().toLanguageTag()))
            .filterValues { it.isNotBlank() }.forEach { (key, value) -> appendQueryParameter(key, value) }
    }.build().toString()

    fun isExam1(q: Question): Boolean = Uri.parse(q.source).let {
        it.scheme in listOf("http", "https") && it.host.equals("exam1.r-e-f.org", ignoreCase = true)
    }

    fun exam1Contact(q: Question, index: Int, count: Int, lessonId: String?, sessionTitle: String?): Uri = Uri.parse("mailto:jfortin@club.fr?subject=" +
        Uri.encode("Exam1 — question ${q.id}") + "&body=" +
        Uri.encode("Bonjour,\n\nJe souhaite signaler un problème dans la question ${q.id}.\nSource : ${q.source}\nHamigo : ${BuildConfig.VERSION_NAME}\nContexte : ${question(q,index,count,lessonId,sessionTitle)}\n\nDescription du problème :\n"))

    fun openExam1Contact(context: Context, q: Question, index: Int, count: Int, lessonId: String?, sessionTitle: String?) {
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, exam1Contact(q,index,count,lessonId,sessionTitle))) }
            .onFailure { openLink(context, url(questionMetadata(q,index,count,lessonId,sessionTitle),EXAM1_CONTACT_PAGE)) }
    }
}

@Composable
fun QuestionFeedbackButton(q: Question, index: Int, count: Int, lessonId: String?, sessionTitle: String? = null) {
    val context = LocalContext.current
    val viewport=rememberFeedbackViewport()
    val openFeedback=rememberFeedbackLauncher {ContentFeedback.question(q,index,count,lessonId,sessionTitle,context,viewport)}
    IconButton(feedbackClick(openFeedback),Modifier.testTag("question-feedback")) {
        Icon(Icons.Rounded.Flag,"Signaler un problème sur cette question",tint=Muted)
    }
}

@Composable
fun MemoFeedbackButton(cat: RefCategory) {
    val context = LocalContext.current
    val viewport=rememberFeedbackViewport()
    val openFeedback=rememberFeedbackLauncher {ContentFeedback.memo(cat,context,viewport)}
    TextButton(feedbackClick(openFeedback),
        Modifier.fillMaxWidth().testTag("memo-feedback")) {
        Icon(Icons.Rounded.Flag, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Signaler un problème")
    }
}

@Composable internal fun LessonFeedbackButton(lesson:Lesson,enabled:Boolean=true) {
    val context=LocalContext.current
    val viewport=rememberFeedbackViewport()
    val openFeedback=rememberFeedbackLauncher {ContentFeedback.lesson(lesson,context,viewport)}
    IconButton(feedbackClick(openFeedback),enabled=enabled,modifier=Modifier.testTag("lesson-feedback")) {
        Icon(Icons.Rounded.Flag,"Signaler un problème sur cette introduction",tint=Muted)
    }
}

/** Every report entry point asks before any diagnostic data reaches the browser. */
@Composable
internal fun rememberFeedbackLauncher(diagnosticUrl: () -> String): () -> Unit {
    val context=LocalContext.current
    val feedback=LocalAppFeedback.current
    var opened by rememberSaveable {mutableStateOf(false)}
    var includeDiagnostics by rememberSaveable {mutableStateOf(true)}
    if(opened) HamigoSheet(title="Faire un retour",onDismiss={opened=false},titleIcon=Icons.Rounded.Flag,
        modifier=Modifier.testTag("feedback-diagnostics-dialog"),actions={
            TextButton(feedbackClick {opened=false}){Text("Annuler")}
            Button(feedbackClick {
                opened=false
                openLink(context,ContentFeedback.formUrl(includeDiagnostics,diagnosticUrl))
            },Modifier.testTag("feedback-open-form")){Text("Ouvrir le formulaire")}
        }) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=includeDiagnostics,role=Role.Checkbox,onValueChange={
                feedback?.click();includeDiagnostics=it
            }).testTag("feedback-include-diagnostics").padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=includeDiagnostics,onCheckedChange=null)
                Spacer(Modifier.width(10.dp))
                Text("Joindre les données de diagnostic")
            }
            Text(if(includeDiagnostics)
                "Version de Hamigo et d’Android, appareil, affichage et contenu concerné. Les valeurs seront consultables en bas du formulaire."
                else "Le formulaire s’ouvrira sans données de diagnostic ni contexte prérempli.")
        }
    return {includeDiagnostics=true;opened=true}
}

/** Compose supplies the actual window even when LocalContext is an application-only diagnostic wrapper. */
@Composable internal fun rememberFeedbackViewport():Map<String,String> {
    val window=androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val density=androidx.compose.ui.platform.LocalDensity.current
    val configuration=androidx.compose.ui.platform.LocalConfiguration.current
    return mapOf("window_width_dp" to (window.width/density.density).toInt().toString(),"window_height_dp" to (window.height/density.density).toInt().toString(),
        "orientation" to if(window.width>window.height)"landscape" else "portrait","font_scale" to configuration.fontScale.toString(),"diagnostic_mode" to (LocalContext.current is DiagnosticContext).toString())
}
