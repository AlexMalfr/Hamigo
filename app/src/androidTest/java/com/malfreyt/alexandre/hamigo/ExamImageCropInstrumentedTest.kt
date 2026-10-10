package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExamImageCropInstrumentedTest {
    @Test fun paperPaletteNoiseDoesNotDecentreQuestion20081() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=ExamBankTestFixtures.open(context,"20081").use(BitmapFactory::decodeStream)!!
        val untouched=original.copy(Bitmap.Config.ARGB_8888,false)
        val preview=ExamImageProcessor.preview(original)
        try {
            assertTrue("The source shown when enlarging must remain intact",original.sameAs(untouched))
            assertTrue("Only the two text lines should define the width",preview.width in 330..350)
            assertTrue("Bottom-edge paper noise must not expand the crop",preview.height in 125..140)
            val pixels=IntArray(preview.width*preview.height)
            preview.getPixels(pixels,0,preview.width,0,0,preview.width,preview.height)
            val ink=pixels.indices.filter {Color.alpha(pixels[it])>20}
            val left=ink.minOf {it%preview.width};val right=ink.maxOf {it%preview.width}
            val top=ink.minOf {it/preview.width};val bottom=ink.maxOf {it/preview.width}
            assertEquals(16,left);assertEquals(16,preview.width-1-right)
            assertEquals(16,top);assertEquals(16,preview.height-1-bottom)
            if(Build.HARDWARE in listOf("ranchu","goldfish")) {
                val dir=File(context.getExternalFilesDir(null),"exam-20081").apply {mkdirs()}
                File(dir,"20081-original.png").outputStream().use {original.compress(Bitmap.CompressFormat.PNG,100,it)}
                val displayed=Bitmap.createBitmap(preview.width,preview.height,Bitmap.Config.ARGB_8888)
                try {
                    Canvas(displayed).apply {drawColor(Color.rgb(250,248,242));drawBitmap(preview,0f,0f,null)}
                    File(dir,"20081-preview.png").outputStream().use {displayed.compress(Bitmap.CompressFormat.PNG,100,it)}
                } finally {displayed.recycle()}
            }
        } finally {original.recycle();untouched.recycle();preview.recycle()}
    }

    @Test fun palePaperVariantsDisappearWithoutRemovingColouredMarksOrPunctuation() {
        val source=Bitmap.createBitmap(180,100,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(source).apply {drawColor(Color.rgb(255,254,206))}
        val paint=Paint().apply {color=Color.BLACK}
        canvas.drawRect(40f,40f,70f,55f,paint)
        source.setPixel(75,53,Color.BLACK) // Punctuation next to the text must survive.
        paint.color=Color.RED;canvas.drawRect(80f,40f,85f,55f,paint)
        // Source 20081 has these near-paper colours at otherwise empty far-away positions.
        paint.color=Color.rgb(255,255,203);canvas.drawRect(150f,5f,162f,8f,paint)
        paint.color=Color.rgb(255,255,211);canvas.drawRect(3f,91f,15f,96f,paint)
        val preview=ExamImageProcessor.preview(source)
        try {
            assertEquals(77,preview.width);assertEquals(47,preview.height)
            assertEquals(Color.BLACK,preview.getPixel(51,29))
            assertEquals(Color.RED,preview.getPixel(58,21))
            assertEquals(Color.TRANSPARENT,preview.getPixel(0,0))
        } finally {source.recycle();preview.recycle()}
    }
}
