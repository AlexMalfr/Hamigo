package com.malfreyt.alexandre.hamigo

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink=Color(0xFF173F42)
val Teal=Color(0xFF087F82)
val Cream=Color(0xFFFAF8F2)
val Coral=Color(0xFFFF7869)
val Gold=Color(0xFFFFC66F)
val Mist=Color(0xFFE1F2EF)
val Muted=Color(0xFF597678)
val Purple=Color(0xFF8B77C5)
val ChapterColors=listOf(Teal,Color(0xFF547FCD),Purple,Color(0xFFD47648))

@Composable fun HamigoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Teal,onPrimary=Color.White,primaryContainer=Mist,
        secondary=Coral,secondaryContainer=Color(0xFFFFE8E0),background=Cream,surface=Cream,
        onBackground=Ink,onSurface=Ink,outline=Color(0xFFD4DEDA)),
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}
@Composable fun Pico(modifier: Modifier=Modifier, happy: Boolean=true, animate: Boolean=true) {
    val transition=rememberInfiniteTransition(label="radio")
    val wave by transition.animateFloat(.25f,1f,infiniteRepeatable(tween(1300),RepeatMode.Reverse),label="waves")
    Canvas(modifier.semantics {contentDescription="Pico, la mascotte radio"}) {
        val u=size.width/120f
        val top=(size.height-120*u)/2
        fun pos(x:Float,y:Float)=Offset(x*u,top+y*u)
        fun sz(x:Float,y:Float)=Size(x*u,y*u)
        drawOval(Ink.copy(alpha=.08f),pos(15f,106f),sz(90f,10f))
        drawLine(Ink,pos(34f,90f),pos(28f,106f),6*u,StrokeCap.Round)
        drawLine(Ink,pos(85f,90f),pos(91f,106f),6*u,StrokeCap.Round)
        drawLine(Coral,pos(20f,65f),pos(7f,52f),8*u,StrokeCap.Round)
        drawLine(Coral,pos(99f,65f),pos(112f,50f),8*u,StrokeCap.Round)
        drawLine(Gold,pos(60f,18f),pos(60f,40f),6*u,StrokeCap.Round)
        drawCircle(Coral,5*u,pos(60f,18f))
        drawArc(Teal.copy(alpha=if(animate) wave else 1f),210f,120f,false,pos(37f,-5f),sz(46f,40f),style=Stroke(3*u,cap=StrokeCap.Round))
        drawArc(Teal.copy(alpha=if(animate) 1-wave+.25f else .7f),210f,120f,false,pos(45f,4f),sz(30f,28f),style=Stroke(3*u,cap=StrokeCap.Round))
        drawRoundRect(Teal,pos(19f,38f),sz(82f,61f),CornerRadius(17*u))
        drawRoundRect(Color(0xFFFFECCC),pos(19f,34f),sz(82f,61f),CornerRadius(17*u))
        drawRoundRect(Color.White.copy(alpha=.55f),pos(26f,41f),sz(43f,39f),CornerRadius(10*u))
        drawCircle(Ink,3*u,pos(38f,55f));drawCircle(Ink,3*u,pos(57f,55f))
        if(happy) drawArc(Ink,0f,180f,false,pos(38f,59f),sz(19f,12f),style=Stroke(3*u,cap=StrokeCap.Round))
        else drawLine(Ink,pos(40f,69f),pos(55f,69f),3*u,StrokeCap.Round)
        drawCircle(Coral,9*u,pos(83f,56f));drawCircle(Color.White.copy(alpha=.4f),3*u,pos(80f,53f))
        repeat(3) { drawLine(Ink.copy(alpha=.5f),pos(76f,74f+it*5),pos(89f,74f+it*5),2*u,StrokeCap.Round) }
        drawCircle(Coral.copy(alpha=.5f),3*u,pos(31f,65f));drawCircle(Coral.copy(alpha=.5f),3*u,pos(63f,65f))
    }
}
@Composable fun BigTitle(title: String, subtitle: String="") {
    Text(title,fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=Ink,lineHeight=32.sp)
    if(subtitle.isNotBlank()) {Spacer(Modifier.height(7.dp));Text(subtitle,color=Muted,fontSize=14.sp,lineHeight=21.sp)}
}
@Composable fun Panel(modifier: Modifier=Modifier,color: Color=Color.White,content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=color,tonalElevation=0.dp) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun Action(label: String,modifier: Modifier=Modifier,enabled: Boolean=true,onClick:()->Unit) {
    Button(onClick,modifier.fillMaxWidth().heightIn(min=54.dp),enabled=enabled,shape=RoundedCornerShape(17.dp),
        elevation=ButtonDefaults.buttonElevation(defaultElevation=3.dp),contentPadding=PaddingValues(16.dp)) {
        Text(label,fontWeight=FontWeight.Bold,fontSize=16.sp)
    }
}
@Composable fun Eyebrow(text: String,color: Color=Teal) {Text(text.uppercase(),fontSize=11.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.5.sp,color=color)}
