package com.malfreyt.alexandre.hamigo

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PicoFaceInstrumentedTest {
    @Test fun speechAndClosedEyesReplaceEveryUnderlyingExpressionWithoutPaintingAMask() {
        fun face(mood:MascotMood,opening:Float)=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).also {
            val c=Canvas(it);c.scale(2f,2f);PicoFace.draw(c,mood,opening,true,false)
        }
        for(open in listOf(.05f,.6f,1f)) {
            val reference=face(MascotMood.HAPPY,open)
            try {
                for(mood in MascotMood.entries) {
                    val candidate=face(mood,open)
                    try {assertTrue("No old eyes/mouth may survive $mood during speech",reference.sameAs(candidate))} finally {candidate.recycle()}
                }
                val pixels=IntArray(256*256);reference.getPixels(pixels,0,256,0,0,256,256)
                assertFalse("The feature layer contains no white erasing rectangle",pixels.any {it==Color.WHITE})
            } finally {reference.recycle()}
        }
    }
    @Test fun sharedPortraitsCoverRestingTalkingAndTongueWithClosedEyes() {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val sheet=Bitmap.createBitmap(1120,1600,Bitmap.Config.ARGB_8888);val c=Canvas(sheet)
        c.drawColor(Color.rgb(250,248,242))
        val label=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(23,63,66);textSize=15f}
        val variants=listOf("Repos","Parle fermé","Parle ouvert","Yeux fermés")
        MascotMood.entries.forEachIndexed {row,mood->variants.forEachIndexed {col,title->
            val x=col*280f;val y=row*200f
            c.drawText("${mood.name} · $title",x+12,y+20,label)
            PicoRenderer.draw(c,RectF(x+52,y+32,x+228,y+192),mood,if(row%2==0)MascotPose.WAVE else MascotPose.HUG,.25f,
                mouthOpen=when(col){1->.05f;2->.9f;else->null},eyesClosed=col==3)
        }}
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"ui-0.43").apply {mkdirs()}
        try {File(dir,"pico-face-layers.png").outputStream().use {sheet.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {sheet.recycle()}
    }
}
