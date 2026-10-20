package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.json.JSONObject

data class LessonTextReference(val text:String,val lessonId:String)
data class LessonParagraphPresentation(val heading:String="",val emphasis:List<String> = emptyList(),
    val tip:String="",val breakBefore:List<String> = emptyList(),val references:List<LessonTextReference> = emptyList())
internal fun parseLessonPresentation(paragraphs:JSONObject):Map<Int,LessonParagraphPresentation> =
    paragraphs.keys().asSequence().associate {key->
        val p=paragraphs.getJSONObject(key)
        key.toInt() to LessonParagraphPresentation(p.optString("heading"),p.optJSONArray("emphasis")?.strings().orEmpty(),
            p.optString("tip"),p.optJSONArray("breakBefore")?.strings().orEmpty(),
            p.optJSONArray("references")?.objects().orEmpty().map {LessonTextReference(it.getString("text"),it.getString("lessonId"))})
    }

/** Exact app names only: ordinary words such as “certificat” keep their technical meaning. */
internal enum class AppTextTerm(val label:String,val icon:ImageVector,val color:Color) {
    PATH("Parcours",Icons.Rounded.Route,Teal),MEMO("Mémo",Icons.Rounded.MenuBook,Teal),
    PRACTICE("Défis",Icons.Rounded.Bolt,Teal),TEAM("Équipe",Icons.Rounded.Groups,Teal),PROFILE("Moi",Icons.Rounded.Person,Teal),
    CERTIFICATE("Certificat",Icons.Rounded.School,Teal),EXPLORATION("Exploration",Icons.Rounded.Explore,Purple),
    REPORT("drapeau",Icons.Rounded.Flag,Coral);
    // Keep the familiar icon palette while giving small prose enough contrast on cream/white.
    val textColor:Color get()=when(this){REPORT->Color(0xFFAC493B);EXPLORATION->Color(0xFF6C529F);else->color}
}
internal data class AppTextMatch(val start:Int,val end:Int,val term:AppTextTerm)
internal fun appTextMatches(source:String):List<AppTextMatch> = AppTextTerm.entries.flatMap {term->
    val name=if(term==AppTextTerm.REPORT)"(?i:${Regex.escape(term.label)})" else Regex.escape(term.label)
    Regex("(?<![\\p{L}\\p{N}])$name(?![\\p{L}\\p{N}])").findAll(source).map {AppTextMatch(it.range.first,it.range.last+1,term)}.toList()
}.sortedBy {it.start}
internal data class FormattedProse(val text:AnnotatedString,val icons:Set<AppTextTerm>,val placeholders:List<AnnotatedString.Range<Placeholder>>,val links:List<IntRange>)

/** Decoration never changes authored/spoken text, choices, answers or their identities. */
internal fun formattedProse(source:String,emphasis:List<String> = emptyList(),references:List<LessonTextReference> = emptyList(),
    onOpenLesson:((String)->Unit)?=null):FormattedProse {
    val matches=appTextMatches(source);val endings=matches.associateBy {it.end}
    val positions=IntArray(source.length+1);val placeholders=mutableListOf<AnnotatedString.Range<Placeholder>>()
    val links=mutableListOf<IntRange>()
    val annotated=buildAnnotatedString {
        for(index in 0..source.length){
            endings[index]?.let {match->
                addStyle(SpanStyle(color=match.term.textColor,fontWeight=FontWeight.Bold),positions[match.start],length)
                append('\u00a0');val at=length;appendInlineContent(match.term.name,"\uFFFC")
                placeholders+=AnnotatedString.Range(Placeholder(1.em,1.em,PlaceholderVerticalAlign.TextCenter),at,length)
            }
            positions[index]=length
            if(index<source.length)append(source[index])
        }
        for(phrase in emphasis)Regex(Regex.escape(phrase)).findAll(source).forEach {m->
            addStyle(SpanStyle(fontWeight=FontWeight.Bold),positions[m.range.first],positions[m.range.last+1])
        }
        for(ref in references)Regex(Regex.escape(ref.text)).findAll(source).forEach {m->
            val start=positions[m.range.first];val end=positions[m.range.last+1]
            addStyle(SpanStyle(fontWeight=FontWeight.Bold,color=Teal),start,end)
            links+=start until end
            if(onOpenLesson!=null)addLink(LinkAnnotation.Clickable(ref.lessonId,linkInteractionListener={onOpenLesson(ref.lessonId)}),start,end)
        }
    }
    return FormattedProse(annotated,matches.map {it.term}.toSet(),placeholders,links)
}

@Composable internal fun AppReferenceText(text:String,modifier:Modifier=Modifier,fontSize:TextUnit=16.sp,
    color:Color=Ink,fontWeight:FontWeight?=null,lineHeight:TextUnit=TextUnit.Unspecified,fontStyle:FontStyle?=null,
    textAlign:TextAlign?=null,maxLines:Int=Int.MAX_VALUE,overflow:TextOverflow=TextOverflow.Clip,
    emphasis:List<String> = emptyList(),references:List<LessonTextReference> = emptyList(),onOpenLesson:((String)->Unit)?=null) {
    val open by rememberUpdatedState(onOpenLesson)
    val prose=remember(text,emphasis,references,onOpenLesson!=null){formattedProse(text,emphasis,references,if(onOpenLesson!=null)({open?.invoke(it)}) else null)}
    val icons=remember(prose.icons){prose.icons.associate {term->term.name to InlineTextContent(Placeholder(1.em,1.em,PlaceholderVerticalAlign.TextCenter)) {
        Icon(term.icon,null,Modifier.fillMaxSize().testTag("app-term-${term.name.lowercase()}"),tint=term.color)
    }}}
    var layout by remember(prose) {mutableStateOf<TextLayoutResult?>(null)}
    Text(prose.text,modifier.semantics {this.text=AnnotatedString(text)}.drawBehind {
        val result=layout ?: return@drawBehind
        for(range in prose.links){
            val first=result.getLineForOffset(range.first);val last=result.getLineForOffset(range.last)
            for(line in first..last){
                val start=maxOf(range.first,result.getLineStart(line));val end=minOf(range.last+1,result.getLineEnd(line,visibleEnd=true))
                if(start>=end)continue
                val left=result.getBoundingBox(start).left;val right=result.getBoundingBox(end-1).right
                val y=result.getLineBaseline(line)+3.dp.toPx()
                drawLine(Teal,Offset(left,y),Offset(right,y),1.2.dp.toPx(),StrokeCap.Round,
                    pathEffect=PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(),3.dp.toPx())))
            }
        }
    },inlineContent=icons,fontSize=fontSize,lineHeight=lineHeight,color=color,fontWeight=fontWeight,fontStyle=fontStyle,
        textAlign=textAlign,maxLines=maxLines,overflow=overflow,onTextLayout={layout=it})
}
