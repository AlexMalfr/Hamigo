package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/** Reviews cannot promise unseen questions; retain every usual shortcut that the bank supports. */
internal fun questionCountOptions(availableCount:Int?=null):List<Int> {
    val presets=listOf(10,20,40,80,150)
    if(availableCount==null)return presets
    if(availableCount<=0)return emptyList()
    val fitting=presets.filter {it<=availableCount}
    return if(availableCount in fitting)fitting else fitting+availableCount
}

/** One set of compact choices for mixes and reviews, measured at the current font size. */
@Composable
fun QuestionCountSelector(
    count: Int,
    customSelected: Boolean,
    onCountSelected: (Int, Boolean) -> Unit,
    testTag: String,
    customButtonTag: String,
    availableCount: Int?=null,
) {
    var customOpen by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(count.toString()) }
    val options=questionCountOptions(availableCount)
    val customMaximum=minOf(1000,availableCount?:1000)
    Layout(modifier=Modifier.fillMaxWidth().testTag(testTag),content={
        Text("Questions :",fontSize=12.sp,color=Muted,modifier=Modifier.testTag("$testTag-label"))
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            options.forEach { n ->
                val active=count==n&&!customSelected
                QuestionCountChoice(active,{onCountSelected(n,false)},Modifier.testTag("$testTag-$n")) {
                    Box(Modifier.padding(horizontal=8.dp),contentAlignment=Alignment.Center) {
                        Text("$n",fontSize=13.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,color=Ink)
                    }
                }
            }
            QuestionCountChoice(customSelected,{custom=count.toString();customOpen=true},Modifier.testTag(customButtonTag)) {
                Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.Edit,"Choisir un nombre personnalisé",modifier=Modifier.size(18.dp),tint=if(customSelected)Teal else Purple)
                    if(customSelected)Text("$count",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Teal)
                }
            }
        }
    }) { measurables,constraints ->
        val children=measurables.map {it.measure(constraints.copy(minWidth=0,minHeight=0))}
        val label=children.first()
        val choices=children.drop(1)
        val gap=4.dp.roundToPx()
        val labelGap=8.dp.roundToPx()
        val choiceWidth=choices.sumOf {it.width}+gap*(choices.size-1)
        val preferredWidth=label.width+labelGap+choiceWidth
        val width=if(constraints.hasBoundedWidth)constraints.maxWidth else preferredWidth
        val inline=preferredWidth<=width
        val positions=mutableListOf<Pair<Int,Int>>()
        var height: Int
        if(inline) {
            height=children.maxOf {it.height}
            positions+=0 to (height-label.height)/2
            var x=label.width+labelGap
            choices.forEach {choice ->positions+=x to (height-choice.height)/2;x+=choice.width+gap}
        } else {
            positions+=0 to 0
            val rows=mutableListOf<List<Int>>()
            var row=mutableListOf<Int>()
            var rowWidth=0
            choices.forEachIndexed {i,choice ->
                if(row.isNotEmpty()&&rowWidth+gap+choice.width>width) {
                    rows+=row;row=mutableListOf();rowWidth=0
                }
                rowWidth+=choice.width+if(row.isEmpty())0 else gap
                row+=i
            }
            if(row.isNotEmpty())rows+=row
            var y=label.height+gap
            rows.forEach {indices ->
                val rowHeight=indices.maxOf {choices[it].height}
                val freeSpace=width-indices.sumOf {choices[it].width}
                var usedWidth=0
                indices.forEachIndexed {i,index ->
                    val choice=choices[index]
                    val x=if(indices.size==1)freeSpace/2 else usedWidth+(freeSpace.toLong()*i/(indices.size-1)).toInt()
                    positions+=x to (y+(rowHeight-choice.height)/2)
                    usedWidth+=choice.width
                }
                y+=rowHeight+gap
            }
            height=y-gap
        }
        layout(constraints.constrainWidth(width),constraints.constrainHeight(height)) {
            children.forEachIndexed {i,child ->child.placeRelative(positions[i].first,positions[i].second)}
        }
    }
    if(customOpen) AlertDialog(
        onDismissRequest={customOpen=false},title={Text("Combien de questions ?")},
        text={OutlinedTextField(custom,{custom=it.filter(Char::isDigit).take(4)},singleLine=true,
            label={Text(if(customMaximum==1000)"De 1 à 1 000" else "De 1 à $customMaximum")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
            modifier=Modifier.fillMaxWidth().testTag("$testTag-input"))},
        confirmButton={TextButton({onCountSelected(custom.toInt(),true);customOpen=false},enabled=custom.toIntOrNull() in 1..customMaximum) {Text("Choisir")}},
        dismissButton={TextButton({customOpen=false}) {Text("Annuler")}},
    )
}

/** Forty-dp touch area around a chip whose painted body is only thirty-two dp high. */
@Composable
private fun QuestionCountChoice(active:Boolean,onClick:()->Unit,modifier:Modifier,content:@Composable ()->Unit) {
    Surface(onClick=onClick,modifier=modifier.height(40.dp).widthIn(min=40.dp).semantics {selected=active},
        color=Color.Transparent,shape=RoundedCornerShape(10.dp)) {
        Surface(modifier=Modifier.padding(vertical=4.dp),color=if(active)Mist else Cream,shape=RoundedCornerShape(10.dp),
            border=BorderStroke(1.dp,if(active)Teal else Color(0xFFD4DEDA)),content=content)
    }
}
