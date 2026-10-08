package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
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
    const val FORM_URL = "https://alexandre-malfreyt.notion.site/3f1dbe8ec53680e18e5bd7682e0661c0"
    const val EXAM1_CONTACT_PAGE = "https://f6kgl-f5kff.fr/exam1/"

    private fun questionMetadata(q: Question, index: Int, count: Int, lessonId: String?, sessionTitle: String?) = linkedMapOf(
        "content" to "question", "question_id" to q.id, "question_number" to (index + 1).toString(),
        "question_count" to count.toString(), "question_type" to q.kind, "topic" to q.topic,
        "section" to q.section, "source" to q.source, "lesson_id" to lessonId.orEmpty(),
        "image" to q.image.orEmpty(), "visual" to q.visual,
        "title" to q.prompt, "session_title" to sessionTitle.orEmpty()
    )

    fun question(q: Question, index: Int, count: Int, lessonId: String?, sessionTitle: String? = null): String =
        url(questionMetadata(q, index, count, lessonId, sessionTitle))

    fun memo(cat: RefCategory): String = url(linkedMapOf(
        "content" to "memo", "memo_id" to cat.id, "title" to cat.title, "source" to cat.source
    ))

    private fun url(metadata: Map<String, String>, base: String = FORM_URL): String = Uri.parse(base).buildUpon().apply {
        (metadata + linkedMapOf("app_version" to BuildConfig.VERSION_NAME,
            "app_version_code" to BuildConfig.VERSION_CODE.toString(), "android_api" to Build.VERSION.SDK_INT.toString(),
            "device_model" to Build.MODEL, "locale" to Locale.getDefault().toLanguageTag()))
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
    var explainSource by remember(q.id, index) { mutableStateOf(false) }
    IconButton({
        if (ContentFeedback.isExam1(q)) explainSource = true
        else openLink(context, ContentFeedback.question(q, index, count, lessonId, sessionTitle))
    }, Modifier.testTag("question-feedback")) {
        Icon(Icons.Rounded.Flag, "Signaler un problème sur cette question", tint = Muted)
    }
    if (explainSource) AlertDialog(
        onDismissRequest = { explainSource = false },
        title = { Text("Question Exam1") },
        text = { Text("Cette question vient d’Exam1 et n’a pas été écrite pour Hamigo. Pour une erreur d’énoncé ou de réponse, contacte Jean-Luc F6GPX, responsable de la banque.\n\nPour un problème d’affichage ou d’interaction dans l’app, fais un retour à Hamigo.",Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button({ explainSource = false; ContentFeedback.openExam1Contact(context, q,index,count,lessonId,sessionTitle) }, Modifier.fillMaxWidth()) { Text("Contacter Exam1") }
                TextButton({ explainSource = false; openLink(context, ContentFeedback.question(q, index, count, lessonId,sessionTitle)) }, Modifier.fillMaxWidth()) { Text("Signaler à Hamigo") }
                TextButton({ explainSource = false }, Modifier.fillMaxWidth()) { Text("Annuler") }
            }
        }
    )
}

@Composable
fun MemoFeedbackButton(cat: RefCategory) {
    val context = LocalContext.current
    TextButton({ openLink(context, ContentFeedback.memo(cat)) },
        Modifier.fillMaxWidth().testTag("memo-feedback")) {
        Icon(Icons.Rounded.Flag, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Signaler un problème")
    }
}
