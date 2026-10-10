package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class FrenchSpeechTest {
    @Test fun morseSignalsBecomePointsTraitsAndExplicitSeparatorsWithoutReadingPunctuation() {
        assertEquals("Quel caractère correspond à trait, point ?",FrenchSpeech.prepare("Quel caractère correspond à ━• ?"))
        assertEquals("Lis en Morse point, trait, pause entre lettres, trait, point, pause entre mots, point",FrenchSpeech.prepare("Lis en Morse .- -. / ."))
        assertEquals("Une phrase. Puis une autre.",FrenchSpeech.prepare("Une phrase. Puis une autre."))
        assertEquals("Le rapport point-trait du Morse reste fixe.",FrenchSpeech.prepare("Le rapport point-trait du Morse reste fixe."))
    }
    @Test fun formulasKeepProductsFractionsPowersRootsAndGroupingWithoutEvaluatingThem() {
        assertEquals("u égale ère fois i",FrenchSpeech.prepare("U = R × I"))
        assertEquals("pé égale u au carré divisé par ère",FrenchSpeech.prepare("P = U² / R"))
        assertEquals("i maximum égale i efficace divisé par racine carrée de 2",FrenchSpeech.prepare("Imax = Ieff / √2"))
        val root=FrenchSpeech.prepare("P = √(R × I)")
        assertTrue(root.contains("racine carrée de ère fois i")) // Keep the intentionally wrong distractor wrong.
        assertEquals("double vé égale ère fois i au carré fois té",FrenchSpeech.prepare("W = RI²t"))
        assertTrue(FrenchSpeech.prepare("R = U / (I + 2)").contains("ouvrir la parenthèse, i plus 2, fermer la parenthèse"))
    }
    @Test fun unitsAndDecimalsAreExplicitAndPrefixesRemainCaseSensitive() {
        val actual=FrenchSpeech.prepare("2,2 µF, 750 mV et 145.05 MHz ; 10 mW, 10 MW et 4 kΩ.")
        assertTrue(actual.contains("2 virgule deux microfarads"));assertTrue(actual.contains("750 millivolts"))
        assertTrue(actual.contains("145 virgule zéro cinq mégahertz"));assertTrue(actual.contains("10 milliwatts"))
        assertTrue(actual.contains("10 mégawatts"));assertTrue(actual.contains("4 kiloohms"))
        assertEquals("u égale 5 volts",FrenchSpeech.prepare("U = 5 V"))
        assertTrue(FrenchSpeech.prepare("Une résistance en Ω et une capacité en F.").contains("en farads"))
        assertTrue(FrenchSpeech.prepare("Q = 100 µC").contains("100 microcoulombs"))
        val prose=FrenchSpeech.prepare("La formule crête/√2 s’applique directement.")
        assertTrue(prose.contains("s’applique"));assertFalse(prose.contains("secondes"))
    }
    @Test fun greekIndicesExponentsComparisonsAndRadioAbbreviationsAreSpoken() {
        val actual=FrenchSpeech.prepare("λ ≈ 300/f ; R₁ = 10³ Ω ; 10⁻⁶ F ; ±5 % ; U ≤ 5 V ; log₁₀(100).")
        listOf("lambda","environ égal","indice 1","au cube","puissance moins 6","plus ou moins","pour cent","inférieur ou égal","logarithme en base dix").forEach {assertTrue("Missing $it in $actual",actual.contains(it))}
        assertEquals("cé ku et ku ère emme, vé ache effe.",FrenchSpeech.prepare("CQ et QRM, VHF."))
        assertTrue(FrenchSpeech.prepare("F4ABC/P").contains("barre oblique"))
    }
    @Test fun normalizationDoesNotLeakPlaceholdersOrReadUrlsOrInventAnAnswer() {
        val prompt="Consulte https://exam1.r-e-f.org/ puis calcule U = R × I. Quelle tension ?"
        val actual=FrenchSpeech.prepare(prompt)
        assertTrue(actual.contains("lien vers la source"));assertFalse(actual.contains("https"));assertFalse(actual.contains('\uE000'))
        assertFalse(actual.contains("240"));assertEquals("",FrenchSpeech.prepare(""))
    }
    @Test fun scientificNotationBinaryDigitsAndLogicSymbolsHaveExplicitReadings() {
        assertEquals("2 virgule cinq fois dix puissance moins 3",FrenchSpeech.prepare("2.5e-3"))
        assertEquals("un un zéro un, en base deux",FrenchSpeech.prepare("1101₂"))
        val logic=FrenchSpeech.prepare("A ∧ B ; A ∨ B ; ¬ A ; A ⊕ B")
        listOf("et","ou","non","ou exclusif").forEach {assertTrue(logic.contains(it))}
    }
    @Test fun labelledMorseCharactersBecomeNaturalSentencesIncludingLiteralPunctuation() {
        assertEquals("Le caractère arobase se lit point trait trait point trait point.",FrenchSpeech.prepare("@ : .--.-."))
        val text=FrenchSpeech.prepare("? : ••━━•• · / : ━••━• · . : •━•━•━ · = : ━•••━ · , : ━━••━━ · @ : •━━•━•")
        listOf("Le caractère point d’interrogation se lit", "Le caractère barre oblique se lit trait point point trait point.","Le caractère point final se lit", "Le caractère égal se lit","Le caractère virgule se lit","Le caractère arobase se lit").forEach {assertTrue(text.contains(it))}
        assertFalse(text.contains("pause entre mots"));assertFalse(text.contains("·"))
        assertEquals("La lettre e se lit point. La lettre té se lit trait.",FrenchSpeech.prepare("E : • · T : ━"))
        assertTrue(FrenchSpeech.prepare("1 : •━━━━").startsWith("Le chiffre un se lit point trait trait trait trait."))
    }
    @Test fun punctuationEnumerationsTimingTablesAndPrefixDefinitionsAreReadAsMeaningfulPhrases() {
        val prose=FrenchSpeech.prepare("Cette étape travaille ?, /, ., =, ,, @. Ces signes servent aux messages.")
        assertTrue(prose.contains("le point d’interrogation, la barre oblique, le point final, le signe égal, la virgule et l’arobase."))
        val timing=FrenchSpeech.prepare("point 1 · trait 3 · pause interne 1 · entre lettres 3 · entre mots 7")
        assertEquals("Un point dure une unité. Un trait dure 3 unités. La pause entre deux éléments dure une unité. La pause entre deux lettres dure 3 unités. La pause entre deux mots dure 7 unités.",timing)
        val prefixes=FrenchSpeech.prepare("1 k = 10³ · 1 m = 10⁻³ · 1 µ = 10⁻⁶")
        listOf("Le préfixe kilo correspond à dix puissance 3.","Le préfixe milli correspond à dix puissance moins 3.","Le préfixe micro correspond à dix puissance moins 6.").forEach {assertTrue(prefixes.contains(it))}
        assertFalse(prefixes.contains("mètres"))
    }
    @Test fun ordinaryEquationsAndMultipleFormulasAreNeverMistakenForMorseExamples() {
        assertEquals("té égale moins i",FrenchSpeech.prepare("T = - I"))
        assertEquals("u égale ère fois i",FrenchSpeech.prepare("U = R · I"))
        assertEquals("i égale u divisé par ère . ère égale u divisé par i . i égale pé divisé par u",FrenchSpeech.prepare("I = U/R · R = U/I · I = P/U"))
    }
    @Test fun scientificAndUnitExplanationsAreSpokenAsUnitsWhileEquationsKeepTheirVariables() {
        val definition=FrenchSpeech.prepare("Les unités aident : V/Ω donne A, et W/V donne A. Utilise ohms et F ; une vitesse en m/s.")
        listOf("volts par ohm donne ampères","watts par volt donne ampères","ohms et farads","mètres par seconde").forEach {assertTrue(definition.contains(it))}
        assertEquals("Une notation scientifique écrit a fois 10 puissance enne .",FrenchSpeech.prepare("Une notation scientifique écrit a × 10ⁿ."))
        assertTrue(FrenchSpeech.prepare("1 µF vaut 1000 nF.").contains("1 microfarad vaut 1000 nanofarads"))
        assertEquals("pé égale double vé divisé par vé",FrenchSpeech.prepare("P = W/V"))
        val course=FrenchSpeech.prepare("Une formule attend généralement les unités de base. Pour RC, utilise ohms et F; pour une longueur d’onde, hertz si la vitesse est en m/s.")
        assertTrue(course,course.contains("ohms et farads"))
        assertTrue(FrenchSpeech.prepare("Les unités de base : utilise Ω et F.").contains("ohms et farads"))
    }
}
