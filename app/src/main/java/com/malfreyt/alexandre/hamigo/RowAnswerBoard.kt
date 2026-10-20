package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

internal object RowAnswers {
    fun correct(row:AnswerRow,value:String)=if(row.choices.isEmpty())WrittenAnswers.correct(value,row.acceptedAnswers)
        else value==row.choices.getOrNull(row.answer)
    fun complete(rows:List<AnswerRow>,values:Map<String,String>)=rows.isNotEmpty()&&rows.all {values[it.id]?.isNotBlank()==true}
    fun correct(rows:List<AnswerRow>,values:Map<String,String>)=complete(rows,values)&&rows.all {correct(it,values[it.id].orEmpty())}
    fun solution(row:AnswerRow)=if(row.choices.isEmpty())row.acceptedAnswers.firstOrNull().orEmpty() else row.choices.getOrNull(row.answer).orEmpty()
}

/** Every row recalls its own answer; a choice in one row never supplies the other answers. */
@Composable internal fun RowAnswerBoard(q:Question,values:Map<String,String>,enabled:Boolean,feedback:Boolean?,onChange:(Map<String,String>)->Unit) {
    val density=LocalDensity.current
    val ime=WindowInsets.ime.getBottom(density)>0
    val horizontal=questionToolsHorizontal()
    q.rows.forEach {row->
        key(q.id,row.id) {
            val value=values[row.id].orEmpty()
            val correct=RowAnswers.correct(row,value)
            var expanded by remember {mutableStateOf(false)}
            var focused by remember {mutableStateOf(false)}
            var size by remember {mutableStateOf(IntSize.Zero)}
            val visibility=remember {BringIntoViewRequester()}
            val choices=remember(q.presentationSeed,row.id) {row.choices.shuffled(Random(q.presentationSeed xor row.id.hashCode()))}
            LaunchedEffect(ime,focused,size,horizontal) {
                if(ime&&focused&&size.height>0){delay(160);visibility.bringIntoView(Rect(0f,0f,size.width.toFloat(),size.height+with(density){(if(horizontal)96.dp else 160.dp).toPx()}))}
            }
            Row(Modifier.fillMaxWidth().testTag("answer-row-${row.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.weight(.36f),shape=RoundedCornerShape(14.dp),color=Mist,
                    border=BorderStroke(1.dp,Teal.copy(alpha=.16f))) {
                    MorseAwareText(row.prompt,Modifier.padding(12.dp),fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold)
                }
                Column(Modifier.weight(.64f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(row.choices.isEmpty())OutlinedTextField(value,{if(enabled)onChange(values+(row.id to it))},
                        enabled=enabled,singleLine=true,label={Text("Réponse")},
                        isError=feedback!=null&&!correct,
                        modifier=Modifier.fillMaxWidth().testTag("answer-row-input-${row.id}").bringIntoViewRequester(visibility)
                            .onSizeChanged {size=it}.onFocusChanged {focused=it.isFocused})
                    else Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(feedbackClick {expanded=true},enabled=enabled,
                            modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("answer-row-select-${row.id}"),
                            shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp),
                            border=BorderStroke(if(feedback!=null)2.dp else 1.dp,if(feedback==null)Teal else if(correct)Teal else Coral)) {
                            Text(value.ifBlank {"Choisir"},Modifier.weight(1f),fontSize=14.sp)
                            Icon(Icons.Rounded.ArrowDropDown,null)
                        }
                        DropdownMenu(expanded&&enabled,{expanded=false}) {
                            choices.forEach {option->DropdownMenuItem(text={MorseAwareText(option,fontSize=15.sp)},
                                onClick=feedbackClick {onChange(values+(row.id to option));expanded=false})}
                        }
                    }
                    if(feedback!=null)Text(if(correct)"Correct" else "Réponse : ${RowAnswers.solution(row)}",fontSize=12.sp,lineHeight=17.sp,color=if(correct)Teal else Coral)
                }
            }
        }
    }
}
