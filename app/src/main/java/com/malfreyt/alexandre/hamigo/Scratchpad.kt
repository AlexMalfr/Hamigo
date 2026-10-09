package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal data class InkPoint(val x: Float, val y: Float, val pressure: Float)
internal data class InkStroke(val points: List<InkPoint>)

/** Ephemeral session data, never part of progression, backups or social sharing. */
internal class ScratchpadState {
    var text by mutableStateOf(TextFieldValue(""))
    var typing by mutableStateOf(false)
    var stylusOnly by mutableStateOf(false)
    var revision by mutableIntStateOf(0)
        private set
    val strokes = mutableListOf<InkStroke>()
    private val undo = mutableListOf<List<InkStroke>>()
    val canUndo get(): Boolean { revision; return undo.isNotEmpty() }
    val hasInk get(): Boolean { revision; return strokes.isNotEmpty() }
    fun replaceInk(next: List<InkStroke>) {
        if (next == strokes) return
        undo.add(strokes.toList())
        strokes.clear(); strokes.addAll(next); revision++
    }
    fun undoInk() {
        if (undo.isEmpty()) return
        val previous = undo.removeAt(undo.lastIndex)
        strokes.clear(); strokes.addAll(previous); revision++
    }
    fun clear() { text = TextFieldValue(""); replaceInk(emptyList()) }
}

