package com.malfreyt.alexandre.hamigo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.asin
import kotlin.math.sqrt

/** One continuous lower edge follows the header and the avatar's exposed circular cap. */
@Composable internal fun Modifier.profileHeaderShadow(state: LazyListState, surfaceHeight: Float, avatar: Rect?): Modifier {
    val strength by animateFloatAsState(if(stickyHeaderDetached(state))1f else 0f,tween(150),label="profile-header-shadow")
    return drawBehind {
        val gutter=16.dp.toPx()
        val bottom=if(surfaceHeight>0f)surfaceHeight else (size.height-8.dp.toPx()).coerceAtLeast(0f)
        val center=avatar?.center
        val radius=avatar?.width?.div(2f) ?: 0f
        val dy=if(center!=null)bottom-center.y else radius
        val hasCap=center!=null && radius>0f && dy in 0f..<radius
        val dx=if(hasCap)sqrt(radius*radius-dy*dy) else 0f
        val angle=if(hasCap)Math.toDegrees(asin(dy/radius).toDouble()).toFloat() else 0f
        val edge=Path().apply {
            moveTo(-gutter,bottom)
            if(hasCap && center!=null && avatar!=null) {
                lineTo(center.x-dx,bottom)
                arcTo(avatar,180f-angle,2f*angle-180f,false)
            }
            lineTo(size.width+gutter,bottom)
        }
        if(strength>0f) for(spread in HamigoEdgeShadowLayers downTo 1) {
            drawPath(edge,Color.Black.copy(alpha=HamigoEdgeShadowAlpha*strength),style=Stroke(spread*2.dp.toPx()))
        }
        // Cover the inner half of the shadow with the actual silhouette, never a rectangular strip.
        val surface=Path().apply {
            moveTo(-gutter,0f);lineTo(size.width+gutter,0f);lineTo(size.width+gutter,bottom)
            if(hasCap && center!=null && avatar!=null) {
                lineTo(center.x+dx,bottom)
                arcTo(avatar,angle,180f-2f*angle,false)
            }
            lineTo(-gutter,bottom);close()
        }
        drawPath(surface,Cream)
    }
}
