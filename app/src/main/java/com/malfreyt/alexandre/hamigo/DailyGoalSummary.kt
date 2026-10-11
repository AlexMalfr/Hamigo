package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun DailyGoalSummary(xp:Int,goal:Int,modifier:Modifier=Modifier,onEdit:(()->Unit)?=null) {
    val reached=xp>=goal;val fraction=(xp.toFloat()/goal.coerceAtLeast(1)).coerceIn(0f,1f)
    Panel(modifier.testTag("daily-goal-summary"),color=if(reached)Color(0xFFFFF4D7) else Mist.copy(alpha=.6f)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(68.dp).clearAndSetSemantics {contentDescription="Objectif du jour : $xp sur $goal XP${if(reached)", atteint" else ""}"},contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    val stroke=Stroke(6.dp.toPx(),cap=StrokeCap.Round)
                    drawCircle(Color.White,style=stroke)
                    drawArc(if(reached)Brush.linearGradient(listOf(Color(0xFFDDB24C),Color(0xFFFFE49A),Color(0xFFFFD166))) else Brush.linearGradient(listOf(Teal,Teal.copy(alpha=.75f))),-90f,360f*fraction,false,style=stroke)
                }
                if(reached)Icon(Icons.Rounded.Check,null,tint=Color(0xFF966517),modifier=Modifier.size(29.dp))
                else Text("${(fraction*100).toInt()} %",fontWeight=FontWeight.ExtraBold,color=Teal,fontSize=17.sp)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Objectif du jour",Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Ink,fontSize=16.sp)
                    if(onEdit!=null)IconButton(feedbackClick(onEdit),Modifier.size(32.dp).testTag("edit-daily-goal")) {
                        Icon(Icons.Rounded.Edit,"Modifier l’objectif quotidien",Modifier.size(18.dp),tint=Teal)
                    }
                }
                Text("$xp / $goal XP",fontWeight=FontWeight.ExtraBold,color=if(reached)Color(0xFF966517) else Teal,fontSize=23.sp,lineHeight=27.sp)
                Text(if(reached)"Objectif atteint !" else "Encore ${(goal-xp).coerceAtLeast(0)} XP",color=Muted,fontSize=13.sp)
            }
        }
    }
}
