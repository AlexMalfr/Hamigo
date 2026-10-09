package com.malfreyt.alexandre.hamigo

internal enum class FeedbackCue { CLICK, SELECT, DRAG, SNAP, SUCCESS, ERROR, COMPLETE, FINISH, PICO }
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
    }
    fun sound(cue:FeedbackCue)=when(cue) {
        FeedbackCue.CLICK,FeedbackCue.SELECT,FeedbackCue.DRAG,FeedbackCue.SNAP->"ui_click"
        FeedbackCue.SUCCESS->"ui_success"
        FeedbackCue.ERROR->"ui_error"
        FeedbackCue.COMPLETE->"ui_complete"
        FeedbackCue.FINISH->"ui_finish"
        FeedbackCue.PICO->"ui_pico"
    }
}

/** Throttling and precedence are shared by touch, keyboard and accessibility actions. */
internal class FeedbackThrottle {
    private var lastMinor=Long.MIN_VALUE
    private var lastPlayful=Long.MIN_VALUE
    fun allow(cue:FeedbackCue,now:Long):Boolean {
        if(cue==FeedbackCue.PICO) {
            if(lastPlayful!=Long.MIN_VALUE&&now-lastPlayful<600)return false
            lastPlayful=now
        } else if(cue in setOf(FeedbackCue.CLICK,FeedbackCue.SELECT,FeedbackCue.DRAG,FeedbackCue.SNAP)) {
            if(lastMinor!=Long.MIN_VALUE&&now-lastMinor<90)return false
            lastMinor=now
        }
        return true
    }
}
