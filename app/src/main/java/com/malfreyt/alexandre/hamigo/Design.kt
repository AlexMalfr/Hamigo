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
import androidx.compose.ui.graphics.nativeCanvas
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
    MaterialTheme(colorScheme=lightColorScheme(primary=Teal,onPrimary=Color.White,primaryContainer=Mist,onPrimaryContainer=Ink,
        secondary=Coral,onSecondary=Ink,secondaryContainer=Color(0xFFFFE8E0),onSecondaryContainer=Ink,background=Cream,surface=Cream,
        onBackground=Ink,onSurface=Ink,onSurfaceVariant=Muted,surfaceVariant=Color(0xFFF2F0E8),surfaceContainerHigh=Color(0xFFFFFCF6),outline=Color(0xFFD4DEDA)),
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}
@Composable fun Pico(modifier: Modifier=Modifier, happy: Boolean=true, animate: Boolean=true,
                     mood: MascotMood=if(happy)MascotMood.HAPPY else MascotMood.THINKING,
                     pose: MascotPose=MascotPose.WAVE) {
    val transition=rememberInfiniteTransition(label="radio")
    val wave by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1700),RepeatMode.Restart),label="waves")
    Canvas(modifier.semantics {contentDescription="Pico, la mascotte radio. ${mood.description}"}) {
        PicoRenderer.draw(drawContext.canvas.nativeCanvas,android.graphics.RectF(0f,0f,size.width,size.height),mood,pose,if(animate)wave else .25f)
    }
}
@Composable fun BigTitle(title: String, subtitle: String="") {
    Text(title,fontSize=26.sp,fontWeight=FontWeight.ExtraBold,color=Ink,lineHeight=30.sp)
    if(subtitle.isNotBlank()) {Spacer(Modifier.height(5.dp));Text(subtitle,color=Muted,fontSize=14.sp,lineHeight=20.sp)}
}
@Composable fun Panel(modifier: Modifier=Modifier,color: Color=Color.White,content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=color,tonalElevation=0.dp) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable fun Action(label: String,modifier: Modifier=Modifier,enabled: Boolean=true,onClick:()->Unit) {
    Button(feedbackClick(onClick),modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(15.dp),
        elevation=ButtonDefaults.buttonElevation(defaultElevation=2.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)) {
        Text(label,fontWeight=FontWeight.Bold,fontSize=16.sp)
    }
}
@Composable fun Eyebrow(text: String,color: Color=Teal) {Text(text.uppercase(),fontSize=11.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.5.sp,color=color)}