/** Standard Android pen/finger events, including history, cancellation and palm rejection. */
internal class ScratchpadInkView(context: Context, val state: ScratchpadState) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val density = resources.displayMetrics.density
    private var pointer = -1
    private var tool = MotionEvent.TOOL_TYPE_UNKNOWN
    private var erasing = false
    private val draft = mutableListOf<InkPoint>()
    private var seenRevision = state.revision
    init {
        contentDescription = "Feuille de brouillon. Dessine au doigt ou au stylet ; le mode Texte permet de saisir au clavier."
        isFocusable = false
        if (Build.VERSION.SDK_INT >= 34) setAutoHandwritingEnabled(false)
    }
    fun refresh() {
        if (seenRevision != state.revision) { draft.clear(); pointer = -1; seenRevision = state.revision }
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = android.graphics.Color.rgb(228, 240, 237); paint.strokeWidth = density
        val step = 24*density
        var x = step; while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), paint); x += step }
        var y = step; while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, paint); y += step }
        paint.color = Ink.toArgb()
        state.strokes.forEach { drawStroke(canvas, it.points) }
        if (!erasing) drawStroke(canvas, draft)
        if (state.strokes.isEmpty() && draft.isEmpty()) {
            paint.color = Muted.toArgb(); paint.textAlign = Paint.Align.CENTER
            paint.textSize = 14*resources.displayMetrics.scaledDensity
            canvas.drawText("Au doigt ou au stylet", width/2f, height/2f, paint)
        }
    }
    private fun drawStroke(canvas: Canvas, points: List<InkPoint>) {
        points.forEachIndexed { index, p ->
            paint.strokeWidth = 2.5f*density*(.8f + p.pressure*.5f)
            if (index == 0) canvas.drawCircle(p.x*width, p.y*height, paint.strokeWidth/2, paint)
            else {
                val previous=points[index-1]
                canvas.drawLine(previous.x*width, previous.y*height, p.x*width, p.y*height, paint)
            }
        }
    }
    private fun isPen(type: Int) = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
    private fun append(event: MotionEvent, index: Int) {
        fun add(x: Float, y: Float, pressure: Float) {
            if(width<=0 || height<=0) return
            draft.add(InkPoint((x/width).coerceIn(0f,1f), (y/height).coerceIn(0f,1f), if(isPen(tool)) pressure.coerceIn(.1f,1f) else 1f))
        }
        for (history in 0 until event.historySize) add(event.getHistoricalX(index,history),event.getHistoricalY(index,history),event.getHistoricalPressure(index,history))
        add(event.getX(index),event.getY(index),event.getPressure(index))
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index=event.actionIndex
                val nextTool=event.getToolType(index)
                if (state.stylusOnly && !isPen(nextTool)) return true
                // An arriving pen replaces an unfinished finger/palm contact.
                if (pointer < 0 || isPen(nextTool) && !isPen(tool)) {
                    draft.clear(); pointer=event.getPointerId(index); tool=nextTool
                    erasing=tool==MotionEvent.TOOL_TYPE_ERASER || isPen(tool) && event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
                    parent?.requestDisallowInterceptTouchEvent(true)
                    append(event,index)
                }
            }
            MotionEvent.ACTION_MOVE -> event.findPointerIndex(pointer).takeIf { it>=0 }?.let { append(event,it) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if(event.getPointerId(event.actionIndex)==pointer) {
                val canceled=Build.VERSION.SDK_INT>=33 && event.flags and MotionEvent.FLAG_CANCELED != 0
                if(!canceled) {
                    append(event,event.actionIndex)
                    if(erasing) state.replaceInk(state.strokes.filterNot(::touchedByEraser))
                    else if(draft.isNotEmpty()) state.replaceInk(state.strokes + InkStroke(draft.toList()))
                }
                draft.clear(); pointer=-1; seenRevision=state.revision
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { draft.clear(); pointer=-1; parent?.requestDisallowInterceptTouchEvent(false) }
        }
        invalidate()
        return true
    }
    private fun touchedByEraser(stroke: InkStroke): Boolean {
        val radius=12*density
        return draft.any { eraser ->
            stroke.points.withIndex().any { (index,end) ->
                val start=stroke.points[(index-1).coerceAtLeast(0)]
                val dx=(end.x-start.x)*width; val dy=(end.y-start.y)*height
                val px=(eraser.x-start.x)*width; val py=(eraser.y-start.y)*height
                val t=if(dx*dx+dy*dy==0f) 0f else ((px*dx+py*dy)/(dx*dx+dy*dy)).coerceIn(0f,1f)
                val ex=px-t*dx; val ey=py-t*dy
                ex*ex+ey*ey<=radius*radius
            }
        }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

@Composable internal fun FloatingScratchpad(isOpen: Boolean, onDismiss: () -> Unit, state: ScratchpadState, anchorBounds: Rect?) {
    val visibility=remember { Animatable(0f) }
    var rendered by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(isOpen) {
        if(isOpen) rendered=true
        visibility.animateTo(if(isOpen)1f else 0f,tween(200,easing=FastOutSlowInEasing))
        if(!isOpen) rendered=false
    }
    if(!rendered) return
    Dialog({dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=LocalView.current
        val focus=LocalFocusManager.current
        val keyboard=LocalSoftwareKeyboardController.current
        val textFocus=remember { FocusRequester() }
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let {
                it.setDimAmount(0f); it.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                @Suppress("DEPRECATION")
                it.navigationBarColor=android.graphics.Color.TRANSPARENT
                if(Build.VERSION.SDK_INT>=28) it.navigationBarDividerColor=android.graphics.Color.TRANSPARENT
                if(Build.VERSION.SDK_INT>=29) it.isNavigationBarContrastEnforced=false
            }
            onDispose { }
        }
        LaunchedEffect(isOpen,state.typing) {
            if(isOpen && state.typing) { withFrameNanos { }; textFocus.requestFocus(); keyboard?.show() }
            else { focus.clearFocus(); keyboard?.hide() }
        }
        var bounds by remember { mutableStateOf(Rect.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.32f*visibility.value))
            .pointerInput(Unit) { detectTapGestures { dismiss() } }.imePadding().safeDrawingPadding(),contentAlignment=Alignment.Center) {
            // Measure a stationary wrapper, so animated coordinates cannot feed
            // back into their own scale/translation while docking to the button.
            Box(Modifier.padding(horizontal=14.dp).widthIn(max=540.dp).fillMaxWidth().fillMaxHeight(.88f)
                .onGloballyPositioned { bounds=it.screenBounds(view) }) {
            Surface(Modifier.fillMaxSize().graphicsLayer {
                    val f=visibility.value
                    scaleX=if(anchorBounds!=null) (anchorBounds.width/bounds.width.coerceAtLeast(1f))*(1-f)+f else .85f+.15f*f
                    scaleY=if(anchorBounds!=null) (anchorBounds.height/bounds.height.coerceAtLeast(1f))*(1-f)+f else .85f+.15f*f
                    if(anchorBounds!=null) { translationX=(anchorBounds.center.x-bounds.center.x)*(1-f); translationY=(anchorBounds.center.y-bounds.center.y)*(1-f) }
                    alpha=(f/.15f).coerceIn(0f,1f)
                }.pointerInput(Unit) { detectTapGestures { } }.testTag("scratchpad-surface"),
                shape=RoundedCornerShape(24.dp),color=Cream,shadowElevation=8.dp) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EditNote,null,tint=Teal)
                        Text("Brouillon",Modifier.weight(1f).padding(start=8.dp),color=Ink,fontSize=21.sp,fontWeight=FontWeight.ExtraBold)
                        IconButton(feedbackClick {focus.clearFocus();keyboard?.hide();dismiss()}) { Icon(Icons.Rounded.Close,"Fermer le brouillon") }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(!state.typing, feedbackClick {state.typing=false},{Text("Dessin")},leadingIcon={Icon(Icons.Rounded.Draw,null,Modifier.size(18.dp))},modifier=Modifier.weight(1f))
                        FilterChip(state.typing, feedbackClick {state.typing=true},{Text("Texte")},leadingIcon={Icon(Icons.Rounded.Keyboard,null,Modifier.size(18.dp))},modifier=Modifier.weight(1f))
                    }
                    if(state.typing) {
                        OutlinedTextField(state.text,{state.text=it},Modifier.fillMaxWidth().weight(1f).focusRequester(textFocus).testTag("scratchpad-text"),
                            placeholder={Text("Calculs, idées, étapes…")},label={Text("Mes notes")})
                    } else {
                        // Reading revision triggers a redraw only after committed edits, never for every pen point.
                        val revision=state.revision
                        AndroidView(factory={ScratchpadInkView(it,state)},update={revision; it.refresh()},modifier=Modifier.fillMaxWidth().weight(1f)
                            .clip(RoundedCornerShape(16.dp)).background(Color.White).testTag("scratchpad-ink"))
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        if(!state.typing) {
                            IconButton(feedbackClick {state.undoInk()},enabled=state.canUndo) { Icon(Icons.Rounded.Undo,"Annuler le dernier trait") }
                            FilterChip(state.stylusOnly, feedbackClick {state.stylusOnly=!state.stylusOnly},{Text("Stylet seul",fontSize=12.sp)})
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(feedbackClick {state.clear()},enabled=state.text.text.isNotEmpty() || state.hasInk) {
                            Icon(Icons.Rounded.DeleteSweep,"Effacer le brouillon",tint=Color(0xFF963E54))
                        }
                    }
                }
            }
            }
        }
    }
}
