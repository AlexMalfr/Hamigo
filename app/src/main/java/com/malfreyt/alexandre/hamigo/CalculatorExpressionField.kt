package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

/** Native selection/clipboard and hardware editing, without ever requesting the software keyboard. */
internal class CalculatorEditor(context:Context):EditText(context) {
    var changing=false
    var changed:((TextFieldValue)->Unit)?=null
    private var initialX=0f
    private var initialY=0f
    private var dragging=false
    private var anchor=CalculatorEdit("")
    private val caret=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0xff00858a.toInt();strokeWidth=2*resources.displayMetrics.density}
    private val selectionPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0x55349fa3}
    private val selectionPath=android.graphics.Path()
    private var caretVisible=true
    private var blink:Runnable?=null
    init {
        setSingleLine();showSoftInputOnFocus=false;isCursorVisible=false
        highlightColor=android.graphics.Color.TRANSPARENT
        isSoundEffectsEnabled=false;isHapticFeedbackEnabled=false
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters=arrayOf(android.text.InputFilter.LengthFilter(512))
        setTextColor(0xff073d40.toInt());textSize=18f;setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setPadding((10*resources.displayMetrics.density).toInt(),0,(10*resources.displayMetrics.density).toInt(),0)
        contentDescription="Expression de la calculatrice"
        addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){}
            override fun afterTextChanged(s:Editable?){notifyEdit()}
        })
        blink=Runnable {caretVisible=!caretVisible;invalidate();blink?.let {postDelayed(it,500)}}
    }
    internal fun resetCursorBlink() {caretVisible=true;blink?.let {removeCallbacks(it);if(isAttachedToWindow&&hasWindowFocus())postDelayed(it,500)};invalidate()}
    override fun onAttachedToWindow(){super.onAttachedToWindow();resetCursorBlink()}
    override fun onDetachedFromWindow(){blink?.let {removeCallbacks(it)};super.onDetachedFromWindow()}
    override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);resetCursorBlink()}
    private fun notifyEdit(){if(!changing&&selectionStart>=0){resetCursorBlink();changed?.invoke(TextFieldValue(text.toString(),TextRange(selectionStart,selectionEnd)))}}
    override fun onSelectionChanged(start:Int,end:Int) {
        super.onSelectionChanged(start,end)
        if(!changing&&start>=0) {
            val value=text?.toString().orEmpty()
            val a=CalculatorEditing.snap(value,start)
            val b=CalculatorEditing.snap(value,end,end>start)
            if(a!=start||b!=end){changing=true;setSelection(a,b);changing=false}
            notifyEdit();invalidate()
        }
    }
    override fun onDraw(canvas:Canvas) {
        if(selectionStart>=0&&selectionEnd>=0&&selectionStart!=selectionEnd&&layout!=null) {
            selectionPath.reset();layout.getSelectionPath(minOf(selectionStart,selectionEnd),maxOf(selectionStart,selectionEnd),selectionPath)
            val save=canvas.save();canvas.translate((totalPaddingLeft-scrollX).toFloat(),(totalPaddingTop-scrollY).toFloat())
            canvas.drawPath(selectionPath,selectionPaint);canvas.restoreToCount(save)
        }
        super.onDraw(canvas)
        if(caretVisible&&selectionStart==selectionEnd&&selectionStart>=0&&layout!=null) {
            val line=layout.getLineForOffset(selectionStart)
            val x=totalPaddingLeft+layout.getPrimaryHorizontal(selectionStart)-scrollX
            val top=totalPaddingTop+layout.getLineTop(line)-scrollY
            canvas.drawLine(x,top.toFloat(),x,(totalPaddingTop+layout.getLineBottom(line)-scrollY).toFloat(),caret)
        }
    }
    override fun performClick():Boolean {requestFocus();super.performClick();return true}
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN->{initialX=event.x;initialY=event.y;dragging=false;anchor=CalculatorEdit(text.toString(),selectionStart.coerceAtLeast(0),selectionEnd.coerceAtLeast(0))}
            MotionEvent.ACTION_MOVE->{val dx=event.x-initialX
                if(dragging||abs(dx)>ViewConfiguration.get(context).scaledTouchSlop&&abs(dx)>abs(event.y-initialY)*1.4f) {
                    if(!dragging) {
                        val cancel=MotionEvent.obtain(event).apply {action=MotionEvent.ACTION_CANCEL}
                        try {super.onTouchEvent(cancel)} finally {cancel.recycle()}
                    }
                    dragging=true;parent?.requestDisallowInterceptTouchEvent(true)
                    val next=CalculatorEditing.move(anchor,(dx/(18*resources.displayMetrics.density)).toInt())
                    setSelection(next.start);return true
                }
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->if(dragging){dragging=false;return true}
        }
        return super.onTouchEvent(event)
    }
}

@Composable internal fun CalculatorExpressionField(value:TextFieldValue,onChange:(TextFieldValue)->Unit,modifier:Modifier=Modifier) {
    AndroidView(modifier=modifier,factory={context->CalculatorEditor(context).apply {requestFocus()}},update={editor->
        val edited=editor.text.toString()!=value.text||editor.selectionStart!=value.selection.start||editor.selectionEnd!=value.selection.end
        editor.changed=onChange;editor.changing=true
        if(editor.text.toString()!=value.text)editor.setText(value.text)
        if(editor.selectionStart!=value.selection.start||editor.selectionEnd!=value.selection.end)editor.setSelection(value.selection.start,value.selection.end)
        editor.changing=false;if(edited)editor.resetCursorBlink() else editor.invalidate()
    })
}
