package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Reproducible screenshots of real Compose screens, dialogs and native share artwork.
 * Run directly with am instrument and pull external-files/visual-audit before uninstalling.
 * Fixtures are emulator-only, disable network work, and restore every touched preference.
 */
@RunWith(AndroidJUnit4::class)
class VisualAuditTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val snapshots = linkedMapOf<String, Map<String, *>>()
    private val fixtures = object : ExternalResource() {
        override fun before() {
            for (name in listOf("hamigo", "hamigo_social", "hamigo_secure")) {
                val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                snapshots[name] = prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
                check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit()
                .putBoolean("welcomed", true).putBoolean("autoSync", false)
                .putString("friends", "[]").putString("name", "Alex").commit())
            ProgressSyncScheduler.cancel(context)
        }

        override fun after() {
            stopMorse()
            ProgressSyncScheduler.cancel(context)
            for ((name, values) in snapshots) {
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                for ((key, value) in values) restoreValue(edit, key, value)
                check(edit.commit())
            }
            // Leave any previously configured reminder/sync scheduling in its initial state.
            DailyReminder.schedule(context)
            ProgressSyncScheduler.schedule(context)
        }
    }
    val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixtures).around(ui)
    private lateinit var model: AppModel
    private val folder get() = File(context.getExternalFilesDir(null), "visual-audit").apply { mkdirs() }
    private val verticalScroll = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private val today get() = LocalDate.now()

    @Before fun awaitContentAndSeedLocalProgress() {
        model = ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) { model.content != null }
        ui.runOnIdle {
            val days = JSONObject()
            (13L downTo 0L).forEach { ago -> days.put(today.minusDays(ago).toString(), listOf(12, 24, 30, 9, 42, 18, 36)[(ago % 7).toInt()]) }
            val progress = JSONObject().put("schema", 1).put("xp", 760).put("answers", 236).put("correct", 192)
                .put("dailyXp", days).put("reviews", JSONObject()).put("awarded", JSONObject())
                .put("completed", JSONArray(model.content!!.lessons.take(6).map { it.id }))
            model.progress.prefs.edit().putString("progress", progress.toString()).commit()
            model.progress.reload()
            model.showWelcome = false
            model.revision++
        }
        ui.waitForIdle()
    }

    @Test fun allMainPagesAndSocialDialogs() {
        for ((route, label) in listOf("path" to "01-parcours", "practice" to "02-defis", "resources" to "03-memo", "friends" to "04-equipe", "profile" to "05-moi", "settings" to "06-reglages")) {
            navigate(route)
            capture("$label-top")
            val lastText = when (route) {
                "path" -> "Parcours libre : tu peux explorer une leçon à tout moment. La prochaine étape conseillée reste la même pour tous."
                "practice" -> "Lancer mon mix"
                "resources" -> model.content!!.references.last().title
                "friends" -> "Actualiser l’équipe"
                "profile" -> "La mémoire aime les retrouvailles"
                else -> "Sources & version"
            }
            if (exists(lastText)) scrollTo(lastText)
            else ui.onAllNodes(verticalScroll).onFirst().performTouchInput { swipeUp() }
            capture("$label-lower")
        }

        navigate("path")
        ui.runOnIdle { model.showWelcome = true }
        capture("07-onboarding-github")
        ui.onNodeWithText("Commencer sur cet appareil").performClick()
        val first = model.content!!.lessons.first()
        ui.runOnIdle { model.startLesson(first) }
        capture("08-cours-introduction")
        ui.onAllNodes(verticalScroll).onFirst().performTouchInput { swipeUp() }
        capture("08-cours-introduction-lower")
        model.content!!.chapters.lastOrNull { "morse" in it.title.lowercase() }?.lessons?.lastOrNull()?.let { morse ->
            ui.runOnIdle { model.startLesson(morse) }
            capture("09-cours-morse")
        }

        navigate("friends")
        ui.onNodeWithText("Ajouter").performScrollTo().performClick()
        capture("10-ajout-par-lien")
        ui.onNodeWithText("Annuler").performClick()
        ui.runOnIdle { model.pendingInvite = "abcde0123456789" }
        capture("11-deeplink-invitation")
        ui.onNodeWithText("Annuler").performClick()
        val social = context.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE)
        // A local encrypted sentinel unlocks the connected UI. It is never sent to GitHub.
        model.sync.tokens.store("visual-audit-only-not-a-real-token")
        social.edit().putString("ownerLogin", "AlexMalfr")
            .putString("ownGistUrl", "https://gist.github.com/abcde0123456789")
            .putString("lastSyncedAt", "2026-10-03T18:00:00Z").commit()
        ui.runOnIdle {
            model.progress.prefs.edit().putString("friends", JSONArray(demoFriends().map { friend ->
                JSONObject().put("progress", JSONObject(friend.progress.toJson())).put("gist", friend.gist)
            }).toString()).commit()
            model.refresh()
        }
        navigate("friends")
        capture("12-equipe-connectee-top")
        ui.onNodeWithText("Inviter").performScrollTo().performClick()
        capture("13-invitation-qr-dialog")
        ui.onNodeWithText("Fermer").performClick()
        scrollTo("Le sprint des 7 jours")
        capture("14-equipe-classement")
        scrollTo("Actualiser l’équipe")
        capture("15-equipe-equipiers")
        navigate("friends")
        scrollTo("Données sauvegardées et fréquence")
        ui.onNodeWithText("Données sauvegardées et fréquence").performClick()
        capture("16-synchronisation-details")
    }

    @Test fun practiceConfigurationExpandedAndCustomCount() {
        navigate("practice")
        scrollTo("Choisir les thèmes", substring = true)
        capture("20-mix-compact")
        ui.onNodeWithText("Choisir les thèmes", substring = true).performClick()
        capture("21-mix-themes-reglementation")
        scrollTo("Technique")
        capture("22-mix-themes-technique")
        scrollTo("Nombre libre")
        ui.onNodeWithText("Nombre libre").performClick()
        capture("23-mix-nombre-libre-dialog")
        ui.onNodeWithText("Annuler").performClick()
    }

    @Test fun everyReferenceCategoryAndInteractiveTool() {
        for ((index, category) in model.content!!.references.withIndex()) {
            navigate("resources")
            ui.runOnIdle { model.resource = category }
            capture("ref-${index.toString().padStart(2, '0')}-${category.id}-top")
            if (category.rows.isNotEmpty()) {
                scrollTo(category.rows.last().term)
                capture("ref-${index.toString().padStart(2, '0')}-${category.id}-lower")
            }
        }
        val tools = listOf(
            Triple("resistors", "Les anneaux en vrai", "resistance"),
            Triple("resistors", "Résistances ensemble", "serie-parallele"),
            Triple("morse", "Le traducteur de Pico", "morse"),
            Triple("decibels", "La réglette des décibels", "decibels"),
            Triple("formulas", "Le trio U, R, I", "ohm"),
            Triple("formulas", "Une fréquence, une onde", "longueur-onde")
        )
        for ((categoryId, label, name) in tools) {
            navigate("resources")
            ui.runOnIdle { model.resource = model.content!!.references.first { it.id == categoryId } }
            // Reference rows stay visible initially; tools expand through the real user tap.
            scrollTo(label)
            ui.onNodeWithText(label).performClick()
            scrollTo(label)
            capture("outil-$name")
            val result = when (name) {
                "resistance" -> "4700 Ω ± 5 %"
                "serie-parallele" -> "R = 200 Ω"
                "ohm" -> "U = 9,4 V"
                else -> null
            }
            if (result != null) {
                scrollTo(result)
                capture("outil-$name-resultat")
            }
            if (name == "morse") {
                scrollTo("Morse → texte")
                ui.onNodeWithText("Morse → texte").performClick()
                ui.onNodeWithText("SOS").assertExists()
                capture("outil-morse-inverse")
            }
        }
    }

    @Test fun everyQuestionInteractionAndCorrection() {
        val examples = model.content!!.allQuestions.values.groupBy { it.kind }.mapValues { (_, questions) ->
            questions.firstOrNull { it.image == null } ?: questions.first()
        }.toMutableMap()
        examples["sort"] = examples.getValue("order").copy(id = "visual-sort", kind = "sort")
        for ((kind, question) in examples.toSortedMap()) {
            ui.runOnIdle { model.startQuestions("Les défis de Pico", listOf(question)) }
            capture("question-$kind-01-instruction")
            if (kind == "flash") {
                ui.onNodeWithText("Retourner la carte").performClick()
                capture("question-$kind-02-retournee")
            } else {
                ui.runOnIdle { model.answer(false) }
                capture("question-$kind-02-expression")
                ui.onNodeWithText("Une occasion de retenir").performScrollTo().assertIsDisplayed()
                capture("question-$kind-03-correction")
            }
        }
        model.content!!.activeExam.firstOrNull { it.image != null }?.let { illustrated ->
            ui.runOnIdle { model.startQuestions("Exam1 illustré", listOf(illustrated), exam = true) }
            capture("question-exam1-illustration")
        }
    }

    @Test fun resultsCommunicatePassAndFailure() {
        val lesson = model.content!!.lessons.first()
        val questions = lesson.questions.take(8).toMutableList()
        val failed = Session(lesson.title, questions, lesson.id).apply {
            index = questions.size; firstCorrect = 1; correct = questions.size; gain = 12
        }
        ui.runOnIdle { model.session = failed; model.revision++ }
        ui.onNodeWithText("Ton signal progresse").assertIsDisplayed()
        ui.onNodeWithText("Reprendre le cours").performScrollTo().assertIsDisplayed()
        capture("resultat-lecon-a-consolider")
        val passed = Session(lesson.title, questions.toMutableList(), lesson.id).apply {
            index = questions.size; firstCorrect = questions.size; correct = questions.size; gain = 30
        }
        ui.runOnIdle { model.session = passed; model.revision++ }
        ui.onNodeWithText("Leçon validée !").assertIsDisplayed()
        capture("resultat-lecon-validee")
        val exam = Session("Examen blanc", MutableList(40) { questions[it % questions.size] }, exam = true).apply {
            index = 40; regulationScore = 9; techniqueScore = 17; unanswered = 2
        }
        ui.runOnIdle { model.session = exam; model.revision++ }
        capture("resultat-examen-seuil-non-atteint")
    }

    @Test fun sharePostersQrAndEveryReminderContext() {
        val own = model.progress.snapshot()
        NativeShare.renderProgressImage(context, own).copyTo(File(folder, "poster-personnel.png"), overwrite = true)
        NativeShare.renderTeamImage(context, own, demoFriends().map { it.progress }).copyTo(File(folder, "poster-equipe.png"), overwrite = true)
        val link = FriendInvite.link("abcde0123456789")
        NativeShare.renderInviteImage(context, link).copyTo(File(folder, "poster-invitation-qr.png"), overwrite = true)
        val states = mapOf(
            "premier-pas" to ReminderState(0, 30, emptySet()),
            "serie-a-continuer" to ReminderState(0, 30, setOf(today.minusDays(1).toString(), today.minusDays(2).toString())),
            "serie-a-reprendre" to ReminderState(0, 30, setOf(today.minusDays(3).toString())),
            "objectif-en-cours" to ReminderState(12, 30, setOf(today.toString())),
            "objectif-atteint" to ReminderState(36, 30, setOf(today.toString()))
        )
        for ((name, state) in states) {
            val artwork = ReminderArtwork.render(ReminderContent.build(state, today))
            File(folder, "notification-$name.png").outputStream().use { check(artwork.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            artwork.recycle()
        }
        val sheet = Bitmap.createBitmap(1440, 1260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(250, 248, 242))
        val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(23, 63, 66); textSize = 17f; textAlign = Paint.Align.CENTER }
        MascotPose.entries.forEachIndexed { row, pose ->
            MascotMood.entries.forEachIndexed { column, mood ->
                val left = column * 180f
                val top = row * 210f
                PicoRenderer.draw(canvas, RectF(left + 8f, top + 6f, left + 172f, top + 170f), mood, pose, .3f)
                canvas.drawText(mood.name, left + 90f, top + 185f, labels)
                canvas.drawText(pose.name, left + 90f, top + 206f, labels)
            }
        }
        File(folder, "mascotte-48-expressions-poses.png").outputStream().use { check(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        sheet.recycle()
    }

    private fun navigate(route: String) {
        ui.runOnIdle { model.session = null; model.lesson = null; model.resource = null; model.route = route; model.revision++ }
        ui.waitForIdle()
        SystemClock.sleep(250)
    }

    private fun demoFriends(): List<Friend> = listOf("Camille" to 165, "F4Léo" to 102, "Nora" to 54).mapIndexed { index, (name, weekly) ->
        Friend(ShareProgress(name, 960 - index * 230, 12 - index * 3, 18 - index * 4, weekly,
            dailyXp = (13L downTo 0L).map { ago -> DailyPoint(today.minusDays(ago).toString(), (weekly / 7 + (ago % 3) * 3).toInt()) }), "")
    }

    private fun scrollTo(text: String, substring: Boolean = false) {
        val container = ui.onAllNodes(verticalScroll).onFirst()
        if (container.fetchSemanticsNode().config.contains(SemanticsActions.ScrollToIndex)) {
            container.performScrollToIndex(0)
        }
        container.performScrollToNode(hasText(text, substring = substring))
        ui.waitForIdle()
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Compose can be idle while SurfaceFlinger still presents the preceding frame or dialog fade.
        SystemClock.sleep(500)
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }

    private fun exists(text: String) = ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun restoreValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            null -> editor.remove(key)
            else -> error("Unsupported preference type for $key")
        }
    }
}
