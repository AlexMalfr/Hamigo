package com.malfreyt.alexandre.hamigo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class ExamImageViewportTest {
    @Test fun zoomCanBeReversedAndTheHitAreaFollowsTheVisibleImage() {
        val view=Size(400f,800f);val image=Size(380f,190f);val state=ExamImageViewport()
        val point=Offset(200f,550f)
        assertFalse(state.bounds(view,image).contains(point))
        state.transform(Offset(200f,400f),Offset.Zero,2.5f,view,image)
        assertTrue(state.bounds(view,image).contains(point))
        state.transform(Offset(200f,400f),Offset.Zero,.4f,view,image)
        assertEquals(1f,state.zoom,0.001f);assertEquals(Offset.Zero,state.offset)
    }
    @Test fun panningStaysWithinTheImageAndDoubleTapReturnsToFit() {
        val view=Size(400f,800f);val image=Size(380f,190f);val state=ExamImageViewport()
        state.transform(Offset(200f,400f),Offset(5000f,5000f),3f,view,image)
        assertEquals(370f,state.offset.x,.001f);assertEquals(0f,state.offset.y,.001f)
        state.doubleTap(Offset(200f,400f),view,image)
        assertEquals(1f,state.zoom,0f);assertEquals(Offset.Zero,state.offset)
        state.doubleTap(Offset(200f,400f),view,image);assertEquals(2.5f,state.zoom,0f)
    }
}
