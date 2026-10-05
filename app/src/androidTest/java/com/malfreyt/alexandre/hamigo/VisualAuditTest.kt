package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
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
                "practice" -> "Ouvrir le labo · 12 questions"
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

        navigate("profile")
        scrollTo("Cette semaine")
        ui.onNodeWithContentDescription("Voir la semaine précédente").performClick()
        ui.onNodeWithText("Ton historique").assertExists()
        capture("17-moi-historique")

        // A synthetic session shows the compact return-to-app controls, without opening a browser.
        navigate("friends")
        ui.runOnIdle {
            // The preceding ranking fixture was signed in; this capture represents a fresh login.
            model.sync.disconnect()
            model.oauthSession = DeviceOAuth.Session("visual-audit-only-device", "ABCD-EFGH",
                "https://github.com/login/device", 900, 5, SystemClock.elapsedRealtime() + 900_000)
            model.oauthStatus = "En attente de ton autorisation GitHub…"
            model.refresh()
        }
        ui.onNodeWithContentDescription("Rouvrir l’onglet GitHub").performScrollTo().assertIsDisplayed()
        capture("18-oauth-code-au-retour")
        ui.runOnIdle { model.oauthSession = null; model.oauthStatus = null }

        navigate("resources")
        scrollTo("Mémo")
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("Morse")
        capture("19-memo-recherche-compacte")
        ui.onNodeWithContentDescription("Fermer la recherche des mémos").performClick()
    }

    @Test fun practiceConfigurationExpandedAndCustomCount() {
        navigate("practice")
        scrollTo("Choisir les thèmes", substring = true)
        capture("20-mix-compact")
        ui.onNodeWithText("Choisir les thèmes", substring = true).performClick()
        capture("21-mix-themes-reglementation")
        scrollTo("Technique")
        capture("22-mix-themes-technique")
        scrollTo("Choisir les thèmes", substring = true)
        ui.onNodeWithText("Choisir les thèmes", substring = true).performClick()
        ui.onNodeWithContentDescription("Choisir un nombre personnalisé").performScrollTo().performClick()
        capture("23-mix-nombre-libre-dialog")
        ui.onNodeWithText("Annuler").performClick()
    }

    @Test fun savedGistLinksStayDiscreetAndFriendMenuShowsOnlySocial() {
        // Existing local IDs expose browser links without a token, discovery request or publication.
        val social = context.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE)
        check(social.edit()
            .putString("ownBackupId", "0123456789abcdef0123456789abcdef")
            .putString("ownGistId", "abcdef0123456789abcdef0123456789")
            .commit())
        val friend = demoFriends().first().copy(gist = "https://gist.github.com/fedcba9876543210fedcba9876543210")
        ui.runOnIdle {
            model.progress.prefs.edit().putString("friends", JSONArray().put(
                JSONObject().put("progress", JSONObject(friend.progress.toJson()))
                    .put("gist", friend.gist).put("modifiedAt", 1L)
            ).toString()).commit()
            model.refresh()
        }

        navigate("settings")
        scrollTo("Sauvegarde manuelle")
        ui.onNodeWithText("Gist de sauvegarde").assertDoesNotExist()
        ui.onNodeWithText("Gist social").assertDoesNotExist()
        ui.onNodeWithContentDescription("Afficher les options de sauvegarde").performClick()
        scrollTo("Gist social")
        ui.onNodeWithText("Gist de sauvegarde").assertIsDisplayed()
        ui.onNodeWithText("Gist social").assertIsDisplayed()
        capture("24-reglages-liens-gists")

        navigate("friends")
        ui.onAllNodes(verticalScroll).onFirst().performScrollToNode(hasContentDescription("Options de ${friend.progress.name}"))
        ui.onNodeWithText("Ouvrir le Gist social").assertDoesNotExist()
        ui.onNodeWithContentDescription("Options de ${friend.progress.name}").performClick()
        ui.onNodeWithText("Ouvrir le Gist social").assertIsDisplayed()
        ui.onNodeWithText("Retirer cet équipier").assertIsDisplayed()
        ui.onNodeWithText("Gist de sauvegarde").assertDoesNotExist()
        capture("25-equipe-menu-gist-social")
        // No link is clicked: the audit never opens a browser or sends a request to GitHub.
    }

    @Test fun githubAvatarsAndFriendRemovalRequireConfirmation() {
        // Login-only identities deliberately exercise the offline fallback without CDN requests.
        model.sync.tokens.store("visual-audit-only-not-a-real-token")
        check(context.getSharedPreferences("hamigo_social",Context.MODE_PRIVATE).edit()
            .putString("ownerLogin","AlexMalfr").remove("ownerId").commit())
        val friend=demoFriends().first().copy(gist="https://gist.github.com/fedcba9876543210fedcba9876543210",
            githubIdentity=GitHubIdentity("camille-radio"))
        ui.runOnIdle { model.addFriend(friend);model.refresh() }
        navigate("profile")
        ui.onNodeWithContentDescription("Photo GitHub de Alex").assertIsDisplayed()
        capture("26-moi-avatar-github")
        navigate("settings")
        ui.onNodeWithContentDescription("Photo GitHub de Alex").assertIsDisplayed()
        capture("27-reglages-avatar-github")
        navigate("friends")
        ui.onNodeWithContentDescription("Photo GitHub de AlexMalfr").assertIsDisplayed()
        capture("28-equipe-avatar-github")
        ui.onAllNodes(verticalScroll).onFirst().performScrollToNode(hasContentDescription("Options de Camille"))
        ui.onNodeWithContentDescription("Options de Camille").performClick()
        ui.onNodeWithText("Ouvrir le profil GitHub").assertIsDisplayed()
        capture("29-equipe-profil-menu")
        ui.onNodeWithText("Retirer cet équipier").performClick()
        ui.onNodeWithText("Retirer Camille ?").assertIsDisplayed()
        check(model.friends.size==1)
        capture("30-equipe-retrait-confirmation")
        ui.onNodeWithText("Annuler").performClick()
        check(model.friends.size==1)
        ui.onNodeWithContentDescription("Options de Camille").performClick()
        ui.onNodeWithText("Retirer cet équipier").performClick()
        ui.onNodeWithText("Retirer").performClick()
        ui.runOnIdle {
            check(model.friends.isEmpty())
            check(model.progress.friendRecords().getJSONObject(GitHubSync.gistId(friend.gist)).getBoolean("deleted"))
        }
    }

    @Test fun reciprocalRequestsShowTheirBadgeAndExplicitInvitationChoice() {
        model.sync.tokens.store("visual-audit-only-not-a-real-token")
        check(context.getSharedPreferences("hamigo_social",Context.MODE_PRIVATE).edit()
            .putString("ownerLogin","AlexMalfr").putString("ownGistId","aaaaaaaaaaaaaaaaaaaa")
            .remove("ownerId").commit())
        val own="aaaaaaaaaaaaaaaaaaaa"
        val created=java.time.Instant.now().toString()
        // Display-only fixtures: no action invokes the transport, login-only avatars stay offline.
        val requests=listOf("Camille" to "bbbbbbbbbbbbbbbbbbbb","Nora" to "cccccccccccccccccccc").mapIndexed { index,pair ->
            FriendRequest(java.util.UUID.randomUUID().toString(),pair.second,own,created,
                GitHubIdentity(if(index==0) "camille-radio" else "nora-radio"),
                ShareProgress(pair.first,30,1,1),index.toLong()+1)
        }
        navigate("friends")
        ui.runOnIdle { model.friendRequests=requests }
        ui.onNodeWithContentDescription("2 demandes d’amis en attente",useUnmergedTree=true).assertExists()
        scrollTo("Demandes reçues")
        capture("31-equipe-demandes-badge")
        ui.onAllNodesWithText("Accepter").assertCountEquals(2)
        ui.runOnIdle { model.friendRequests=emptyList() }
        ui.onNodeWithTag("friend-request-badge",useUnmergedTree=true).assertDoesNotExist()
        ui.runOnIdle { model.pendingInvite="bbbbbbbbbbbbbbbbbbbb" }
        ui.onNodeWithText("Ajouter et envoyer la demande").assertIsDisplayed()
        ui.onNodeWithText("Ajouter seulement").assertIsDisplayed()
        capture("32-invitation-ajout-reciproque")
        ui.onNodeWithText("Annuler").performClick()
    }

    @Test fun everyReferenceCategoryAndInteractiveTool() {
        for ((index, category) in model.content!!.references.withIndex()) {
            navigate("resources")
            ui.runOnIdle { model.resource = category }
            capture("ref-${index.toString().padStart(2, '0')}-${category.id}-top")
            if (category.rows.isNotEmpty()) {
                val lastTerm = category.rows.last().term
                scrollTo(if (category.id == "morse") MorseReference.characterName(lastTerm) else lastTerm)
                capture("ref-${index.toString().padStart(2, '0')}-${category.id}-lower")
            }
        }
        val tools = listOf(
            Triple("resistors", "Lire et composer une résistance", "resistance"),
            Triple("resistors", "Série et parallèle", "serie-parallele"),
            Triple("morse", "Texte ↔ Morse, avec le son", "morse"),
            Triple("decibels", "Rapport ↔ gain ou atténuation", "decibels"),
            Triple("formulas", "Deux valeurs, la troisième se révèle", "ohm"),
            Triple("formulas", "Fréquence ↔ longueur d’onde", "longueur-onde")
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
                ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertCountEquals(0)
                capture("outil-morse-inverse")
            }
        }

        navigate("resources")
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "resistors" } }
        capture("ref-resistance-guide-debutant")
        ui.onNodeWithContentDescription("Filtrer cette fiche").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("rouge")
        capture("ref-resistance-filtre-compact")
        ui.onNodeWithContentDescription("Fermer le filtre de la fiche").performClick()
        scrollTo("5 anneaux")
        ui.onNodeWithText("Code des résistances").assertIsDisplayed()
        ui.onNodeWithText("Réviser avec les flashcards").assertIsDisplayed()
        capture("ref-resistance-en-tete-sticky")

        navigate("resources")
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "itu-regions" } }
        capture("ref-carte-trois-regions-uit")
    }

    @Test fun everyQuestionInteractionAndCorrection() {
        val examples = model.content!!.allQuestions.values.groupBy { it.kind }.mapValues { (_, questions) ->
            questions.firstOrNull { it.image == null } ?: questions.first()
        }.toMutableMap()
        examples["sort"] = examples.getValue("order").copy(id = "visual-sort", kind = "sort")
        for ((kind, question) in examples.toSortedMap()) {
            ui.runOnIdle { model.startQuestions("Les défis de Pico", listOf(question)) }
            capture("question-$kind-01-instruction")
            if (kind == "number") {
                ui.onNodeWithContentDescription("Ouvrir la calculatrice").performClick()
                ui.onNode(hasSetTextAction() and hasText("Calcul")).performTextReplacement("10*log(2)")
                ui.onNode(hasSetTextAction() and hasText("Calcul")).performImeAction()
                capture("question-calculatrice-flottante")
                ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
            }
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
            capture("question-exam1-introduction-reglementation")
            ui.runOnIdle { model.beginExamPart() }
            ui.waitUntil(15_000) { exists("Toucher pour agrandir") }
            capture("question-exam1-illustration")
            ui.onNodeWithText("Toucher pour agrandir").performClick()
            capture("question-exam1-originale")
            ui.onNodeWithContentDescription("Illustration originale : pincer pour zoomer").performTouchInput {
                pinch(start0 = Offset(width * .45f, height * .45f), end0 = Offset(width * .25f, height * .25f),
                    start1 = Offset(width * .55f, height * .55f), end1 = Offset(width * .75f, height * .75f))
            }
            capture("question-exam1-pinch-zoom")
            check(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            ui.waitUntil(5_000) { ui.onAllNodesWithContentDescription("Illustration originale : pincer pour zoomer").fetchSemanticsNodes().isEmpty() }
        }

        val examQuestions = examFixtureQuestions()
        ui.runOnIdle {
            model.startQuestions("Examen blanc", examQuestions, exam = true)
            model.session!!.apply { examPart = 1; index = 20; examIntroPending = true }
            model.revision++
        }
        capture("question-exam1-introduction-technique")
        ui.runOnIdle { model.beginExamPart() }
        capture("question-exam1-technique-x-sur-y")
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
        val exam = Session("Examen blanc", examFixtureQuestions().toMutableList(), exam = true).apply {
            index = 40; regulationScore = 9; techniqueScore = 17; unanswered = 2
            correct = 26; firstCorrect = 26; gain = 90; elapsedMillis = 2_234_000
            this.questions.forEachIndexed { i, q ->
                val omitted = i in 18..19
                val good = if (i < 20) i < 9 else i < 37
                val answer = if (good) q.answer else (q.answer + 1) % q.choices.size.coerceAtLeast(1)
                responses[i] = SessionResponse(good, omitted, if (omitted) "" else q.choices.getOrNull(answer).orEmpty(), answer)
                if (!good) missed[q.id] = q
            }
        }
        ui.runOnIdle { model.session = exam; model.revision++ }
        capture("resultat-examen-seuil-non-atteint")
        scrollTo("Partager mes résultats")
        capture("resultat-examen-cta-compacts")
        scrollTo("Récap ⬇️")
        capture("resultat-examen-recap-et-illustration")
        scrollTo("QUESTION 10")
        capture("resultat-examen-reponse-erronee")
        scrollTo("QUESTION 19")
        capture("resultat-examen-sans-reponse")
    }

    @Test fun sharePostersQrAndEveryReminderContext() {
        val own = model.progress.snapshot()
        NativeShare.renderProgressImage(context, own).copyTo(File(folder, "poster-personnel.png"), overwrite = true)
        NativeShare.renderTeamImage(context, own, demoFriends().map { it.progress }).copyTo(File(folder, "poster-equipe.png"), overwrite = true)
        NativeShare.renderResultsImage(context, ShareResults(own.name, "Examen blanc", 26, 40, 2_234_000, 2, 90, 9, 17))
            .copyTo(File(folder, "poster-resultats-examen.png"), overwrite = true)
        NativeShare.renderResultsImage(context, ShareResults(own.name, "Mix radio · 40 questions", 33, 40, 1_420_000, 1, 106))
            .copyTo(File(folder, "poster-resultats-mix.png"), overwrite = true)
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

    private fun examFixtureQuestions(): List<Question> = listOf("regulation", "technique").flatMap { section ->
        model.content!!.activeExam.filter { it.section == section }.sortedBy { if (it.image != null) 0 else 1 }.take(20)
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
