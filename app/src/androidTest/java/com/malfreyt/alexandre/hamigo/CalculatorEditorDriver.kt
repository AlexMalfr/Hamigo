package com.malfreyt.alexandre.hamigo

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextRange
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import org.junit.Assert.*

/** Exercises the real native editor rather than pretending it is a Compose TextField. */
internal class CalculatorEditorDriver(private val ui:ComposeContentTestRule) {
    fun view():CalculatorEditor {
        fun find(view:View):CalculatorEditor?=if(view is CalculatorEditor)view else if(view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull {find(view.getChildAt(it))} else null
        return WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOf {find(it)}
    }
    fun performTextInput(text:String)=apply {ui.runOnIdle {view().let {it.text.replace(minOf(it.selectionStart,it.selectionEnd),maxOf(it.selectionStart,it.selectionEnd),text)}}}
    fun performTextReplacement(text:String)=apply {ui.runOnIdle {view().setText(text);view().setSelection(text.length)}}
    fun performClick()=apply {onView(isAssignableFrom(CalculatorEditor::class.java)).perform(click());ui.waitForIdle()}
    fun select(start:Int,end:Int=start)=apply {ui.runOnIdle {view().setSelection(start,end)}}
    fun selection():TextRange=ui.runOnIdle {view().let {TextRange(it.selectionStart,it.selectionEnd)}}
    fun text():String=ui.runOnIdle {view().text.toString()}
    fun assertTextContains(value:String)=apply {assertTrue(text(),text().contains(value))}
    fun assertTextEquals(value:String)=apply {assertEquals(value,text())}
}
