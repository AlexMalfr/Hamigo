package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap

/** Never exported or persisted; only the current fiche and library are retained per model. */
internal class LearningToolsState {
    var calculatorOpen by mutableStateOf(false)
    var scratchpadOpen by mutableStateOf(false)
    val calculator=CalculatorState()
    val scratchpad=ScratchpadState()
}

/** Values deliberately contain no reference to the weak AppModel key. */
internal object LearningToolsNavigation {
    private data class Fiche(val id:String,val tools:LearningToolsState=LearningToolsState())
    private class Screens {
        val library=LearningToolsState()
        var fiche:Fiche?=null
    }
    private val models=WeakHashMap<AppModel,Screens>()
    private fun screens(model:AppModel)=models.getOrPut(model) {Screens()}
    fun library(model:AppModel):LearningToolsState=screens(model).library
    fun fiche(model:AppModel,id:String):LearningToolsState {
        val screens=screens(model)
        val old=screens.fiche
        if(old!=null&&old.id==id)return old.tools
        return Fiche(id).also {screens.fiche=it}.tools
    }
}

@Composable internal fun learningToolsBottomClearance(bottomOverlap:Dp=LocalNavigationContentOverlap.current):Dp =
    (if(questionToolsHorizontal())96.dp else 160.dp)+bottomOverlap

/** Ephemeral tools for the Mémo library and fiches. No preference or learning data is written. */
@Composable internal fun LearningToolsLayout(resetKey:Any?,modifier:Modifier=Modifier,
    bottomOverlap:Dp=LocalNavigationContentOverlap.current,enabled:Boolean=true,
    toolsState:LearningToolsState?=null,
    content:@Composable (bottomClearance:Dp)->Unit) {
    key(resetKey) {
        val state=toolsState ?: remember {LearningToolsState()}
        var calculatorAnchor by remember {mutableStateOf<Rect?>(null)}
        var scratchpadAnchor by remember {mutableStateOf<Rect?>(null)}
        val focus=LocalFocusManager.current
        val keyboard=LocalSoftwareKeyboardController.current
        val view=LocalView.current
        val horizontal=questionToolsHorizontal()
        val clearance=(if(horizontal)96.dp else 160.dp)+bottomOverlap
        val clearFocus=if(enabled)Modifier.pointerInput(focus,keyboard) {
            detectTapGestures {focus.clearFocus();keyboard?.hide()}
        } else Modifier
        Box(modifier.fillMaxSize().imePadding().then(clearFocus)) {
            content(clearance)
            val placement=Modifier.align(Alignment.BottomEnd).padding(end=18.dp,bottom=18.dp+bottomOverlap)
                .testTag("learning-tools")
            val buttons:@Composable ()->Unit={
                FloatingActionButton(feedbackClick {if(enabled){focus.clearFocus();keyboard?.hide();state.scratchpadOpen=true}},
                    Modifier.size(56.dp).testTag("learning-tool-scratchpad")
                        .semantics {if(!enabled)disabled()}.onGloballyPositioned {scratchpadAnchor=it.screenBounds(view)},
                    containerColor=Mist,contentColor=Teal) {
                    Icon(Icons.Rounded.EditNote,"Ouvrir le brouillon",Modifier.size(28.dp))
                }
                FloatingActionButton(feedbackClick {if(enabled){focus.clearFocus();keyboard?.hide();state.calculatorOpen=true}},
                    Modifier.size(56.dp).testTag("learning-tool-calculator")
                        .semantics {if(!enabled)disabled()}.onGloballyPositioned {calculatorAnchor=it.screenBounds(view)},
                    containerColor=Teal,contentColor=Color.White) {
                    Icon(Icons.Rounded.Calculate,"Ouvrir la calculatrice",Modifier.size(28.dp))
                }
            }
            if(horizontal)Row(placement,horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {buttons()}
            else Column(placement,verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.End) {buttons()}
        }
        // A predictive destination may share the real state, but never opens or dismisses its dialogs.
        FloatingCalculator(enabled&&state.calculatorOpen,{state.calculatorOpen=false},anchorBounds=calculatorAnchor,resetKey=resetKey,state=state.calculator)
        FloatingScratchpad(enabled&&state.scratchpadOpen,{state.scratchpadOpen=false},state.scratchpad,scratchpadAnchor)
    }
}
