package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class ExamArtwork(val original: ImageBitmap, val preview: ImageBitmap)

/** Original assets stay untouched. A derived, cached display bitmap removes the paper colour. */
object ExamImageProcessor {
    fun preview(original: Bitmap): Bitmap {
        val width=original.width; val height=original.height
        val pixels=IntArray(width*height)
        original.getPixels(pixels,0,width,0,0,width,height)
        val paper=paperColour(pixels,width,height)
        val output=pixels.copyOf()
        var left=width; var top=height; var right=-1; var bottom=-1
        output.indices.forEach { i ->
            val value=if(paper!=null) removePaper(output[i],paper) else output[i]
            output[i]=value
        }
        if(paper!=null)removeIsolatedSpecks(output,width,height)
        output.indices.forEach {i ->
            val value=output[i]
            if(android.graphics.Color.alpha(value)>20 && (paper!=null || !near(value,output[0],8))) {
                val x=i%width; val y=i/width
                left=minOf(left,x); right=maxOf(right,x); top=minOf(top,y); bottom=maxOf(bottom,y)
            }
        }
        val transparent=Bitmap.createBitmap(output,width,height,Bitmap.Config.ARGB_8888)
        if(right<left || bottom<top) return transparent
        // Fixed gutters in source pixels replace irregular blank areas without trimming ink.
        val gutter=16
        left=(left-gutter).coerceAtLeast(0); top=(top-gutter).coerceAtLeast(0)
        right=(right+gutter).coerceAtMost(width-1); bottom=(bottom+gutter).coerceAtMost(height-1)
        if(left==0 && top==0 && right==width-1 && bottom==height-1) return transparent
        val cropped=Bitmap.createBitmap(transparent,left,top,right-left+1,bottom-top+1)
        if(cropped!==transparent) transparent.recycle()
        return cropped
    }

    /** Only 1–9 neutral pixels, at most 3×3, with no other ink within 12 source pixels.
     * Punctuation, dotted lines and circuit junctions remain attached to their nearby ink. */
    private fun removeIsolatedSpecks(pixels:IntArray,width:Int,height:Int) {
        val visited=BooleanArray(pixels.size);val queue=IntArray(pixels.size)
        fun ink(index:Int)=android.graphics.Color.alpha(pixels[index])>20
        for(seed in pixels.indices) {
            if(visited[seed]||!ink(seed))continue
            var count=1;var cursor=0;queue[0]=seed;visited[seed]=true
            var left=seed%width;var right=left;var top=seed/width;var bottom=top
            while(cursor<count) {
                val at=queue[cursor++];val x=at%width;val y=at/width
                left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)
                for(ny in maxOf(0,y-1)..minOf(height-1,y+1))for(nx in maxOf(0,x-1)..minOf(width-1,x+1)) {
                    val next=ny*width+nx
                    if(!visited[next]&&ink(next)){visited[next]=true;queue[count++]=next}
                }
            }
            if(count>9||right-left>2||bottom-top>2)continue
            val dots=(0 until count).map {queue[it]}
            if(dots.any {val rgb=listOf(android.graphics.Color.red(pixels[it]),android.graphics.Color.green(pixels[it]),android.graphics.Color.blue(pixels[it]));rgb.max()-rgb.min()>15})continue
            var nearby=false
            for(y in maxOf(0,top-12)..minOf(height-1,bottom+12))for(x in maxOf(0,left-12)..minOf(width-1,right+12)) {
                val at=y*width+x
                if(at !in dots && ink(at))nearby=true
            }
            if(!nearby)dots.forEach {pixels[it]=android.graphics.Color.TRANSPARENT}
        }
    }

    private fun paperColour(pixels:IntArray,width:Int,height:Int):Int? {
        val samples=ArrayList<Int>()
        for(x in 0 until width step max(1,width/40)) {samples+=pixels[x];samples+=pixels[(height-1)*width+x]}
        for(y in 0 until height step max(1,height/20)) {samples+=pixels[y*width];samples+=pixels[y*width+width-1]}
        val value=samples.groupingBy {it}.eachCount().maxByOrNull {it.value}?.key ?: return null
        val r=android.graphics.Color.red(value); val g=android.graphics.Color.green(value); val b=android.graphics.Color.blue(value)
        return value.takeIf {r>=230 && g>=225 && b in 140..239 && abs(r-g)<35}
    }

    private fun near(a:Int,b:Int,tolerance:Int)=abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))<=tolerance &&
        abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))<=tolerance &&
        abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))<=tolerance

    /** Reverse matte compositing to preserve crisp antialiased text and coloured circuit elements. */
    private fun removePaper(pixel:Int,paper:Int):Int {
        // PNG palette rounding/dithering can vary the same pale paper by a few levels.
        // Test it before inverse compositing: a +1 on a nearly saturated channel
        // (e.g. green 254 -> 255) otherwise gives alpha=1 and falsely defines the crop.
        if(near(pixel,paper,6)) return android.graphics.Color.TRANSPARENT
        val channels=intArrayOf(android.graphics.Color.red(pixel),android.graphics.Color.green(pixel),android.graphics.Color.blue(pixel))
        val background=intArrayOf(android.graphics.Color.red(paper),android.graphics.Color.green(paper),android.graphics.Color.blue(paper))
        var alpha=0.0
        channels.indices.forEach { i ->
            val difference=channels[i]-background[i]
            val denominator=if(difference>0) 255-background[i] else background[i]
            if(denominator>0) alpha=max(alpha,abs(difference).toDouble()/denominator)
        }
        if(alpha<=.015) return android.graphics.Color.TRANSPARENT
        alpha=alpha.coerceIn(0.0,1.0)
        val rgb=channels.indices.map {i->((channels[i]-background[i]*(1-alpha))/alpha).roundToInt().coerceIn(0,255)}
        val a=(alpha*android.graphics.Color.alpha(pixel)).roundToInt().coerceIn(0,255)
        return android.graphics.Color.argb(a,rgb[0],rgb[1],rgb[2])
    }
}

