package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A drawn test card, not a photograph or a recording of a real SSTV transmission. */
@Composable internal fun SstvLearningVisual(visual:String) {
    val slant=visual=="sstv:slant"
    Column(Modifier.fillMaxWidth().testTag("sstv-comparison"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            for(received in listOf(false,true))Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(if(received)"Réception" else "Référence",fontSize=13.sp,fontWeight=FontWeight.Bold,color=Ink)
                Canvas(Modifier.fillMaxWidth().aspectRatio(4f/3f).clip(RoundedCornerShape(12.dp)).background(Mist)
                    .semantics {contentDescription=if(!received)"Trois bandes verticales droites sur une mire."
                        else if(slant)"Les mêmes bandes penchent progressivement vers la droite, sans zone manquante."
                        else "Les bandes restent droites ; une large zone horizontale grisée remplace le milieu de l’image."}) {
                    val columns=64;val rows=48;val cellW=size.width/columns;val cellH=size.height/rows
                    for(row in 0 until rows)for(column in 0 until columns) {
                        val x=((column.toFloat()/columns-if(received&&slant)row.toFloat()/rows*.24f else 0f)+1f)%1f
                        val y=row.toFloat()/rows
                        val lost=received&&!slant&&y in .42f.. .62f
                        val color=when {
                            lost->if((row*17+column*13)%7<3)Color(0xFF879799) else Color(0xFFB9C4C2)
                            x in .12f.. .29f->Teal
                            x in .41f.. .58f->Coral
                            x in .70f.. .87f->Gold
                            else->Color(0xFF33565B)
                        }
                        drawRect(color,Offset(column*cellW,row*cellH),Size(cellW+1f,cellH+1f))
                    }
                }
            }
        }
    }
}
