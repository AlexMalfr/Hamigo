package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

data class QuestionPresentation(val label: String, val instruction: String, val color: Color)
fun questionPresentation(kind: String): QuestionPresentation = when(kind) {
    "match" -> QuestionPresentation("Les bonnes connexions", "Relier · touche une carte à gauche, puis son partenaire à droite.", Purple)
    "order", "sort" -> QuestionPresentation("Remets le signal en ordre", "Ordonner · touche les éléments dans le bon ordre.", Purple)
    "number" -> QuestionPresentation("À toi de calculer", "Calculer · saisis le nombre dans l'unité demandée.", Teal)
    "resistor" -> QuestionPresentation("Décode les couleurs", "Lire les anneaux · repère le sens avant de choisir.", Color(0xFFAA6743))
    "flash" -> QuestionPresentation("Flashcard · rappel actif", "Mémoriser · cherche la réponse avant de retourner la carte.", Teal)
    "frequency" -> QuestionPresentation("Accorde la fréquence", "Régler · glisse l'aiguille, puis ajuste avec les boutons.", Teal)
    "truefalse" -> QuestionPresentation("Vrai ou faux ?", "Décider · une affirmation, deux possibilités.", Color(0xFF547FCD))
    "cloze" -> QuestionPresentation("Complète la transmission", "Compléter · touche le mot qui remplit le trou.", Purple)
    "multiselect" -> QuestionPresentation("La chasse aux bons signaux", "Sélection multiple · plusieurs réponses peuvent être justes.", Color(0xFFAA6743))
    "morseListen" -> QuestionPresentation("À l'écoute du morse", "Écouter · lance le son, puis reconnais le message.", Color(0xFF547FCD))
    "morseEncode" -> QuestionPresentation("À toi de transmettre", "Composer · construis le code avec les points et les traits.", Teal)
    "binary" -> QuestionPresentation("Les interrupteurs binaires", "Manipuler · allume ou éteins les bits pour former le nombre.", Purple)
    "waveform" -> QuestionPresentation("Les signaux prennent forme", "Observer · choisis le tracé qui répond à la question.", Color(0xFF547FCD))
    "estimate" -> QuestionPresentation("Vise la bonne valeur", "Estimer · déplace le curseur jusqu'à la valeur demandée.", Color(0xFFAA6743))
    else -> QuestionPresentation("Capte la bonne réponse", "Choisir · une seule réponse est juste.", Teal)
}

@Composable fun QuestionGuide(kind: String, mood: MascotMood, pose: MascotPose, message: String) {
    val type = questionPresentation(kind)
    Surface(color=type.color.copy(alpha=.10f),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start=12.dp,end=8.dp,top=7.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Eyebrow(type.label,type.color)
                Text(type.instruction,color=Ink,fontWeight=FontWeight.SemiBold,fontSize=13.sp,lineHeight=18.sp)
                Text(message,color=Muted,fontSize=11.sp,lineHeight=15.sp)
            }
            Pico(Modifier.size(74.dp),mood=mood,pose=pose)
        }
    }
}

