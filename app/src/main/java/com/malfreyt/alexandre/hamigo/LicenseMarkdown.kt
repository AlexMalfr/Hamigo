package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.ext.gfm.tables.*
import org.commonmark.node.*
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.Paragraph as MarkdownParagraph
import org.commonmark.parser.Parser

private val licenseParser=Parser.builder().extensions(listOf(TablesExtension.create())).build()
private fun Node.children():List<Node> = buildList {var child=firstChild;while(child!=null){add(child);child=child.next}}

/** Repository links to packaged licenses stay offline; other paths point at the shipped commit. */
internal fun licenseLinkTarget(document:String,href:String):String {
    if(href.startsWith("https://")||href.startsWith("http://"))return href
    val path=when(document){"PERIMETERS.md"->"LICENSES/README.md";else->document}
    val parts=mutableListOf<String>()
    (path.substringBeforeLast('/',"")+"/"+href).split('/').forEach {part->
        when(part){"","."->Unit;".."->if(parts.isNotEmpty())parts.removeAt(parts.lastIndex);else->parts.add(part)}
    }
    return when(val resolved=parts.joinToString("/")) {
        "LICENSE"->"LICENSE"
        "LICENSES/README.md"->"PERIMETERS.md"
        "THIRD_PARTY_NOTICES.md"->"THIRD_PARTY_NOTICES.md"
        "LICENSES/Apache-2.0.txt"->"Apache-2.0.txt"
        "LICENSES/CC-BY-NC-SA-4.0.txt"->"CC-BY-NC-SA-4.0.txt"
        "LICENSES/BSD-2-Clause-commonmark.txt"->"BSD-2-Clause-commonmark.txt"
        else->"${HamigoLinks.REPOSITORY}/blob/${BuildConfig.SOURCE_REVISION}/$resolved"
    }
}

private fun inlineLicenseText(node:Node,onLink:(String)->Unit):AnnotatedString=buildAnnotatedString {
    fun appendNode(part:Node) {
        val start=length
        when(part) {
            is MarkdownText->append(part.literal)
            is Code->append(part.literal)
            is SoftLineBreak->append(' ')
            is HardLineBreak->append('\n')
            is HtmlInline->append(part.literal)
            else->part.children().forEach(::appendNode)
        }
        if(length>start)when(part) {
            is StrongEmphasis->addStyle(SpanStyle(fontWeight=FontWeight.Bold),start,length)
            is Emphasis->addStyle(SpanStyle(fontStyle=FontStyle.Italic),start,length)
            is Code->addStyle(SpanStyle(fontFamily=FontFamily.Monospace,background=Mist),start,length)
            is Link->{
                addStyle(SpanStyle(color=Teal,textDecoration=TextDecoration.Underline),start,length)
                addLink(LinkAnnotation.Clickable(part.destination,linkInteractionListener={onLink(part.destination)}),start,length)
            }
        }
    }
    node.children().forEach(::appendNode)
}

@Composable internal fun LicenseMarkdown(source:String,modifier:Modifier=Modifier,onLink:(String)->Unit) {
    val document=remember(source){licenseParser.parse(source)}
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {LicenseBlocks(document.children(),onLink)}
}

@Composable private fun LicenseBlocks(blocks:List<Node>,onLink:(String)->Unit) {
    blocks.forEach {block->when(block) {
        is Heading->Text(inlineLicenseText(block,onLink),Modifier.fillMaxWidth().semantics {heading()},
            fontWeight=FontWeight.Bold,fontSize=if(block.level==1)20.sp else 16.sp,lineHeight=if(block.level==1)26.sp else 22.sp,color=Ink)
        is MarkdownParagraph->Text(inlineLicenseText(block,onLink),fontSize=13.sp,lineHeight=19.sp,color=Ink)
        is TableBlock->{
            val rows=block.children().flatMap {it.children()}
            val header=rows.firstOrNull()?.children().orEmpty()
            if(header.isNotEmpty())Text(header.joinToString(" · "){inlineLicenseText(it,onLink).text},fontSize=12.sp,fontWeight=FontWeight.Bold,color=Muted)
            // Long legal conditions read vertically on a phone instead of in narrow table cells.
            rows.drop(1).forEach {row->Surface(color=Mist.copy(alpha=.55f),shape=RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.children().forEachIndexed {index,cell->Text(inlineLicenseText(cell,onLink),fontSize=13.sp,lineHeight=19.sp,
                        fontWeight=if(index==0)FontWeight.Bold else FontWeight.Normal,color=Ink)}
                }
            }}
        }
        is BulletList,is OrderedList->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            block.children().forEachIndexed {index,item->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(block is OrderedList)"${block.markerStartNumber+index}." else "•",fontSize=13.sp,lineHeight=19.sp,color=Teal)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){LicenseBlocks(item.children(),onLink)}
            }}
        }
        is FencedCodeBlock->Text(block.literal,fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=18.sp)
        is IndentedCodeBlock->Text(block.literal,fontFamily=FontFamily.Monospace,fontSize=12.sp,lineHeight=18.sp)
        is ThematicBreak->HorizontalDivider()
        else->Column(verticalArrangement=Arrangement.spacedBy(12.dp)){LicenseBlocks(block.children(),onLink)}
    }}
}
