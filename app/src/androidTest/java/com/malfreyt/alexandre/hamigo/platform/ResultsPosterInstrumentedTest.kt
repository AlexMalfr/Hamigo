package com.malfreyt.alexandre.hamigo.platform

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultsPosterInstrumentedTest {
    @Test fun resultsTemplateHasItsOwnScoresAndReflectsBothExamParts() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val results=ShareResults("Alex 📻","Examen blanc",31,40,27*60_000L,2,95,14,17)
        val first=NativeShare.renderResultsImage(context,results)
        val changed=NativeShare.renderResultsImage(context,results.copy(regulationScore=9,correct=26))
        val personal=NativeShare.renderProgressImage(context,ShareProgress("Alex 📻",500,8,15))
        val one=BitmapFactory.decodeFile(first.absolutePath)
        val two=BitmapFactory.decodeFile(changed.absolutePath)
        val three=BitmapFactory.decodeFile(personal.absolutePath)
        try {
            assertEquals(1080,one.width); assertEquals(1350,one.height)
            assertFalse(one.sameAs(two)); assertFalse(one.sameAs(three))
            assertTrue(first.length()>10_000)
        } finally {one.recycle();two.recycle();three.recycle();first.delete();changed.delete();personal.delete()}
    }
}
