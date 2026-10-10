package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the actual packaged content and persistent progress without touching the user's prefs. */
@RunWith(AndroidJUnit4::class)
class ContentProgressTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val isolatedContexts = mutableListOf<IsolatedContext>()

    @After
    fun removeTestPreferences() {
        isolatedContexts.forEach { it.cleanup() }
    }

    @Test
    fun packagedCurriculumReferencesAndExamAreCompleteAndUsable() {
        val content = Content(context)
        assertTrue(content.lessons.size >= 90)
        assertTrue(content.lessons.sumOf { it.questions.size } >= 800)
        assertTrue(content.references.size > 18)
        assertTrue(content.flashcards.size >= 390)
        assertTrue(content.references.all { it.group.isNotBlank() && it.source.startsWith("http") })
        assertEquals(content.flashcards.size, content.flashcards.map { it.id }.toSet().size)
        assertEquals("A",content.allQuestions.getValue("flash-nato-0").prompt)
        assertEquals("Alfa",content.allQuestions.getValue("flash-nato-0").choices.single())
        assertEquals(2950, content.activeExam.size)
        assertEquals(content.lessons.size, content.lessons.map { it.id }.toSet().size)
        assertTrue(content.activeExam.none { it.id in content.excluded })
        assertTrue(content.activeExam.any { it.section == "regulation" })
        assertTrue(content.activeExam.any { it.section == "technique" })
        content.lessons.forEach { lesson ->
            assertTrue("Lesson ${lesson.id} has no readable body", lesson.body.any { it.isNotBlank() })
            assertTrue("Lesson ${lesson.id} has too little practice", lesson.questions.size >= 8)
        }
        (content.lessons.flatMap { it.questions } + content.activeExam).forEach { question ->
            assertTrue("Missing prompt: ${question.id}", question.prompt.isNotBlank())
            if (question.choices.isNotEmpty())
                assertTrue("Answer out of bounds: ${question.id}", question.answer in question.choices.indices)
            question.image?.let { path -> context.assets.open(path).use { assertTrue(it.read() >= 0) } }
        }
        assertEquals(content.lessons.first(), content.nextLesson(emptySet()))
        assertEquals(content.lessons[1], content.nextLesson(setOf(content.lessons.first().id)))
        assertNull(content.nextLesson(content.lessons.map { it.id }.toSet()))
    }

    @Test
    fun proceduralQuestionsFromAThousandSeedsStayAvailableForSpacedRevision() {
        val content = Content(context)
        val generated = (0 until 1000).flatMap { PracticeGenerator.create(it) }.distinctBy { it.id }
        assertEquals(68, generated.size)
        generated.forEach { question ->
            val registered = content.allQuestions[question.id]
            assertNotNull("Generated question cannot be recalled later: ${question.id}", registered)
            assertEquals(question.value, registered!!.value)
            assertEquals(question.kind, registered.kind)
        }
        val progress = Progress(isolated())
        val selected = generated.first()
        progress.answer(selected.id, correct = false)
        val backup = JSONObject(progress.export())
        backup.getJSONObject("progress").getJSONObject("reviews").getJSONObject(selected.id).put("due", 1L)
        progress.import(backup.toString())
        assertTrue(progress.due(content).any { it.id == selected.id })
    }

    @Test
    fun earlySuccessfulRereadingDoesNotAdvanceFutureRecall() {
        val progress = Progress(isolated())
        assertEquals(3, progress.answer("test-early", correct = true))
        val first = progress.reviews.getValue("test-early")
        assertEquals(1, first.repetitions)
        assertEquals(1.0, first.interval, 0.0)
        assertTrue(first.due > System.currentTimeMillis())
        repeat(12) {
            assertEquals(0, progress.answer("test-early", correct = true, quality = 5))
            assertEquals(first, progress.reviews.getValue("test-early"))
        }
        assertEquals(3, progress.xp)
        assertEquals(13, progress.totalAnswers)
        assertEquals(13, progress.totalCorrect)
    }

    @Test
    fun immediateCorrectionRemediatesAnIncorrectAnswerWithoutExtraXp() {
        val progress = Progress(isolated())
        assertEquals(1, progress.answer("test-remediate", correct = false))
        val failed = progress.reviews.getValue("test-remediate")
        assertEquals(0, failed.repetitions)
        assertEquals(1, failed.lapses)
        assertEquals(0.0, failed.interval, 0.0)
        assertEquals(0, progress.answer("test-remediate", correct = true))
        val recovered = progress.reviews.getValue("test-remediate")
        assertEquals(1, recovered.repetitions)
        assertEquals(1, recovered.lapses)
        assertEquals(1.0, recovered.interval, 0.0)
        assertTrue(recovered.due - failed.due > 23L * 60 * 60 * 1000)
        assertEquals(1, progress.xp)
        assertEquals(2, progress.totalAnswers)
        assertEquals(1, progress.totalCorrect)
    }

    @Test
    fun lessonCompletionAwardsSixXpOnceAndUnlocksTheNextLesson() {
        val content = Content(context)
        val progress = Progress(isolated())
        val first = content.lessons.first()
        assertEquals(6, progress.complete(first.id))
        assertEquals(0, progress.complete(first.id))
        assertEquals(6, progress.xp)
        assertEquals(setOf(first.id), progress.completed)
        assertEquals(1, progress.snapshot().lessons)
        assertEquals(content.lessons[1], content.nextLesson(progress.completed))
    }

    @Test
    fun repeatingTheSameQuestionsCannotFarmDailyXp() {
        val progress = Progress(isolated())
        assertEquals(3, progress.answer("test-card-a", correct = true))
        assertEquals(1, progress.answer("test-card-b", correct = false))
        repeat(15) {
            assertEquals(0, progress.answer("test-card-a", correct = it % 2 == 0))
            assertEquals(0, progress.answer("test-card-b", correct = true))
        }
        assertEquals(4, progress.xp)
        assertEquals(4, progress.todayXp)
        assertEquals(4, progress.weeklyXp)
        assertEquals(1, progress.streak)
    }

    @Test
    fun savedProgressAndBackupRestorePreserveLearningButExcludeCredentials() {
        val sourceContext = isolated()
        val source = Progress(sourceContext)
        source.name = "F4 Test"
        source.answer("test-persistent", correct = true)
        source.complete("test-lesson")
        val privateMarker = "synthetic-credential-never-export"
        sourceContext.getSharedPreferences("hamigo_secure", Context.MODE_PRIVATE).edit()
            .putString("ciphertext", privateMarker).commit()
        source.prefs.edit().putString("githubToken", privateMarker).commit()

        val reloaded = Progress(sourceContext)
        assertEquals(source.name, reloaded.name)
        assertEquals(source.xp, reloaded.xp)
        assertEquals(source.completed, reloaded.completed)
        assertEquals(source.reviews, reloaded.reviews)
        assertEquals(source.totalAnswers, reloaded.totalAnswers)
        val exported = reloaded.export()
        assertFalse(exported.contains(privateMarker))
        val document = JSONObject(exported)
        assertEquals("hamigo", document.getString("app"))
        assertEquals(2, document.getInt("schema"))
        assertEquals(setOf("app", "schema", "name", "progress", "profileUpdatedAt", "preferencesUpdatedAt", "preferences", "friends", "socialInbox"), document.keys().asSequence().toSet())

        val target = Progress(isolated())
        target.import(exported)
        assertEquals(source.name, target.name)
        assertEquals(source.xp, target.xp)
        assertEquals(source.completed, target.completed)
        assertEquals(source.reviews, target.reviews)
        assertEquals(source.todayXp, target.todayXp)
        assertEquals(0, target.complete("test-lesson"))
        assertEquals(0, target.answer("test-persistent", correct = true))
        assertEquals(9, target.xp)
    }

    @Test
    fun invalidBackupsAreRejectedWithoutReplacingTheCurrentProgress() {
        val progress = Progress(isolated())
        progress.name = "Test intact"
        progress.answer("test-validation", correct = true)
        val before = progress.export()
        val candidates = listOf(
            JSONObject(before).put("app", "another-app").toString(),
            JSONObject(before).put("schema", 3).toString(),
            JSONObject(before).also { it.getJSONObject("progress").put("xp", -1) }.toString(),
            JSONObject(before).also { it.getJSONObject("progress").put("answers", -1) }.toString(),
            JSONObject(before).also { it.getJSONObject("progress").getJSONObject("reviews")
                .getJSONObject("test-validation").put("due", -1) }.toString(),
            JSONObject(before).also { it.getJSONObject("progress").getJSONObject("reviews")
                .getJSONObject("test-validation").put("ease", 1.2) }.toString(),
            JSONObject(before).also { it.getJSONObject("progress").getJSONObject("reviews")
                .getJSONObject("test-validation").put("interval", -1) }.toString()
        )
        candidates.forEach { invalid ->
            try { progress.import(invalid); fail("Invalid backup accepted") }
            catch (_: IllegalArgumentException) { /* expected */ }
            assertEquals(before, progress.export())
        }
    }

    private fun isolated(): IsolatedContext = IsolatedContext(context).also { isolatedContexts += it }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val namespace = "content-progress-test-${UUID.randomUUID()}"
        private val files = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = "$namespace-$name"
            files += isolatedName
            return baseContext.getSharedPreferences(isolatedName, mode)
        }
        fun cleanup() {
            files.forEach {
                baseContext.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
                baseContext.deleteSharedPreferences(it)
            }
        }
    }
}
