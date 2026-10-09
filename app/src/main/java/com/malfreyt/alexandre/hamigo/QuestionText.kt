package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp

internal val LocalQuestionTextHeight=compositionLocalOf {Dp.Unspecified}

/** Large short questions, progressively smaller long ones. Never shrink below readable body size. */
@Composable internal fun questionTextSize(text:AnnotatedString,width:Int,
    placeholders:List<AnnotatedString.Range<Placeholder>> = emptyList()):TextUnit {
    val measurer=rememberTextMeasurer()
    val baseStyle=LocalTextStyle.current
    val height=LocalQuestionTextHeight.current
    val density=LocalDensity.current
    val heightLimit=if(height.value.isFinite())with(density){height.roundToPx()} else Int.MAX_VALUE
    return remember(text,width,placeholders,measurer,baseStyle,heightLimit) {
        (24 downTo 16).firstOrNull {font ->
            val layout=measurer.measure(text,style=baseStyle.copy(fontSize=font.sp,lineHeight=(font*1.32f).sp,fontWeight=FontWeight.ExtraBold),
                placeholders=placeholders,constraints=Constraints(maxWidth=width.coerceAtLeast(1)))
            layout.lineCount<=4 && layout.size.height<=heightLimit
        }?.sp ?: 16.sp
    }
}

@Composable internal fun QuestionText(text:String,towardsLeft:Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val font=questionTextSize(AnnotatedString(text),constraints.maxWidth)
        FormulaAwareQuestion(text,Modifier.fillMaxWidth(),fontSize=font,towardsLeft=towardsLeft)
    }
}
