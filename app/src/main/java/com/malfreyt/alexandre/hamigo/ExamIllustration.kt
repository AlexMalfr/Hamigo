package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
        if(near(pixel,paper,2)) return android.graphics.Color.TRANSPARENT
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
    Surface(onClick=onEnlarge,color=Color.Transparent,shape=RoundedCornerShape(12.dp),modifier=modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Image(artwork.preview,"Question illustrée Exam1",Modifier.fillMaxWidth().aspectRatio(artwork.preview.width.toFloat()/artwork.preview.height))
            Text("Toucher pour agrandir",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
        }
    }
}

/** Image only: pinch and pan, double-tap to reset; Android back dismisses the full-screen view. */
@Composable fun FullscreenExamIllustration(original:ImageBitmap,onDismiss:()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        var zoom by remember {mutableFloatStateOf(1f)}
        var offset by remember {mutableStateOf(Offset.Zero)}
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(original,"Illustration originale : pincer pour zoomer",contentScale=ContentScale.Fit,
                modifier=Modifier.fillMaxSize()
                    .pointerInput(original) {detectTransformGestures {centroid,pan,change,_ ->
                        val old=zoom; val next=(old*change).coerceIn(1f,8f)
                        val center=Offset(size.width/2f,size.height/2f)
                        val focal=centroid-center
                        val nextOffset=(offset-focal)*(next/old)+focal+pan
                        zoom=next
                        offset=Offset(nextOffset.x.coerceIn(-size.width*(next-1)/2f,size.width*(next-1)/2f),
                            nextOffset.y.coerceIn(-size.height*(next-1)/2f,size.height*(next-1)/2f))
                    }}
                    .pointerInput(original) {detectTapGestures(onDoubleTap={zoom=1f;offset=Offset.Zero})}
                    .graphicsLayer {scaleX=zoom;scaleY=zoom;translationX=offset.x;translationY=offset.y})
        }
    }
}
