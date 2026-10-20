package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class QuestionUiVariantsTest {
    private fun q()=Question("stable","",listOf("Oui","Non"),0,"")
    @Test fun illustratedSstvIsDiscoverableAsTwoInterfacesInsideTheSameChoiceType() {
        val slant=q().copy(visual="sstv:slant")
        val loss=q().copy(id="loss",visual="sstv:loss")
        assertEquals(listOf("sstv-slant"),QuestionUiVariants.ids(slant))
        assertEquals(listOf("sstv-loss"),QuestionUiVariants.ids(loss))
        assertEquals(setOf("sstv-slant","sstv-loss"),QuestionUiVariants.representatives(listOf(slant,loss)).map {it.first.id}.toSet())
        assertEquals("choice",slant.kind);assertEquals("stable",slant.id)
    }
    @Test fun rowCompositionsAreDifferentAndExamplesRemainDiagnosticOnly() {
        val examples=DiagnosticUiExamples.questions.filter {it.kind=="rows"}
        assertEquals(setOf("rows-text","rows-dropdown","rows-mixed"),examples.flatMap(QuestionUiVariants::ids).toSet())
        assertTrue(examples.all {it.id.startsWith("diagnostic-ui-")&&it.skillIds.isEmpty()})
        assertTrue(examples.all {DiagnosticData.origin(it)=="Exemple"})
        assertTrue(examples.all {it.rows.size>=2})
    }
    @Test fun imageAndNativeArtworkNeverHideTheUnderlyingInteraction() {
        val question=q().copy(kind="text",image="image.png",visual="logic:and")
        assertEquals(setOf("image","text","logic-single"),QuestionUiVariants.ids(question).toSet())
        assertTrue(QuestionUiVariants.searchable(question).contains("Illustration"))
    }
    @Test fun signedInputIsDiscoverableAndOrdinaryCompositionIsThePrimaryExample() {
        val ordinary=q().copy(kind="number",value=3.0)
        val negative=ordinary.copy(id="negative",value=-3.0)
        assertEquals(listOf("number-signed"),QuestionUiVariants.ids(negative))
        assertTrue(QuestionUiVariants.searchable(negative).contains("signe inversible"))
        assertEquals(ordinary,QuestionUiVariants.primary(listOf(negative,ordinary)))
        val image=q().copy(id="image",image="image.png")
        assertEquals(q(),QuestionUiVariants.primary(listOf(image,q())))
        assertEquals("standard",QuestionUiVariants.representatives(listOf(image,q())).first().first.id)
    }
    @Test fun writtenRecallIncludesADiagnosticOnlyDevelopedField() {
        val short=q().copy(kind="text",acceptedAnswers=listOf("tension"))
        val long=DiagnosticUiExamples.questions.single {it.id=="diagnostic-ui-text-long"}
        assertEquals(listOf("text"),QuestionUiVariants.ids(short))
        assertEquals(listOf("text-long"),QuestionUiVariants.ids(long))
        assertTrue(long.skillIds.isEmpty())
        assertEquals("Exemple",DiagnosticData.origin(long))
        assertEquals(short,QuestionUiVariants.primary(listOf(long,short)))
        assertEquals(2,QuestionUiVariants.allCompositions(listOf(short,long)).size)
    }
    @Test fun graphicalMorseFlashcardIsDistinctFromAnOrdinaryFlashcard() {
        val ordinary=q().copy(kind="flash")
        val morse=ordinary.copy(id="morse",topic="morse",prompt="A",choices=listOf(".-"))
        assertEquals(listOf("flash-morse"),QuestionUiVariants.ids(morse))
        assertEquals(ordinary,QuestionUiVariants.primary(listOf(morse,ordinary)))
        assertEquals(2,QuestionUiVariants.allCompositions(listOf(ordinary,morse)).size)
    }
    @Test fun logicColumnsAreDistinctButDifferentGateNamesDoNotDuplicateTheInterface() {
        val singles=listOf("and","or","not","nand","nor","xor").map {gate->q().copy(id=gate,visual="logic:$gate")}
        val columns=listOf("basic","inverted","nand-nor").map {group->q().copy(id=group,visual="logic:$group")}
        assertTrue(singles.all {QuestionUiVariants.ids(it)==listOf("logic-single")})
        assertTrue(columns.all {QuestionUiVariants.ids(it)==listOf("logic-column")})
        val batch=QuestionUiVariants.allCompositions(columns+singles)
        assertEquals(2,batch.size)
        assertEquals(singles.first(),batch.first())
        assertTrue(QuestionUiVariants.searchable(columns.first()).contains("en colonne"))
    }
    @Test fun previewSearchFindsAllThreeThresholdStatesAndCategories() {
        assertTrue(DiagnosticPreviewCatalog.search("historique").map {it.title}.containsAll(listOf("Historique · aucun examen","Historique · deux examens","Historique · trois examens")))
        assertTrue(DiagnosticPreviewCatalog.search("trois","Examens").any {it.title=="Historique · trois examens"})
        assertTrue(DiagnosticPreviewCatalog.search("bibliotheque").any {it.title=="Mémo · bibliothèque"})
        assertTrue(DiagnosticPreviewCatalog.search("historique","Paramètres").isEmpty())
    }
    @Test fun allCompositionsIncludesCombinationsWithoutRepeatingTheirQuestions(){
        val plain=q()
        val illustrated=plain.copy(id="illustration",image="image.png")
        val chart=plain.copy(id="chart",visual="logic:and")
        val illustratedChart=chart.copy(id="illustrated-chart",image="image.png")
        val questions=listOf(plain,illustratedChart,illustrated,chart,chart.copy(id="duplicate"))
        assertEquals(4,QuestionUiVariants.allCompositions(questions).size)
        val representatives=QuestionUiVariants.representatives(questions)
        assertEquals(4,representatives.size)
        assertEquals(4,representatives.map {it.first.id}.distinct().size)
        val combined=representatives.single {it.second.id=="illustrated-chart"}.first
        assertTrue(QuestionUiVariants.matches(illustratedChart,combined.id))
        assertFalse(QuestionUiVariants.matches(illustrated,combined.id))
        assertEquals(questions.map(QuestionUiVariants::compositionKey).toSet(),QuestionUiVariants.allCompositions(questions).map(QuestionUiVariants::compositionKey).toSet())
        assertTrue(QuestionUiVariants.allCompositions(emptyList()).isEmpty())
    }
    @Test fun aMeasuredCircuitHasOneInterfaceAndBatchTestingIncludesEachCompositionOnce(){
        val measured=q().copy(kind="circuit",visual="circuit:measurement")
        val duplicate=measured.copy(id="same-ui",prompt="Une autre formulation")
        val series=measured.copy(id="series",visual="circuit:series")
        val receiver=measured.copy(id="receiver",visual="circuit:receiver")
        assertEquals(listOf("circuit-measurement"),QuestionUiVariants.ids(measured))
        val batch=QuestionUiVariants.allCompositions(listOf(measured,duplicate,series,receiver))
        assertEquals(3,batch.size)
        assertEquals(3,batch.map(QuestionUiVariants::compositionKey).distinct().size)
        assertTrue(batch.all {it.id in setOf("stable","same-ui","series","receiver")})
    }

}
