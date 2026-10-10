package com.malfreyt.alexandre.hamigo

internal enum class FeedbackCue { CLICK, SELECT, DRAG, SNAP, SUCCESS, ERROR, COMPLETE, FINISH, PICO,
    SLIDER_TICK, SLIDER_MARK, SLIDER_EDGE, SLIDER_RELEASE, FLASH_FLIP, BINARY_ON, BINARY_OFF, MORSE_DOT, MORSE_DASH, ORDER_STEP, TRUE_FALSE }
internal data class HapticPattern(val times:LongArray,val strengths:IntArray)
internal object FeedbackDesign {
    fun haptic(cue:FeedbackCue):HapticPattern?=when(cue) {
        FeedbackCue.CLICK->null // Ordinary navigation should not vibrate on every tap.
        FeedbackCue.SELECT->HapticPattern(longArrayOf(0,12),intArrayOf(0,48))
        FeedbackCue.DRAG->HapticPattern(longArrayOf(0,16),intArrayOf(0,66))
        FeedbackCue.SNAP->HapticPattern(longArrayOf(0,12,22,8),intArrayOf(0,74,0,40))
        FeedbackCue.SUCCESS->HapticPattern(longArrayOf(0,16,85,20),intArrayOf(0,72,0,125))
        FeedbackCue.ERROR->HapticPattern(longArrayOf(0,22,52,14),intArrayOf(0,92,0,48))
        FeedbackCue.COMPLETE->HapticPattern(longArrayOf(0,18,112,12,242,20),intArrayOf(0,78,0,110,0,155))
        FeedbackCue.FINISH->HapticPattern(longArrayOf(0,18,82,18),intArrayOf(0,62,0,88))
        FeedbackCue.PICO->HapticPattern(longArrayOf(0,10,35,18),intArrayOf(0,45,0,80))
        FeedbackCue.SLIDER_TICK->HapticPattern(longArrayOf(0,6),intArrayOf(0,42))
        FeedbackCue.SLIDER_MARK->HapticPattern(longArrayOf(0,10),intArrayOf(0,88))
        FeedbackCue.SLIDER_EDGE->HapticPattern(longArrayOf(0,12,24,8),intArrayOf(0,105,0,58))
        FeedbackCue.SLIDER_RELEASE->HapticPattern(longArrayOf(0,12),intArrayOf(0,70))
        FeedbackCue.FLASH_FLIP->HapticPattern(longArrayOf(0,8,155,14),intArrayOf(0,44,0,100))
        FeedbackCue.BINARY_ON->HapticPattern(longArrayOf(0,16),intArrayOf(0,84))
        FeedbackCue.BINARY_OFF->HapticPattern(longArrayOf(0,10),intArrayOf(0,54))
        FeedbackCue.MORSE_DOT->HapticPattern(longArrayOf(0,8),intArrayOf(0,48))
        FeedbackCue.MORSE_DASH->HapticPattern(longArrayOf(0,24),intArrayOf(0,58))
        FeedbackCue.ORDER_STEP->HapticPattern(longArrayOf(0,9),intArrayOf(0,66))
        FeedbackCue.TRUE_FALSE->HapticPattern(longArrayOf(0,17),intArrayOf(0,78))
    }
    fun volume(cue:FeedbackCue)=when(cue) {
        FeedbackCue.CLICK->.24f
        FeedbackCue.SELECT->.19f
        FeedbackCue.DRAG,FeedbackCue.SNAP->.2f
        FeedbackCue.SUCCESS->.64f
        FeedbackCue.ERROR->.48f
        FeedbackCue.COMPLETE->.66f
        FeedbackCue.FINISH->.55f
        FeedbackCue.PICO->.5f
        FeedbackCue.SLIDER_TICK,FeedbackCue.SLIDER_MARK,FeedbackCue.SLIDER_EDGE,FeedbackCue.SLIDER_RELEASE->0f
        else->.22f
    }
    fun sound(cue:FeedbackCue):String?=when(cue) {
        FeedbackCue.CLICK,FeedbackCue.SELECT,FeedbackCue.DRAG,FeedbackCue.SNAP->"ui_click"
        FeedbackCue.SUCCESS->"ui_success"
        FeedbackCue.ERROR->"ui_error"
        FeedbackCue.COMPLETE->"ui_complete"
        FeedbackCue.FINISH->"ui_finish"
        FeedbackCue.PICO->"ui_pico"
        FeedbackCue.SLIDER_TICK,FeedbackCue.SLIDER_MARK,FeedbackCue.SLIDER_EDGE,FeedbackCue.SLIDER_RELEASE->null
        else->"ui_click"
    }
}

/** Throttling and precedence are shared by touch, keyboard and accessibility actions. */
internal class FeedbackThrottle {
    private var lastMinor=Long.MIN_VALUE
    private var lastPlayful=Long.MIN_VALUE
    private var lastSlider=Long.MIN_VALUE
    fun allow(cue:FeedbackCue,now:Long):Boolean {
        if(cue==FeedbackCue.SLIDER_TICK||cue==FeedbackCue.SLIDER_MARK) {
            if(lastSlider!=Long.MIN_VALUE&&now-lastSlider<36)return false
            lastSlider=now
        } else if(cue==FeedbackCue.SLIDER_EDGE||cue==FeedbackCue.SLIDER_RELEASE) {
            lastSlider=now
        } else if(cue==FeedbackCue.PICO) {
            if(lastPlayful!=Long.MIN_VALUE&&now-lastPlayful<600)return false
            lastPlayful=now
        } else if(cue !in setOf(FeedbackCue.SUCCESS,FeedbackCue.ERROR,FeedbackCue.COMPLETE,FeedbackCue.FINISH)) {
            if(lastMinor!=Long.MIN_VALUE&&now-lastMinor<90)return false
            lastMinor=now
        }
        return true
    }
}
