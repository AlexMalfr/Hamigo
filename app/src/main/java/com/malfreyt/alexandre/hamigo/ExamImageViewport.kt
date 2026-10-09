package com.malfreyt.alexandre.hamigo

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

/** All gestures are measured in the fixed screen plane, never in the transformed image plane. */
internal class ExamImageViewport {
    var zoom by mutableFloatStateOf(1f);private set
    var offset by mutableStateOf(Offset.Zero);private set
    fun reset(){zoom=1f;offset=Offset.Zero}
    fun transform(centroid:Offset,pan:Offset,factor:Float,viewport:Size,image:Size) {
        val old=zoom;val next=(old*factor).coerceIn(1f,8f)
        val focal=centroid-Offset(viewport.width/2,viewport.height/2)
        val moved=(offset-focal)*(next/old)+focal+pan
        zoom=next
        val x=max(0f,(image.width*next-viewport.width)/2)
        val y=max(0f,(image.height*next-viewport.height)/2)
        offset=Offset(moved.x.coerceIn(-x,x),moved.y.coerceIn(-y,y))
    }
    fun bounds(viewport:Size,image:Size):Rect {
        val center=Offset(viewport.width/2,viewport.height/2)+offset
        return Rect(center-Offset(image.width*zoom/2,image.height*zoom/2),center+Offset(image.width*zoom/2,image.height*zoom/2))
    }
    fun doubleTap(point:Offset,viewport:Size,image:Size) {
        if(zoom>1.01f)reset() else transform(point,Offset.Zero,2.5f,viewport,image)
    }
}

internal fun fitExamImage(original:Size,viewport:Size,padding:Float):Size {
    val scale=min(max(1f,viewport.width-padding)/original.width,max(1f,viewport.height-padding*3.33f)/original.height)
    return Size(original.width*scale,original.height*scale)
}
