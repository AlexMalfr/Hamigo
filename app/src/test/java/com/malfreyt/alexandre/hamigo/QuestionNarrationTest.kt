package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class QuestionNarrationTest {
    private val q=Question("narration-test","L’opérateur annonce son ___.",listOf("numéro de téléphone","niveau dans Hamigo","indicatif","mot de passe"),2,"SECRET CORRECTION",kind="cloze",value=98765.0)
    @Test fun rowsReadVisiblePromptsAndAlternativesWithoutRevealingTextAnswersOrMarkingTheCorrectChoice() {
        val rows=q.copy(kind="rows",prompt="Complète ces lignes.",choices=emptyList(),rows=listOf(
            AnswerRow("letter","B",acceptedAnswers=listOf("SECRET ANSWER")),
            AnswerRow("method","Quelle opération ?",choices=listOf("U/R","U × R","R/U"),answer=1)))
        val text=QuestionNarration.text(rows)
        assertTrue(text.contains("Ligne 1. La lettre B. Écris ta réponse."))
        assertTrue(text.contains("Ligne 2. Quelle opération ? Choisis parmi :"))
        assertTrue(text.contains("u divisé par ère"));assertTrue(text.contains("u fois ère"));assertTrue(text.contains("ère divisé par u"))
        assertFalse(text.contains("SECRET"));assertFalse(text.contains("98765"))
        assertEquals(text,QuestionNarration.text(rows.copy(rows=listOf(rows.rows[0].copy(acceptedAnswers=listOf("ANOTHER HIDDEN ANSWER")),rows.rows[1].copy(answer=2)))))
    }
    @Test fun clozeIsAnOralSentenceWithAllChoicesAndNoAnswerMarker() {
        val text=FrenchSpeech.prepare(QuestionNarration.text(q))
        assertEquals("L’opérateur annonce son quelque chose. Complète avec une réponse parmi : numéro de téléphone, niveau dans Hamigo, indicatif, ou mot de passe.",text)
        assertFalse(text.contains("SECRET"));assertFalse(text.contains("98765"))
    }
    @Test fun orderUsesTheDisplayedOrderAndMatchingNeverReadsThePairsTogether() {
        val ordered=QuestionNarration.text(q.copy(kind="order"),listOf("mot de passe","indicatif","niveau dans Hamigo","numéro de téléphone"))
        assertTrue(ordered.contains("mot de passe ; indicatif ; niveau dans Hamigo ; numéro de téléphone"))
        val paired=QuestionNarration.text(q.copy(kind="match",pairs=listOf(PairItem("Zulu","Alpha"),PairItem("Bravo","Charlie"))))
        assertTrue(paired.contains("premier groupe : Zulu ; Bravo"));assertTrue(paired.contains("second groupe :"))
        assertTrue(paired.contains("Alpha"));assertTrue(paired.contains("Charlie"))
        assertFalse(paired.contains("Zulu → Alpha"))
    }
    @Test fun listeningAndFlashcardsNeverRevealHiddenAudioOrAnswer() {
        val listen=q.copy(kind="morseListen",choices=listOf("E","M"),bands=listOf("--"))
        val text=FrenchSpeech.prepare(QuestionNarration.text(listen))
        assertTrue(text.contains("la lettre emme"));assertFalse(text.contains("trait"))
        assertFalse(QuestionNarration.text(q.copy(kind="flash")).contains("indicatif"))
        assertTrue(QuestionNarration.text(q.copy(kind="flash"),flashAnswer=true).contains("numéro de téléphone"))
    }
    @Test fun everyInputFamilyHasAnOralInstructionWithoutReadingItsStoredSolution() {
        for(kind in listOf("choice","multiselect","truefalse","number","frequency","estimate","binary","morseEncode","resistor","waveform")) {
            val text=QuestionNarration.text(q.copy(kind=kind,unit="V",tolerance=.05))
            assertFalse(kind,text.contains("98765"));assertFalse(kind,text.contains("SECRET"));assertTrue(kind,text.length>q.prompt.length)
        }
    }
    @Test fun qCodesAreSpelledConsistentlyWithoutAHandPickedWhitelist() {
        val native=q.copy(id="q-613ebac6-55ec-4f76-aebd-b56adc28ecbd",kind="choice",prompt="Que signifie QRT ?")
        assertTrue(FrenchSpeech.prepare(QuestionNarration.text(native)).startsWith("Que signifie Q ère té ?"))
        assertTrue(FrenchSpeech.prepare(QuestionNarration.text(native.copy(id="q-8cca3d67-edbf-42b9-bb8b-853a2c60d5cf",prompt="Que demande QRZ ?"))).startsWith("Que demande Q ère zède ?"))
        for((code,spoken) in listOf("QRV" to "Q ère vé","QSB" to "Q esse bé","QSA" to "Q esse a","QTR" to "Q té ère")) {
            assertEquals("Code $spoken.",FrenchSpeech.prepare("Code $code."))
        }
        assertEquals("non et",FrenchSpeech.prepare("NAND"))
    }
    @Test fun examMarkUnitIsReadAsAScaleInsteadOfADivision() {
        val exam=q.copy(prompt="Combien de bonnes réponses au minimum faut-il en réglementation ?",kind="number",choices=emptyList(),unit="/20",tolerance=0.0)
        val spoken=FrenchSpeech.prepare(QuestionNarration.text(exam))
        assertEquals("Combien de bonnes réponses au minimum faut-il en réglementation ? Saisis une note sur 20. La valeur doit être exacte.",spoken)
        assertFalse(spoken.contains("divisé"))
        assertTrue(FrenchSpeech.prepare(QuestionNarration.text(exam.copy(unit=" / 100 ",tolerance=1.0))).contains("note sur 100. Une différence maximale de 1 point est acceptée."))
        assertTrue(FrenchSpeech.prepare(QuestionNarration.text(exam.copy(tolerance=2.0))).contains("2 points est acceptée"))
        assertTrue(FrenchSpeech.prepare(QuestionNarration.text(exam.copy(unit="V"))).contains("Saisis une valeur en volts."))
        assertEquals("10 divisé par 20",FrenchSpeech.prepare(FrenchSpeech.option("10/20")))
    }
    @Test fun aWordProblemBeginningWithANumberIsNotRenamedAsAConversion() {
        val exam=q.copy(id="q-8dc6ff85-64f1-4e1a-9ea3-ddd9684a44d6",prompt="20 questions de réglementation en 15 minutes : temps moyen par question en secondes ?",kind="number",choices=emptyList(),unit="s",tolerance=0.0)
        val spoken=FrenchSpeech.prepare(QuestionNarration.text(exam))
        assertTrue(spoken.startsWith(exam.prompt))
        assertFalse(spoken.contains("Convertis"))
        assertFalse(QuestionNarration.text(exam.copy(prompt="20 questions en minutes ?")).contains("Convertis"))
        assertTrue(QuestionNarration.text(exam.copy(prompt="2,5 MHz en kHz ?")).startsWith("Convertis 2,5 MHz en kHz."))
        assertTrue(QuestionNarration.text(exam.copy(prompt="0,5A en mA ?")).startsWith("Convertis 0,5A en mA."))
    }
    @Test fun standaloneLettersAndRadioAbbreviationsDoNotDependOnEngineGuesses() {
        assertEquals("Cette étape travaille e, té, i et emme.",FrenchSpeech.prepare("Cette étape travaille E, T, I, M."))
        assertTrue(FrenchSpeech.prepare("La lettre M. Le groupe DE.").contains("La lettre emme. Le groupe dé e."))
        assertTrue(FrenchSpeech.prepare("Compose le caractère / en Morse.").contains("barre oblique"))
        assertTrue(FrenchSpeech.prepare("20 QCM, au moins 10/20.").contains("Q cé emme"))
        assertTrue(FrenchSpeech.prepare("de 144–146 MHz").contains("144 à 146"))
    }
    @Test fun formulaAlternativesKeepTheirOperatorsAndProseFullStopsAreNotMorse() {
        assertEquals("pé divisé par u",FrenchSpeech.prepare(FrenchSpeech.option("P/U")))
        assertEquals("u moins pé",FrenchSpeech.prepare(FrenchSpeech.option("U−P")))
        assertEquals("u moins pé",FrenchSpeech.prepare(FrenchSpeech.option("U-P")))
        assertEquals("2 fois effe",FrenchSpeech.prepare(FrenchSpeech.option("2f")))
        assertEquals("effe",FrenchSpeech.option("f"))
        assertEquals("non(1) égale 0.",FrenchSpeech.prepare("NON(1) = 0."))
        assertFalse(FrenchSpeech.prepare("Ce circuit équivaut à NON ET(1, 1).").contains("point"))
        assertTrue(FrenchSpeech.prepare("|Γ| est positif.").contains("valeur absolue de gamma"))
        assertTrue(QuestionNarration.text(q.copy(prompt="2,5 MHz en kHz ?",kind="number")).startsWith("Convertis 2,5 MHz en kHz."))
    }
}
