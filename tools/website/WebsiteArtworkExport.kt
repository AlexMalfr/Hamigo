package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File
import kotlin.math.cos

/** Artifact export, run only on the isolated emulator, then moved outside test sources. */
class WebsiteArtworkExport {
    @Test fun export() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"pico-website-frames").apply {mkdirs()}
        repeat(32) {frame->
            val image=Bitmap.createBitmap(560,560,Bitmap.Config.ARGB_8888)
            val phase=((1.0-cos(frame*2.0*Math.PI/32.0))/2.0).toFloat()
            PicoRenderer.draw(Canvas(image),RectF(24f,24f,536f,536f),MascotMood.HAPPY,MascotPose.WAVE,phase,drawGroundShadow=false)
            File(root,"frame-%02d.png".format(frame)).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
    }
}