private val linkColors=listOf(Teal,Purple,Color(0xFF547FCD),Color(0xFFAA6743),Color(0xFFAC5377),Color(0xFF548345))
@Composable fun MatchBoard(q: Question, key: String, matches: Map<Int,Int>, left: Int?, enabled: Boolean,
                          onSelect: (Int)->Unit, onConnect: (Int)->Unit) {
    val shuffled=remember(key){q.pairs.indices.shuffled(Random(q.id.hashCode()))}
    val leftCenters=remember(key){mutableStateMapOf<Int,Float>()}
    val rightCenters=remember(key){mutableStateMapOf<Int,Float>()}
    var canvasTop by remember(key){mutableFloatStateOf(0f)}
    val complete=matches.size==q.pairs.size
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text("${matches.size}/${q.pairs.size} liens créés",fontSize=12.sp,fontWeight=FontWeight.Bold,color=Purple)
        Text(if(complete)"Tout est relié !" else if(left!=null)"Choisis à droite →" else "← Choisis à gauche",fontSize=11.sp,color=Muted)
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.Top) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            q.pairs.forEachIndexed{i,pair -> LinkTile(pair.left,i,left==i || i in matches,enabled,if(i in matches)linkColors[i%linkColors.size]else null,
                Modifier.onGloballyPositioned{leftCenters[i]=it.positionInRoot().y+it.size.height/2f}){onSelect(i)} }
        }
        Canvas(Modifier.width(30.dp).fillMaxHeight().onGloballyPositioned{canvasTop=it.positionInRoot().y}) {
            matches.forEach{(source,target)->
                val sourceY=leftCenters[source];val targetY=rightCenters[target]
                if(sourceY!=null&&targetY!=null) {
                    val startY=sourceY-canvasTop;val endY=targetY-canvasTop
                    val path=Path().apply{moveTo(0f,startY);cubicTo(size.width*.45f,startY,size.width*.55f,endY,size.width,endY)}
                    drawPath(path,linkColors[source%linkColors.size].copy(alpha=.7f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                    drawCircle(linkColors[source%linkColors.size],3.dp.toPx(),Offset(2.dp.toPx(),startY))
                    drawCircle(linkColors[source%linkColors.size],3.dp.toPx(),Offset(size.width-2.dp.toPx(),endY))
                }
            }
            left?.let{leftCenters[it]}?.let{center->drawLine(Purple,Offset(0f,center-canvasTop),Offset(size.width*.4f,center-canvasTop),3.dp.toPx(),StrokeCap.Round)}
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            shuffled.forEach{i -> val source=matches.entries.firstOrNull{it.value==i}?.key
                LinkTile(q.pairs[i].right,source,source!=null,enabled&&left!=null,source?.let{linkColors[it%linkColors.size]},
                    Modifier.onGloballyPositioned{rightCenters[i]=it.positionInRoot().y+it.size.height/2f}){onConnect(i)}
            }
        }
    }
}

@Composable private fun LinkTile(text:String,index:Int?,selected:Boolean,enabled:Boolean,color:Color?,modifier:Modifier=Modifier,onClick:()->Unit) {
    val accent=color ?: Purple
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=64.dp),shape=RoundedCornerShape(14.dp),
        color=if(selected)accent.copy(alpha=.12f)else Color.White,border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)accent else Color(0xFFD5DEDA))) {
        Row(Modifier.padding(horizontal=7.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            if(index!=null&&selected)Surface(color=accent,shape=RoundedCornerShape(6.dp)){Text("${index+1}",Modifier.padding(horizontal=5.dp,vertical=3.dp),color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)}
            Text(text,fontSize=13.sp,lineHeight=17.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,color=Ink)
        }
    }
}

@Composable fun MorseSymbols(code:String,modifier:Modifier=Modifier,color:Color=Teal) {
    val normalized=normalizeMorse(code)
    Canvas(modifier.fillMaxWidth().height(58.dp).semantics {contentDescription="Code morse : $normalized"}) {
        val weights=normalized.map{if(it=='.')1f else if(it=='-')3f else if(it=='/')4f else 2f}
        val total=weights.sum()+(normalized.length-1).coerceAtLeast(0)*.75f
        val unit=minOf(17.dp.toPx(),(size.width-16.dp.toPx())/total.coerceAtLeast(1f))
        val start=(size.width-total*unit)/2f
        var x=start
        normalized.forEachIndexed{i,c ->
            val length=weights[i]*unit
            if(c=='.')drawCircle(color,unit*.45f,Offset(x+length/2f,size.height/2f))
            else if(c=='-')drawLine(color,Offset(x+unit*.45f,size.height/2f),Offset(x+length-unit*.45f,size.height/2f),unit*.9f,StrokeCap.Round)
            else if(c=='/')drawLine(Muted.copy(alpha=.5f),Offset(x+length/2f,size.height*.2f),Offset(x+length/2f,size.height*.8f),1.5.dp.toPx())
            x+=length+unit*.75f
        }
    }
}

fun normalizeMorse(code:String):String=code.replace('·','.').replace('•','.').replace('−','-').replace('–','-')
    .filter{it=='.'||it=='-'||it=='/'||it.isWhitespace()}.trim().replace(Regex("\\s+")," ").replace(Regex("\\s*/\\s*")," / ")

