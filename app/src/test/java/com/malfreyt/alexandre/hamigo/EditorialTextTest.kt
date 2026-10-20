package com.malfreyt.alexandre.hamigo

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class EditorialTextTest {
    @Test fun onlyStandaloneAppNamesAndReportFlagsReceiveIcons() {
        val source="Parcours, Mémo et Équipe. Moi : Certificat ou Exploration. Le drapeau de signalement."
        assertEquals(listOf(AppTextTerm.PATH,AppTextTerm.MEMO,AppTextTerm.TEAM,AppTextTerm.PROFILE,AppTextTerm.CERTIFICATE,AppTextTerm.EXPLORATION,AppTextTerm.REPORT),appTextMatches(source).map {it.term})
        assertTrue(appTextMatches("Le certificat français explore la radio. Parcoursien, Mémoire et Équipement.").isEmpty())
    }
    @Test fun typographyPreservesWordsPunctuationAndInteractiveRangesAfterInsertedIcons() {
        val source="Mémo complète la loi d’Ohm ; Défis sert à pratiquer."
        var opened=""
        val prose=formattedProse(source,listOf("sert à pratiquer"),listOf(LessonTextReference("la loi d’Ohm","c03-l03"))){opened=it}
        assertEquals(source,prose.text.text.replace("\u00a0\uFFFC",""))
        assertEquals(2,prose.placeholders.size)
        val link=prose.links.single()
        assertEquals("la loi d’Ohm",prose.text.text.substring(link.first,link.last+1))
        assertTrue(prose.text.spanStyles.any {it.item.fontWeight==FontWeight.Bold&&prose.text.text.substring(it.start,it.end)=="sert à pratiquer"})
        val annotation=prose.text.getLinkAnnotations(link.first,link.last+1).single().item as androidx.compose.ui.text.LinkAnnotation.Clickable
        annotation.linkInteractionListener!!.onClick(annotation)
        assertEquals("c03-l03",opened)
    }
    @Test fun anInertPreviewKeepsEmphasisWithoutAddingNavigationActions() {
        val prose=formattedProse("La loi d’Ohm.",references=listOf(LessonTextReference("loi d’Ohm","c03-l03")))
        assertEquals(1,prose.links.size)
        assertTrue(prose.text.getLinkAnnotations(0,prose.text.length).isEmpty())
    }
}
