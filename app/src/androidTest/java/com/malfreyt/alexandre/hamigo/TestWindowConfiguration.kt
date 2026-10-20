package com.malfreyt.alexandre.hamigo

import android.app.UiAutomation
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Apply after UiAutomation connects, so its saved rotation cannot override the requested format. */
internal fun ComposeTestRule.prepareTestWindow() {
    val rotation=InstrumentationRegistry.getArguments().getString("hamigoRotation")?.toIntOrNull() ?: return
    require(rotation in 0..3)
    InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(when(rotation) {
        0->UiAutomation.ROTATION_FREEZE_0
        1->UiAutomation.ROTATION_FREEZE_90
        2->UiAutomation.ROTATION_FREEZE_180
        else->UiAutomation.ROTATION_FREEZE_270
    })
    waitUntil(30_000) {
        var ready=false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ready=foregroundTestWindow()?.let {(it.width>it.height)==(rotation%2==1)}==true
        }
        ready
    }
}

/** Strictly inspect actual Android windows; never synchronize the Activity behind a Dialog. */
private fun foregroundTestWindow():View? {
    check(Looper.myLooper()==Looper.getMainLooper())
    val top=WindowInspector.getGlobalWindowViews().lastOrNull {
        it.isAttachedToWindow&&it.isShown&&it.width>0&&it.height>0
    }
    // A newly shown Dialog must acquire focus; the Activity below it is never a substitute.
    return top?.takeIf {it.hasWindowFocus()}
}

/** Wait for rendered frames of the focused native window before a full-screen screenshot. */
internal fun ComposeTestRule.awaitForegroundFrames(frameCount:Int=2,timeoutMillis:Long=15_000) {
    check(Build.VERSION.SDK_INT>=29) {"Foreground frame synchronization requires Android 10 or newer"}
    require(frameCount>0&&timeoutMillis>0)
    val instrumentation=InstrumentationRegistry.getInstrumentation()
    // Connecting UiAutomation may temporarily change window focus. Do it before waiting.
    instrumentation.uiAutomation
    waitForIdle()
    val deadline=SystemClock.elapsedRealtime()+timeoutMillis
    repeat(frameCount) {frameIndex->
        var committed=false
        var attempts=0
        while(!committed&&attempts<3&&SystemClock.elapsedRealtime()<deadline) {
            attempts++
            waitUntil((deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1)) {
                var focused=false
                instrumentation.runOnMainSync {focused=foregroundTestWindow()!=null}
                focused
            }
            val frame=CountDownLatch(1)
            var scheduledWindow:View?=null
            instrumentation.runOnMainSync {
                foregroundTestWindow()?.let {view->
                    scheduledWindow=view
                    view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()}
                    view.postInvalidateOnAnimation()
                }
            }
            val remaining=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1).coerceAtMost(3_000)
            if(scheduledWindow!=null&&frame.await(remaining,TimeUnit.MILLISECONDS)) {
                instrumentation.runOnMainSync {committed=foregroundTestWindow()===scheduledWindow}
            }
        }
        check(committed) {"The focused foreground window did not commit frame $frameIndex after $attempts attempts"}
    }
}
