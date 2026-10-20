package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun LessonParagraph(lesson:Lesson,index:Int,modifier:Modifier=Modifier,onOpenLesson:((String)->Unit)?=null,
    morseContent:(@Composable ()->Unit)?=null) {
    val source=lesson.body[index]
    val style=lesson.presentation[index] ?: LessonParagraphPresentation()
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(style.heading.isNotBlank())Text(style.heading,Modifier.padding(top=8.dp).semantics {heading()},
            fontSize=19.sp,lineHeight=25.sp,fontWeight=FontWeight.Bold,color=Teal)
        if(morseContent!=null)morseContent()
        else {
            val text=if(style.tip.isBlank())source else source.replace(style.tip,"").trim()
            val chunks=remember(text,style.breakBefore){
                val starts=(listOf(0)+style.breakBefore.mapNotNull {text.indexOf(it).takeIf {i->i>0}}).sorted()
                starts.mapIndexed {i,start->text.substring(start,starts.getOrNull(i+1) ?: text.length).trim()}.filter {it.isNotBlank()}
            }
            chunks.forEach {chunk->
                if(morseTextParts(chunk).any {it.code})MorseAwareText(chunk,fontSize=16.sp,lineHeight=25.sp)
                else AppReferenceText(chunk,fontSize=16.sp,lineHeight=25.sp,emphasis=style.emphasis,references=style.references,onOpenLesson=onOpenLesson)
            }
            if(style.tip.isNotBlank())AppReferenceText(style.tip,Modifier.padding(vertical=4.dp).testTag("lesson-tip-${lesson.id}"),
                fontSize=17.sp,lineHeight=26.sp,fontStyle=FontStyle.Italic,color=Teal)
        }
    }
}

/** An in-place reference, never a navigation action or a second lesson/session. */
@Composable internal fun LessonReferenceDialog(lesson:Lesson,onDismiss:()->Unit) {
    val density=LocalDensity.current
    val window=LocalWindowInfo.current.containerSize
    val maxHeight=with(density){window.height.toDp()}-48.dp
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(shape=RoundedCornerShape(24.dp),color=Cream,modifier=Modifier.padding(horizontal=20.dp).widthIn(max=560.dp).fillMaxWidth().heightIn(max=maxHeight).testTag("lesson-reference-dialog")) {
            Column(Modifier.padding(horizontal=20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("APERÇU DU COURS",Modifier.weight(1f),fontSize=11.sp,fontWeight=FontWeight.Bold,color=Muted)
                    IconButton(feedbackClick(onDismiss),Modifier.testTag("lesson-reference-close")) {Icon(Icons.Rounded.Close,"Fermer l’aperçu du cours")}
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("lesson-reference-content"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(lesson.title,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                    Text(lesson.summary,fontSize=14.sp,lineHeight=21.sp,color=Muted)
                    // Use the same prose and native Morse shapes. Reference links are inert in the preview.
                    lesson.body.indices.forEach {index->LessonParagraph(lesson,index)}
                    if(lesson.formula.isNotBlank())Panel(color=Mist){Eyebrow("À RETENIR");MorseAwareText(lesson.formula,fontSize=18.sp,fontWeight=FontWeight.Bold)}
                    Spacer(Modifier.height(4.dp))
                }
                TextButton(feedbackClick(onDismiss),Modifier.align(Alignment.End).padding(vertical=4.dp)) {Text("Fermer",fontWeight=FontWeight.Bold)}
            }
        }
    }
}
