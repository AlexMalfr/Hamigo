package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Native icons name the actual Hamigo tools; this does not describe equipment allowed at the official exam. */
@Composable internal fun StudyToolsHint(modifier:Modifier=Modifier) {
    val description="Dans Hamigo, les boutons calculatrice et brouillon en bas à droite restent accessibles dans les questions, les fiches Mémo et les examens blancs."
    val text=buildAnnotatedString {
        withStyle(SpanStyle(fontWeight=FontWeight.Bold,color=Teal)){append("Astuce : ")}
        append("dans Hamigo, les boutons calculatrice\u00a0")
        appendInlineContent("calculator","[calculatrice]")
        append(" et brouillon\u00a0")
        appendInlineContent("scratchpad","[brouillon]")
        append(" en bas à droite restent accessibles dans les questions, les fiches Mémo et les examens blancs.")
    }
    val placeholder=Placeholder(20.sp,20.sp,PlaceholderVerticalAlign.TextCenter)
    val icons=mapOf(
        "calculator" to InlineTextContent(placeholder) {Icon(Icons.Rounded.Calculate,null,Modifier.fillMaxSize(),tint=Teal)},
        "scratchpad" to InlineTextContent(placeholder) {Icon(Icons.Rounded.EditNote,null,Modifier.fillMaxSize(),tint=Teal)}
    )
    Text(text,inlineContent=icons,fontSize=13.sp,lineHeight=20.sp,color=Muted,
        modifier=modifier.fillMaxWidth().padding(vertical=6.dp).testTag("study-tools-hint")
            .clearAndSetSemantics {contentDescription=description})
}
