package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamImageInstrumentedTest {
    @Test fun isolatedExamSpecksAreRemovedButNearbyPunctuationAndDiagramMarksStay() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for(id in listOf("20978","20003")) {
            val original=context.assets.open("exam1/images/$id.png").use(BitmapFactory::decodeStream)!!
            val untouched=original.copy(Bitmap.Config.ARGB_8888,false)
            val preview=ExamImageProcessor.preview(original)
            try {assertTrue(original.sameAs(untouched));assertTrue("Only the text should define the crop for $id",preview.height<130)}
            finally {original.recycle();untouched.recycle();preview.recycle()}
        }
        val original=Bitmap.createBitmap(180,100,Bitmap.Config.ARGB_8888)
        Canvas(original).drawColor(Color.rgb(255,254,206))
        val black=Paint().apply {color=Color.BLACK}
        Canvas(original).drawRect(40f,40f,70f,55f,black)
        original.setPixel(75,53,Color.BLACK) // A punctuation mark near text.
        original.setPixel(160,85,Color.BLACK) // Isolated scan noise.
        val preview=ExamImageProcessor.preview(original)
        try {assertEquals(68,preview.width);assertEquals(47,preview.height);assertEquals(Color.BLACK,preview.getPixel(51,29))}
        finally {original.recycle();preview.recycle()}
    }
    @Test fun yellowPaperBecomesTransparentAndInkIsCroppedWithoutChangingTheOriginal() {
        val original=Bitmap.createBitmap(770,350,Bitmap.Config.ARGB_8888)
        val paper=Color.rgb(255,254,206)
        val paint=Paint().apply {color=Color.BLACK}
        val canvas=Canvas(original).apply {drawColor(paper)}
        canvas.drawRect(300f,100f,400f,150f,paint)
        paint.color=Color.RED; canvas.drawRect(410f,100f,420f,150f,paint)
        val untouched=original.copy(Bitmap.Config.ARGB_8888,false)
        val preview=ExamImageProcessor.preview(original)
        try {
            assertTrue(original.sameAs(untouched))
            assertEquals(152,preview.width)
            assertEquals(82,preview.height)
            assertEquals(0,Color.alpha(preview.getPixel(0,0)))
            assertEquals(Color.BLACK,preview.getPixel(30,30))
            assertEquals(Color.RED,preview.getPixel(130,30))
        } finally {original.recycle();untouched.recycle();preview.recycle()}
    }

    @Test fun bundledImageKeepsAnOriginalYellowVersionAndHasAnUnmattedReadablePreview() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=context.assets.open("exam1/images/10001.png").use {BitmapFactory.decodeStream(it)}!!
        val untouched=original.copy(Bitmap.Config.ARGB_8888,false)
        val preview=ExamImageProcessor.preview(original)
        try {
            assertTrue("Preview processing must preserve every pixel of the original",original.sameAs(untouched))
            val paper=original.getPixel(0,0)
            assertEquals(255,Color.alpha(paper))
            // PNG colour decoding may round one channel by one level on Android. The paper remains pale yellow.
            assertTrue(kotlin.math.abs(Color.red(paper)-255)<=2)
            assertTrue(kotlin.math.abs(Color.green(paper)-254)<=2)
            assertTrue(kotlin.math.abs(Color.blue(paper)-206)<=2)
            assertTrue(preview.height<original.height)
            assertTrue(preview.width<=original.width)
            assertEquals(0,Color.alpha(preview.getPixel(0,0)))
            val pixels=IntArray(preview.width*preview.height)
            preview.getPixels(pixels,0,preview.width,0,0,preview.width,preview.height)
            assertTrue(pixels.any {Color.alpha(it)>200 && Color.red(it)<100})
        } finally {original.recycle();untouched.recycle();preview.recycle()}
    }
}