@Composable fun MorseComposer(code:String,onChange:(String)->Unit,enabled:Boolean) {
    Panel(color=Mist) {
        if(code.isBlank())Text("Ton message attend son premier point…",fontSize=13.sp,color=Muted)
        else MorseSymbols(code)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({onChange(code+".")},Modifier.weight(1f).height(54.dp),enabled=enabled){Text("●  Point",fontSize=16.sp,fontWeight=FontWeight.Bold)}
            Button({onChange(code+"-")},Modifier.weight(1f).height(54.dp),enabled=enabled){Text("━  Trait",fontSize=16.sp,fontWeight=FontWeight.Bold)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            OutlinedButton({if(code.isNotBlank()&&!code.endsWith(" "))onChange(code+" ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(6.dp)){Text("Lettre suivante",fontSize=11.sp)}
            OutlinedButton({if(code.isNotBlank()&&!code.endsWith("/ "))onChange(code.trimEnd()+" / ")},Modifier.weight(1f),enabled=enabled&&code.isNotBlank(),contentPadding=PaddingValues(6.dp)){Text("Mot suivant",fontSize=11.sp)}
            IconButton({onChange(code.dropLast(1))},enabled=enabled&&code.isNotBlank()){Icon(Icons.Rounded.Backspace,"Effacer le dernier symbole")}
        }
        OutlinedButton({playMorse(code)},enabled=code.isNotBlank(),modifier=Modifier.fillMaxWidth()){Icon(Icons.Rounded.VolumeUp,null);Spacer(Modifier.width(8.dp));Text("Écouter ma transmission")}
    }
}

@Composable fun BinarySwitches(value:Int,bits:Int,enabled:Boolean,onChange:(Int)->Unit) {
    Panel(color=Purple.copy(alpha=.10f)) {
        Text("${bits} bits · de gauche à droite, les poids diminuent",fontSize=12.sp,color=Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.fillMaxWidth()) {
            (bits-1 downTo 0).forEach{bit -> val active=value and (1 shl bit)!=0
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("${1 shl bit}",fontSize=11.sp,color=Muted)
                    Surface(onClick={onChange(value xor (1 shl bit))},enabled=enabled,shape=RoundedCornerShape(10.dp),color=if(active)Purple else Color.White,
                        border=BorderStroke(1.dp,Purple.copy(alpha=.4f)),modifier=Modifier.fillMaxWidth().height(52.dp).semantics {contentDescription="Bit de poids ${1 shl bit} : ${if(active)1 else 0}"}) {
                        Box(contentAlignment=Alignment.Center){Text(if(active)"1"else"0",color=if(active)Color.White else Purple,fontWeight=FontWeight.ExtraBold,fontSize=22.sp)}
                    }
                }
            }
        }
        Text("Lis chaque interrupteur comme un 0 ou un 1.",fontSize=11.sp,color=Muted)
    }
}

@Composable fun WaveformPreview(kind:String,modifier:Modifier=Modifier,color:Color=Teal) {
    Canvas(modifier.fillMaxWidth().height(68.dp).semantics {contentDescription=when(kind){"am"->"Oscillation dont l'amplitude varie";"fm"->"Oscillation dont la fréquence varie";"square"->"Signal carré";"dc"->"Signal constant";"noise"->"Signal irrégulier";else->"Oscillation sinusoïdale"}}) {
        drawLine(Muted.copy(alpha=.18f),Offset(0f,size.height/2f),Offset(size.width,size.height/2f),1.dp.toPx())
        val p=Path()
        repeat(240){i ->
            val t=i/239.0
            val y=when(kind) {
                "am"->sin(t*PI*26)*(.35+.65*(sin(t*PI*2)+1)/2)
                "fm"->sin(t*PI*18+4*sin(t*PI*2))
                "square"->if(sin(t*PI*8)>=0).75 else -.75
                "dc"->.45
                "noise"->sin(t*513)*.35+sin(t*173)*.3+sin(t*61)*.2
                else -> sin(t*PI*8)*.85
            }
            val x=t.toFloat()*size.width;val cy=size.height/2f-y.toFloat()*size.height*.36f
            if(i==0)p.moveTo(x,cy)else p.lineTo(x,cy)
        }
        drawPath(p,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
    }
}
