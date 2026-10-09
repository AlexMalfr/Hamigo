package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class OrderBoardRevealInstrumentedTest {
    @get:Rule val ui=createComposeRule()

    @Test fun revealCardsKeepTheirPastelFacesWithoutShadowBleedingThrough() {
        val feedback=mutableStateOf<Boolean?>(null)
        val q=Question("crop-order-preview","Range ces fréquences.",listOf("10 Hz","20 kHz","30 MHz","40 GHz"),0,"",kind="order")
        ui.setContent {
            HamigoTheme {
                Box(Modifier.fillMaxWidth().background(Cream).padding(20.dp).testTag("order-render")) {
                    OrderBoard(q,listOf(0,2,1,3),enabled=feedback.value==null,feedback=feedback.value,onOrder={})
                }
            }
        }
        ui.runOnIdle {feedback.value=false}
        ui.waitForIdle()
        for((item,color) in listOf(0 to Teal,2 to Color(0xFFB65049))) {
            val image=ui.onNodeWithTag("order-item-$item").captureToImage().asAndroidBitmap()
            val expected=color.copy(alpha=.12f).compositeOver(Cream).toArgb()
            val sample=image.getPixel((image.width*.7f).toInt(),(image.height*.15f).toInt())
            for(channel in listOf<(Int)->Int>({android.graphics.Color.red(it)},{android.graphics.Color.green(it)},{android.graphics.Color.blue(it)})) {
                assertTrue("Card $item has its intended pastel, without a grey shadow under its face",abs(channel(sample)-channel(expected))<=2)
            }
        }
        if(android.os.Build.HARDWARE in listOf("ranchu","goldfish")) {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val dir=File(context.getExternalFilesDir(null),"exam-20081").apply {mkdirs()}
            val image=ui.onNodeWithTag("order-render").captureToImage().asAndroidBitmap()
            File(dir,"order-reveal-colours.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
