package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileHeaderInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    @Test fun detachedShadowFollowsTheCircularCapInsteadOfCrossingItWithAStraightStrip() {
        var pinned by mutableStateOf(false)
        ui.setContent {
            HamigoTheme {
                val state=remember(pinned) { LazyListState(if(pinned)1 else 0) }
                val density=LocalDensity.current
                Box(Modifier.fillMaxWidth().height(200.dp).background(Cream).testTag("shadow-root")) {
                    Box(Modifier.padding(top=40.dp,start=16.dp,end=16.dp).fillMaxWidth().height(88.dp)
                        .profileHeaderShadow(state,with(density){80.dp.toPx()},with(density){Rect(0f,16.dp.toPx(),72.dp.toPx(),88.dp.toPx())}))
                }
            }
        }
        fun pixels()=ui.onNodeWithTag("shadow-root").captureToImage().toPixelMap()
        fun sample(x: Float,y: Float): Color {
            val map=pixels()
            return map[with(ui.density){x.dp.toPx()}.toInt(),with(ui.density){y.dp.toPx()}.toInt()]
        }
        assertEquals(Cream.red,sample(52f,131f).red,.015f)
        ui.runOnIdle {pinned=true};ui.waitForIdle()
        assertEquals("The filled avatar cap covers the interior shadow",Cream.red,sample(52f,123f).red,.015f)
        assertTrue("A shadow follows the bottom of the avatar",sample(52f,131f).red<Cream.red-.04f)
        assertTrue("The straight edge still casts its shadow",sample(160f,123f).red<Cream.red-.04f)
        assertEquals("The cap shadow fades beyond its extent",Cream.red,sample(52f,141f).red,.015f)
        val map=pixels();val y=with(ui.density){123.dp.toPx()}.toInt()
        assertEquals("Shadow spans both viewport gutters",map[2,y].red,map[map.width-3,y].red,.015f)
    }
}
