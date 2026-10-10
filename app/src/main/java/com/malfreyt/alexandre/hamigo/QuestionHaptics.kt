package com.malfreyt.alexandre.hamigo

import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt

/** Detents are relative to the scale, never to the correct answer. */
internal fun sliderCue(from:Float,to:Float,min:Float,max:Float,intervals:Int):FeedbackCue? {
    if(max<=min||!from.isFinite()||!to.isFinite())return null
    val count=intervals.coerceIn(10,100)
    fun bucket(v:Float)=((v.coerceIn(min,max)-min)/(max-min)*count).roundToInt()
    val old=bucket(from);val next=bucket(to)
    return when {
        old==next->null
        next==0||next==count->FeedbackCue.SLIDER_EDGE
        next/(count/10)!=old/(count/10)->FeedbackCue.SLIDER_MARK
        else->FeedbackCue.SLIDER_TICK
    }
}

@Composable internal fun HapticQuestionSlider(value:Float,onValueChange:(Float)->Unit,range:ClosedFloatingPointRange<Float>,
    enabled:Boolean,resetKey:Any?,steps:Int=0,intervals:Int=100,quantize:(Float)->Float={it}) {
    val feedback=LocalAppFeedback.current
    var moved by remember(resetKey){mutableStateOf(false)}
    Slider(value,{raw->
        val next=quantize(raw)
        sliderCue(value,next,range.start,range.endInclusive,intervals)?.let {feedback?.event(it);moved=true}
        onValueChange(next)
    },Modifier.testTag("question-slider"),enabled=enabled,valueRange=range,steps=steps,
        onValueChangeFinished={if(moved)feedback?.event(FeedbackCue.SLIDER_RELEASE);moved=false})
}
