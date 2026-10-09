package com.malfreyt.alexandre.hamigo

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Same soft edge as the navigation bar, cast above the stationary question footer. */
internal fun Modifier.questionFooterShadow()=drawBehind {
    for(spread in HamigoEdgeShadowLayers downTo 1) {
        drawRect(Color.Black.copy(alpha=HamigoEdgeShadowAlpha),Offset(0f,-spread.dp.toPx()),Size(size.width,spread.dp.toPx()))
    }
}