@Composable fun rememberExamArtwork(path:String?):ExamArtwork? {
    val context=LocalContext.current
    val result by produceState<ExamArtwork?>(null,path) {
        value=withContext(Dispatchers.Default) {
            path?.let {runCatching {
                val original=context.assets.open(it).use(BitmapFactory::decodeStream)
                requireNotNull(original)
                ExamArtwork(original.asImageBitmap(),ExamImageProcessor.preview(original).asImageBitmap())
            }.getOrNull()}
        }
    }
    return result
}

@Composable fun ExamIllustration(artwork:ExamArtwork,onEnlarge:()->Unit,modifier:Modifier=Modifier) {
    Surface(onClick=feedbackClick(onEnlarge),color=Color.Transparent,shape=RoundedCornerShape(12.dp),modifier=modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Image(artwork.preview,"Question illustrée Exam1",Modifier.fillMaxWidth().aspectRatio(artwork.preview.width.toFloat()/artwork.preview.height))
            Text("Toucher pour agrandir",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
        }
    }
}

/** Original image above the current page; pinch/pan, double-tap reset, tap outside or back closes. */
@Composable fun FullscreenExamIllustration(original:ImageBitmap,onDismiss:()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,dismissOnClickOutside=false)) {
        val view=LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {window->
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }
        val state=remember(original){ExamImageViewport()}
        val dismiss by rememberUpdatedState(onDismiss)
        val padding=with(LocalDensity.current){24.dp.toPx()}
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.64f)).testTag("exam-image-overlay")
            .pointerInput(original,padding) {
                var lastTap=0L;var lastPoint=Offset.Zero
                awaitEachGesture {
                    val first=awaitFirstDown(requireUnconsumed=false);first.consume()
                    val viewport=Size(size.width.toFloat(),size.height.toFloat())
                    val image=fitExamImage(Size(original.width.toFloat(),original.height.toFloat()),viewport,padding)
                    val beganInside=state.bounds(viewport,image).contains(first.position)
                    var fingers=1;var moved=false;var lastPosition=first.position
                    do {
                        val event=awaitPointerEvent()
                        fingers=max(fingers,event.changes.count {it.pressed})
                        val pan=event.calculatePan();val change=event.calculateZoom()
                        val position=event.changes.firstOrNull {it.id==first.id}?.position ?: lastPosition
                        if((position-first.position).getDistance()>viewConfiguration.touchSlop)moved=true
                        lastPosition=position
                        if(fingers>1||moved) {
                            val centroid=event.calculateCentroid(useCurrent=false)
                            if(centroid!=Offset.Unspecified)state.transform(centroid,pan,change,viewport,image)
                        }
                        event.changes.forEach {it.consume()}
                    } while(event.changes.any {it.pressed})
                    if(fingers==1&&!moved) {
                        if(!beganInside&&!state.bounds(viewport,image).contains(lastPosition))dismiss()
                        else {
                            val now=first.uptimeMillis
                            if(lastTap>0&&now-lastTap<=viewConfiguration.doubleTapTimeoutMillis&&(first.position-lastPoint).getDistance()<viewConfiguration.touchSlop*3) {
                                state.doubleTap(first.position,viewport,image);lastTap=0
                            } else {lastTap=now;lastPoint=first.position}
                        }
                    } else lastTap=0
                }
            },contentAlignment=Alignment.Center) {
            val fitted=fitExamImage(Size(original.width.toFloat(),original.height.toFloat()),Size(constraints.maxWidth.toFloat(),constraints.maxHeight.toFloat()),padding)
            val density=LocalDensity.current
            Image(original,"Illustration originale : pincer pour zoomer",contentScale=ContentScale.Fit,
                modifier=Modifier.width(with(density){fitted.width.toDp()}).height(with(density){fitted.height.toDp()})
                    .testTag("exam-image-original").semantics {stateDescription="Zoom ${(state.zoom*100).roundToInt()} %"}
                    .graphicsLayer {scaleX=state.zoom;scaleY=state.zoom;translationX=state.offset.x;translationY=state.offset.y})
        }
    }
}
