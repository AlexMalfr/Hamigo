package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

internal data class InkPoint(val x:Float,val y:Float,val pressure:Float)
internal data class InkStroke(val points:List<InkPoint>,val color:Int=0xFF073D40.toInt(),val widthDp:Float=2.5f)
private val ScratchpadInkColors=listOf(
    0xFF073D40.toInt() to "Encre",0xFF00858A.toInt() to "Turquoise",0xFFB54B3D.toInt() to "Corail",
    0xFF7556A8.toInt() to "Violet",0xFFB87910.toInt() to "Ocre")


/** Ephemeral session data. The long sheet and history stay in its existing model owner. */
internal class ScratchpadState {
    var text by mutableStateOf(TextFieldValue(""))
    var typing by mutableStateOf(false)
    var stylusOnly by mutableStateOf(false)
    var eraser by mutableStateOf(false)
    var color by mutableIntStateOf(0xFF073D40.toInt())
    var brushWidth by mutableFloatStateOf(2.5f)
    var toolbarRight by mutableStateOf(false)
    // Fractions of the paper width, so rotation scales the whole sheet uniformly.
    var paperScroll=0f
    var revision by mutableIntStateOf(0)
        private set
    val strokes=mutableListOf<InkStroke>()
    private data class Document(val text:TextFieldValue,val strokes:List<InkStroke>)
    private val undo=mutableListOf<Document>()
    private val redo=mutableListOf<Document>()
    private var inkGesture:Document?=null
    val canUndo get():Boolean {revision;return undo.isNotEmpty()}
    val canRedo get():Boolean {revision;return redo.isNotEmpty()}
    val hasInk get():Boolean {revision;return strokes.isNotEmpty()}
    private fun snapshot()=Document(text,strokes.toList())
    private fun rememberEdit(before:Document=snapshot()) {
        undo.add(before);if(undo.size>60)undo.removeAt(0)
        redo.clear()
    }
    private fun restore(document:Document) {
        text=document.text;strokes.clear();strokes.addAll(document.strokes);revision++
    }
    fun editText(value:TextFieldValue) {
        if(value.text!=text.text) {rememberEdit();revision++}
        text=value
    }
    fun replaceInk(next:List<InkStroke>) {
        if(next==strokes)return
        rememberEdit();strokes.clear();strokes.addAll(next);revision++
    }
    /** Erasure is visible immediately, but one contact contributes only one undo entry. */
    fun beginInkGesture() {
        endInkGesture(canceled=true)
        inkGesture=snapshot()
    }
    fun updateInkGesture(next:List<InkStroke>) {
        if(inkGesture==null||next==strokes)return
        strokes.clear();strokes.addAll(next);revision++
    }
    fun endInkGesture(canceled:Boolean) {
        val before=inkGesture ?: return
        inkGesture=null
        if(canceled) {
            if(before!=snapshot())restore(before)
        } else if(before!=snapshot()) {
            rememberEdit(before);revision++
        }
    }
    fun undoInk() {
        endInkGesture(canceled=true)
        if(undo.isEmpty())return
        redo.add(snapshot());restore(undo.removeAt(undo.lastIndex))
    }
    fun redoInk() {
        endInkGesture(canceled=true)
        if(redo.isEmpty())return
        undo.add(snapshot());restore(redo.removeAt(redo.lastIndex))
    }
    fun clear() {
        endInkGesture(canceled=true)
        if(text.text.isEmpty()&&strokes.isEmpty())return
        rememberEdit();text=TextFieldValue("");strokes.clear();revision++
    }
}

/** A six-page-height sheet with native pen input, two-finger pan and accessible scroll. */
internal class ScratchpadInkView(context:Context,val state:ScratchpadState):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val density=resources.displayMetrics.density
    private var pointer=-1
    private var tool=MotionEvent.TOOL_TYPE_UNKNOWN
    private var erasing=false
    private var panning=false
    private var panY=0f
    private var scrollbarDragging=false
    private var scrollbarGrab=0f
    private var draftColor=state.color
    private var draftWidth=state.brushWidth
    private val draft=mutableListOf<InkPoint>()
    private var seenRevision=state.revision
    private val paperHeight get()=(width*6f).coerceAtLeast(height.toFloat())
    private val offset get()=state.paperScroll*width
    init {
        contentDescription="Feuille de brouillon, défilement à deux doigts ou avec la barre à droite"
        isFocusable=false;isSoundEffectsEnabled=false;isHapticFeedbackEnabled=false
        // The always-visible track below also accepts touch; unlike a fading system
        // scrollbar, it remains discoverable when this custom View is idle.
        isVerticalScrollBarEnabled=false
        if(Build.VERSION.SDK_INT>=34)setAutoHandwritingEnabled(false)
    }
    fun refresh() {
        if(seenRevision!=state.revision) {
            state.endInkGesture(canceled=true)
            draft.clear();pointer=-1;erasing=false;seenRevision=state.revision
        }
        invalidate()
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh);scrollPaperTo(state.paperScroll)
    }
    internal fun scrollPaperTo(position:Float) {
        state.paperScroll=position.coerceIn(0f,((paperHeight-height)/width.coerceAtLeast(1)).coerceAtLeast(0f))
        invalidate()
    }
    private fun panBy(pixels:Float) {scrollPaperTo(state.paperScroll+pixels/width.coerceAtLeast(1))}
    internal fun scrollbarThumbBounds():RectF {
        val inset=8*density
        val track=(height-2*inset).coerceAtLeast(1f)
        val thumb=(track*height/paperHeight.coerceAtLeast(1f)).coerceIn((32*density).coerceAtMost(track),track)
        val fraction=if(paperHeight>height)offset/(paperHeight-height) else 0f
        val top=inset+(track-thumb)*fraction
        return RectF(width-11*density,top,width-5*density,top+thumb)
    }
    private fun scrollFromBar(y:Float) {
        val thumb=scrollbarThumbBounds()
        val travel=height-16*density-thumb.height()
        val fraction=if(travel>0)(y-scrollbarGrab-8*density)/travel else 0f
        scrollPaperTo(fraction.coerceIn(0f,1f)*(paperHeight-height)/width.coerceAtLeast(1))
    }
    override fun computeVerticalScrollRange()=paperHeight.toInt()
    override fun computeVerticalScrollOffset()=offset.toInt()
    override fun computeVerticalScrollExtent()=height
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className="android.widget.ScrollView";info.isScrollable=paperHeight>height
        if(offset>0)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if(offset+height<paperHeight)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }
    override fun performAccessibilityAction(action:Int,arguments:Bundle?):Boolean = when(action) {
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD->{panBy(height*.8f);true}
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD->{panBy(-height*.8f);true}
        else->super.performAccessibilityAction(action,arguments)
    }
    override fun onGenericMotionEvent(event:MotionEvent):Boolean {
        if(event.action==MotionEvent.ACTION_SCROLL) {panBy(-event.getAxisValue(MotionEvent.AXIS_VSCROLL)*48*density);return true}
        return super.onGenericMotionEvent(event)
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        paint.color=android.graphics.Color.rgb(228,240,237);paint.strokeWidth=density
        val step=(width/15f).coerceAtLeast(16*density)
        var x=step;while(x<width) {canvas.drawLine(x,0f,x,height.toFloat(),paint);x+=step}
        var y=step-(offset%step);while(y<height) {canvas.drawLine(0f,y,width.toFloat(),y,paint);y+=step}
        state.strokes.forEach {drawStroke(canvas,it.points,it.color,it.widthDp)}
        if(!erasing)drawStroke(canvas,draft,draftColor,draftWidth)
        else draft.lastOrNull()?.let {point->
            paint.color=state.color;paint.style=Paint.Style.STROKE;paint.strokeWidth=density
            canvas.drawCircle(point.x*width,point.y*paperHeight-offset,eraserRadius(),paint)
            paint.style=Paint.Style.FILL
        }
        if(paperHeight>height) {
            val thumb=scrollbarThumbBounds()
            paint.color=0xFFE0ECE6.toInt()
            canvas.drawRoundRect(thumb.left,8*density,thumb.right,height-8*density,3*density,3*density,paint)
            paint.color=0xB300858A.toInt()
            canvas.drawRoundRect(thumb,3*density,3*density,paint)
        }
    }
    private fun drawStroke(canvas:Canvas,points:List<InkPoint>,color:Int,widthDp:Float) {
        paint.color=color
        points.forEachIndexed {index,point->
            paint.strokeWidth=widthDp*density*(.8f+point.pressure*.5f)
            if(index==0)canvas.drawCircle(point.x*width,point.y*paperHeight-offset,paint.strokeWidth/2,paint)
            else {
                val previous=points[index-1]
                canvas.drawLine(previous.x*width,previous.y*paperHeight-offset,point.x*width,point.y*paperHeight-offset,paint)
            }
        }
    }
    private fun isPen(type:Int)=type==MotionEvent.TOOL_TYPE_STYLUS||type==MotionEvent.TOOL_TYPE_ERASER
    private fun append(event:MotionEvent,index:Int) {
        val firstNewPoint=draft.size
        fun add(x:Float,y:Float,pressure:Float) {
            if(width<=0||height<=0)return
            draft.add(InkPoint((x/width).coerceIn(0f,1f),((y+offset)/paperHeight).coerceIn(0f,1f),if(isPen(tool))pressure.coerceIn(.1f,1f) else 1f))
        }
        for(history in 0 until event.historySize)add(event.getHistoricalX(index,history),event.getHistoricalY(index,history),event.getHistoricalPressure(index,history))
        add(event.getX(index),event.getY(index),event.getPressure(index))
        if(erasing) {
            state.updateInkGesture(state.strokes.filterNot {touchedByEraser(it,firstNewPoint)})
            // This revision is ours; refreshing Compose must not cancel a live contact.
            seenRevision=state.revision
        }
    }
    private fun centerY(event:MotionEvent)=(0 until event.pointerCount).map(event::getY).average().toFloat()
    private fun cancelInkContact() {
        state.endInkGesture(canceled=true)
        draft.clear();pointer=-1;erasing=false;seenRevision=state.revision
    }
    override fun onDetachedFromWindow() {
        cancelInkContact()
        super.onDetachedFromWindow()
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN->{
                val index=event.actionIndex;val nextTool=event.getToolType(index)
                if(!isPen(nextTool)&&!isPen(tool)&&event.pointerCount>1) {
                    cancelInkContact();scrollbarDragging=false;panning=true;panY=centerY(event)
                } else if(!isPen(nextTool)&&pointer<0&&event.getX(index)>width-24*density&&paperHeight>height) {
                    cancelInkContact();panning=false;scrollbarDragging=true
                    val thumb=scrollbarThumbBounds()
                    scrollbarGrab=if(event.getY(index) in thumb.top..thumb.bottom)event.getY(index)-thumb.top else thumb.height()/2
                    scrollFromBar(event.getY(index))
                } else if(!isPen(nextTool)&&state.stylusOnly&&pointer<0) {
                    cancelInkContact();scrollbarDragging=false;panning=true;panY=centerY(event)
                } else if(pointer<0||isPen(nextTool)&&!isPen(tool)) {
                    cancelInkContact();scrollbarDragging=false;panning=false
                    pointer=event.getPointerId(index);tool=nextTool
                    erasing=state.eraser||tool==MotionEvent.TOOL_TYPE_ERASER||isPen(tool)&&event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY!=0
                    draftColor=state.color;draftWidth=state.brushWidth
                    if(erasing)state.beginInkGesture()
                    append(event,index)
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE->if(scrollbarDragging)scrollFromBar(event.getY(0))
                else if(panning) {val next=centerY(event);panBy(panY-next);panY=next}
                else event.findPointerIndex(pointer).takeIf {it>=0}?.let {append(event,it)}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP->{
                var ended=false
                if(panning||scrollbarDragging) {panning=false;scrollbarDragging=false;pointer=-1;ended=true}
                else if(event.getPointerId(event.actionIndex)==pointer) {
                    ended=true
                    val canceled=Build.VERSION.SDK_INT>=33&&event.flags and MotionEvent.FLAG_CANCELED!=0
                    if(!canceled) {
                        append(event,event.actionIndex)
                        if(erasing)state.endInkGesture(canceled=false)
                        else if(draft.isNotEmpty())state.replaceInk(state.strokes+InkStroke(draft.toList(),draftColor,draftWidth))
                    } else state.endInkGesture(canceled=true)
                    draft.clear();pointer=-1;erasing=false;seenRevision=state.revision;performClick()
                }
                if(ended) {tool=MotionEvent.TOOL_TYPE_UNKNOWN;parent?.requestDisallowInterceptTouchEvent(false)}
            }
            MotionEvent.ACTION_CANCEL->{
                cancelInkContact();tool=MotionEvent.TOOL_TYPE_UNKNOWN;panning=false;scrollbarDragging=false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate();return true
    }
    private fun eraserRadius()=(state.brushWidth*4).coerceIn(10f,32f)*density
    private fun touchedByEraser(stroke:InkStroke,firstNewPoint:Int):Boolean {
        val radius=eraserRadius()+stroke.widthDp*density/2
        val radiusSquared=radius*radius
        fun nearPointSegment(px:Float,py:Float,ax:Float,ay:Float,bx:Float,by:Float):Boolean {
            val dx=bx-ax;val dy=by-ay
            val t=if(dx*dx+dy*dy==0f)0f else (((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy)).coerceIn(0f,1f)
            val ex=px-ax-t*dx;val ey=py-ay-t*dy
            return ex*ex+ey*ey<=radiusSquared
        }
        fun segmentsTouch(a:InkPoint,b:InkPoint,c:InkPoint,d:InkPoint):Boolean {
            val ax=a.x*width;val ay=a.y*paperHeight
            val bx=b.x*width;val by=b.y*paperHeight
            val cx=c.x*width;val cy=c.y*paperHeight
            val dx=d.x*width;val dy=d.y*paperHeight
            val denominator=(bx-ax)*(dy-cy)-(by-ay)*(dx-cx)
            if(kotlin.math.abs(denominator)>.0001f) {
                val t=((cx-ax)*(dy-cy)-(cy-ay)*(dx-cx))/denominator
                val u=((cx-ax)*(by-ay)-(cy-ay)*(bx-ax))/denominator
                if(t in 0f..1f&&u in 0f..1f)return true
            }
            return nearPointSegment(ax,ay,cx,cy,dx,dy)||nearPointSegment(bx,by,cx,cy,dx,dy)||
                nearPointSegment(cx,cy,ax,ay,bx,by)||nearPointSegment(dx,dy,ax,ay,bx,by)
        }
        // Include the previous sample so a quick sweep also erases between events.
        return (firstNewPoint.coerceAtLeast(1)-1 until draft.size).any {index->
            val end=draft[index];val start=draft[(index-1).coerceAtLeast(0)]
            stroke.points.indices.any {strokeIndex->
                segmentsTouch(start,end,stroke.points[(strokeIndex-1).coerceAtLeast(0)],stroke.points[strokeIndex])
            }
        }
    }
    override fun performClick():Boolean {super.performClick();return true}
}

@Composable private fun ScratchpadEraserIcon() {
    val tint=LocalContentColor.current
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        rotate(-32f) {
            val top=Offset(size.width*.25f,size.height*.12f);val block=Size(size.width*.5f,size.height*.76f)
            drawRoundRect(tint,top,block,androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            drawRect(Cream,Offset(top.x,center.y),Size(block.width,block.height*.5f))
            drawRoundRect(tint,top,block,androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),style=Stroke(1.5.dp.toPx()))
            drawLine(tint,Offset(top.x,center.y),Offset(top.x+block.width,center.y),1.5.dp.toPx())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FloatingScratchpad(isOpen:Boolean,onDismiss:()->Unit,state:ScratchpadState,anchorBounds:Rect?) {
    val visibility=remember {Animatable(0f)}
    var rendered by remember {mutableStateOf(false)}
    val dismiss by rememberUpdatedState(onDismiss)
    val window=LocalWindowInfo.current.containerSize
    val landscape=window.width>window.height
    var clearConfirmation by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(isOpen) {
        if(isOpen)rendered=true
        visibility.animateTo(if(isOpen)1f else 0f,tween(200,easing=FastOutSlowInEasing))
        if(!isOpen)rendered=false
    }
    if(!rendered)return
    Dialog({dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,dismissOnClickOutside=false)) {
        val view=LocalView.current
        val focus=LocalFocusManager.current
        val keyboard=LocalSoftwareKeyboardController.current
        val textFocus=remember {FocusRequester()}
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let {
                it.setDimAmount(0f);it.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                @Suppress("DEPRECATION")
                it.navigationBarColor=android.graphics.Color.TRANSPARENT
                if(Build.VERSION.SDK_INT>=28)it.navigationBarDividerColor=android.graphics.Color.TRANSPARENT
                if(Build.VERSION.SDK_INT>=29)it.isNavigationBarContrastEnforced=false
            }
            onDispose {}
        }
        LaunchedEffect(isOpen,state.typing) {
            if(isOpen&&state.typing) {withFrameNanos {};textFocus.requestFocus();keyboard?.show()}
            else {focus.clearFocus();keyboard?.hide()}
        }
        @Composable fun modeToggle() {
            SingleChoiceSegmentedButtonRow(Modifier.width(112.dp).height(48.dp).testTag("scratchpad-mode")) {
                SegmentedButton(!state.typing,feedbackClick {state.typing=false},shape=SegmentedButtonDefaults.itemShape(0,2),icon={}) {
                    Icon(Icons.Rounded.Draw,"Dessin",Modifier.size(20.dp))
                }
                SegmentedButton(state.typing,feedbackClick {state.typing=true},shape=SegmentedButtonDefaults.itemShape(1,2),icon={}) {
                    Icon(Icons.Rounded.Keyboard,"Texte",Modifier.size(20.dp))
                }
            }
        }
        @Composable fun tool(description:String,enabled:Boolean=true,selected:Boolean=false,click:()->Unit,icon:@Composable ()->Unit) {
            IconButton(feedbackClick(click),Modifier.size(48.dp).semantics {
                contentDescription=description
                if(description=="Gomme")stateDescription=if(selected)"Activée" else "Désactivée"
            },enabled=enabled,colors=IconButtonDefaults.iconButtonColors(
                containerColor=if(selected)Mist else Color.Transparent,contentColor=if(selected)Teal else Ink)) {icon()}
        }
        @Composable fun closeTool() {
            tool("Fermer le brouillon",click={focus.clearFocus();keyboard?.hide();dismiss()}) {Icon(Icons.Rounded.Close,null)}
        }
        @Composable fun actions(sidebar:Boolean) {
            val buttons=(if(state.typing)emptyList() else listOf<@Composable ()->Unit>(
                {tool("Gomme",true,state.eraser,{state.eraser=!state.eraser}) {ScratchpadEraserIcon()}}
            ))+listOf<@Composable ()->Unit>(
                {tool("Annuler la dernière modification",state.canUndo,click={state.undoInk()}) {Icon(Icons.Rounded.Undo,null)}},
                {tool("Rétablir",state.canRedo,click={state.redoInk()}) {Icon(Icons.Rounded.Redo,null)}},
                {tool("Effacer le brouillon",state.text.text.isNotEmpty()||state.hasInk,click={clearConfirmation=true}) {Icon(Icons.Rounded.DeleteSweep,null,tint=Color(0xFF963E54))}}
            )
            if(sidebar)Column(horizontalAlignment=Alignment.CenterHorizontally) {
                buttons.chunked(2).forEach {pair->Row {pair.forEach {it()}}}
            } else Row {buttons.forEach {it()}}
        }
        @Composable fun widths(modifier:Modifier=Modifier.width(144.dp),onPick:()->Unit={}) {
            SingleChoiceSegmentedButtonRow(modifier.height(48.dp).testTag("scratchpad-widths")) {
                listOf(Triple(1.5f,4.dp,"Fine"),Triple(2.5f,7.dp,"Moyenne"),Triple(5f,11.dp,"Épaisse")).forEachIndexed {index,(width,diameter,label)->
                    SegmentedButton(state.brushWidth==width,feedbackClick {state.brushWidth=width;state.eraser=false;onPick()},
                        shape=SegmentedButtonDefaults.itemShape(index,3),enabled=!state.typing,icon={},
                        modifier=Modifier.height(48.dp).semantics {contentDescription="Épaisseur $label"}) {
                        Box(Modifier.size(diameter).clip(CircleShape).background(LocalContentColor.current))
                    }
                }
            }
        }
        @Composable fun widthMenu(modifier:Modifier) {
            var expanded by remember {mutableStateOf(false)}
            Box(modifier) {
                OutlinedButton(feedbackClick {expanded=true},Modifier.fillMaxWidth().height(48.dp).testTag("scratchpad-width-menu")
                    .semantics {contentDescription="Épaisseur du trait"},contentPadding=PaddingValues(horizontal=8.dp)) {
                    Box(Modifier.size(if(state.brushWidth>=5f)11.dp else if(state.brushWidth>=2.5f)7.dp else 4.dp).clip(CircleShape).background(Ink))
                    Spacer(Modifier.width(12.dp));Icon(Icons.Rounded.ExpandMore,null,Modifier.size(20.dp))
                }
                DropdownMenu(expanded,{expanded=false}) {widths(Modifier.padding(horizontal=8.dp).width(144.dp)){expanded=false}}
            }
        }
        @Composable fun colors(sidebar:Boolean,modifier:Modifier=Modifier,dropdownOnly:Boolean=false) {
            val choices=ScratchpadInkColors
            @Composable fun colorRow(items:List<Pair<Int,String>>,rowModifier:Modifier,onPick:()->Unit={}) {
                SingleChoiceSegmentedButtonRow(rowModifier.height(48.dp)) {
                    items.forEachIndexed {index,(color,label)->
                        SegmentedButton(state.color==color,feedbackClick {state.color=color;state.eraser=false;onPick()},
                            shape=SegmentedButtonDefaults.itemShape(index,items.size),enabled=!state.typing,icon={},
                            modifier=Modifier.height(48.dp).semantics {contentDescription="Couleur $label"}) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)),contentAlignment=Alignment.Center) {
                                if(state.color==color)Icon(Icons.Rounded.Check,null,Modifier.size(14.dp),tint=Cream)
                            }
                        }
                    }
                }
            }
            if(sidebar)Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                choices.chunked(3).forEach {colorRow(it,Modifier.width((48*it.size).dp))}
            } else BoxWithConstraints(modifier.testTag("scratchpad-colors").semantics {
                stateDescription=choices.firstOrNull {it.first==state.color}?.second.orEmpty()
            }) {
                var expanded by remember {mutableStateOf(false)}
                val slots=((maxWidth.value+.01f)/48f).toInt().coerceAtLeast(1)
                val visible=if(slots>=choices.size)choices else choices.take((slots-1).coerceAtLeast(0))
                val overflow=choices.drop(visible.size)
                val count=visible.size+if(overflow.isEmpty())0 else 1
                if(dropdownOnly)OutlinedButton(feedbackClick {expanded=true},Modifier.fillMaxWidth().height(48.dp)
                    .semantics {contentDescription="Autres couleurs"},contentPadding=PaddingValues(horizontal=8.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Color(state.color)))
                    Spacer(Modifier.width(12.dp));Icon(Icons.Rounded.ExpandMore,null,Modifier.size(20.dp))
                } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(48.dp)) {
                    visible.forEachIndexed {index,(color,label)->
                        SegmentedButton(state.color==color,feedbackClick {state.color=color;state.eraser=false},
                            shape=SegmentedButtonDefaults.itemShape(index,count),enabled=!state.typing,icon={},
                            modifier=Modifier.height(48.dp).semantics {contentDescription="Couleur $label"}) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(Color(color)),contentAlignment=Alignment.Center) {
                                if(state.color==color)Icon(Icons.Rounded.Check,null,Modifier.size(14.dp),tint=Cream)
                            }
                        }
                    }
                    if(overflow.isNotEmpty()) {
                        val hiddenSelected=overflow.any {it.first==state.color}
                        SegmentedButton(hiddenSelected,feedbackClick {expanded=true},
                            shape=SegmentedButtonDefaults.itemShape(count-1,count),enabled=!state.typing,icon={},
                            modifier=Modifier.height(48.dp).testTag("scratchpad-colors-more").semantics {
                                contentDescription="Autres couleurs"
                                stateDescription=if(hiddenSelected)choices.first {it.first==state.color}.second else "Ouvrir les couleurs masquées"
                            }) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                if(hiddenSelected)Box(Modifier.size(16.dp).clip(CircleShape).background(Color(state.color)))
                                Icon(Icons.Rounded.ExpandMore,null,Modifier.size(if(hiddenSelected)16.dp else 24.dp))
                            }
                        }
                    }
                }
                DropdownMenu(expanded,{expanded=false}) {
                    val menuColors=if(dropdownOnly)choices else overflow
                    colorRow(menuColors,Modifier.padding(horizontal=8.dp).width((48*menuColors.size).dp)){expanded=false}
                }
            }
        }
        @Composable fun stylusChoice(compact:Boolean=false,shortLabel:Boolean=false) {
            val change=feedbackClick {state.stylusOnly=!state.stylusOnly}
            if(compact)FilledTonalIconToggleButton(state.stylusOnly,{change()},Modifier.size(48.dp)
                .semantics {
                    contentDescription="Stylet seul"
                    stateDescription=if(state.stylusOnly)"Dessin réservé au stylet" else "Dessin au doigt ou au stylet"
                },enabled=!state.typing,
                colors=IconButtonDefaults.filledTonalIconToggleButtonColors(checkedContainerColor=Mist)) {
                Text("Stylet\nseul",fontSize=10.sp,lineHeight=11.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Medium,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            } else FilterChip(state.stylusOnly,change,
                modifier=Modifier.semantics {contentDescription="Stylet seul"},
                enabled=!state.typing,label={Text(if(shortLabel)"Stylet seul" else "Stylet uniquement",fontSize=12.sp)},
                leadingIcon={Icon(if(state.stylusOnly)Icons.Rounded.Check else Icons.Rounded.Edit,null,Modifier.size(18.dp))})
        }
        @Composable fun switchToolbarSide() {
            tool("Changer le côté des outils",click={state.toolbarRight=!state.toolbarRight}) {
                Icon(if(state.toolbarRight)Icons.Rounded.KeyboardArrowLeft else Icons.Rounded.KeyboardArrowRight,null)
            }
        }
        @Composable fun sidebar() {
            BoxWithConstraints(Modifier.fillMaxHeight()) {
                // With the real IME open, two fixed 48 dp rows leave all text actions reachable.
                val condensed=state.typing&&maxHeight<220.dp
                val compactDrawing=maxHeight<300.dp
                Column(Modifier.width(if(state.typing)192.dp else 208.dp).fillMaxHeight().testTag("scratchpad-tools"),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    if(condensed) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            modeToggle();Spacer(Modifier.weight(1f));closeTool()
                        }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            actions(sidebar=false);switchToolbarSide()
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            modeToggle()
                            closeTool()
                        }
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("scratchpad-tool-options"),
                            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            if(!state.typing)stylusChoice()
                            actions(sidebar=false)
                            if(!state.typing) {
                                if(compactDrawing)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    widthMenu(Modifier.weight(1f))
                                    colors(sidebar=false,modifier=Modifier.weight(1f),dropdownOnly=true)
                                } else {
                                    widths(Modifier.fillMaxWidth())
                                    colors(sidebar=false,modifier=Modifier.fillMaxWidth())
                                }
                            }
                        }
                        switchToolbarSide()
                    }
                }
            }
        }
        @Composable fun portraitTools() {
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("scratchpad-tools")) {
                val toolbarWidth=maxWidth
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        actions(sidebar=false)
                        if(!state.typing)stylusChoice(compact=toolbarWidth<314.dp,shortLabel=toolbarWidth<344.dp)
                    }
                    if(!state.typing)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        widths()
                        colors(sidebar=false,modifier=Modifier.weight(1f))
                    }
                }
            }
        }
        @Composable fun sheet(modifier:Modifier) {
            if(state.typing)OutlinedTextField(state.text,state::editText,modifier.focusRequester(textFocus).testTag("scratchpad-text"),placeholder={Text("Calculs, idées, étapes…")},label={Text("Mes notes")})
            else {
                val revision=state.revision
                AndroidView(factory={ScratchpadInkView(it,state)},update={revision;it.refresh()},modifier=modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).testTag("scratchpad-ink"))
            }
        }
        var bounds by remember {mutableStateOf(Rect.Zero)}
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.32f*visibility.value)).testTag("scratchpad-backdrop")
            .pointerInput(Unit) {detectTapGestures {point->if(point.y<size.height*.5f)dismiss()}}.imePadding().safeDrawingPadding(),contentAlignment=Alignment.Center) {
            Box(Modifier.padding(horizontal=14.dp).widthIn(max=if(landscape)1000.dp else 540.dp).fillMaxWidth().fillMaxHeight(.94f)
                .onGloballyPositioned {bounds=it.screenBounds(view)}) {
                Surface(Modifier.fillMaxSize().graphicsLayer {
                    val fraction=visibility.value
                    scaleX=if(anchorBounds!=null)(anchorBounds.width/bounds.width.coerceAtLeast(1f))*(1-fraction)+fraction else .85f+.15f*fraction
                    scaleY=if(anchorBounds!=null)(anchorBounds.height/bounds.height.coerceAtLeast(1f))*(1-fraction)+fraction else .85f+.15f*fraction
                    if(anchorBounds!=null) {translationX=(anchorBounds.center.x-bounds.center.x)*(1-fraction);translationY=(anchorBounds.center.y-bounds.center.y)*(1-fraction)}
                    alpha=(fraction/.15f).coerceIn(0f,1f)
                }.pointerInput(Unit) {detectTapGestures {}}.testTag("scratchpad-surface"),shape=RoundedCornerShape(24.dp),color=Cream,shadowElevation=8.dp) {
                    if(landscape) {
                        Row(Modifier.fillMaxSize().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            if(!state.toolbarRight)sidebar()
                            sheet(Modifier.weight(1f).fillMaxHeight())
                            if(state.toolbarRight)sidebar()
                        }
                    } else Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            BoxWithConstraints(Modifier.weight(1f)) {
                                Text("Brouillon",color=Ink,fontSize=if(maxWidth<120.dp)16.sp else 19.sp,fontWeight=FontWeight.ExtraBold,
                                    maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            modeToggle()
                            closeTool()
                        }
                        portraitTools()
                        sheet(Modifier.fillMaxWidth().weight(1f))
                    }

                }
            }
        }
        if(clearConfirmation)AlertDialog(onDismissRequest={clearConfirmation=false},title={Text("Tout effacer ?")},
            text={Text("Les notes et les traits de ce brouillon seront effacés. Tu pourras rétablir le contenu avec Annuler.")},
            confirmButton={TextButton(feedbackClick {state.clear();clearConfirmation=false}) {Text("Tout effacer")}},
            dismissButton={TextButton(feedbackClick {clearConfirmation=false}) {Text("Annuler")}})
    }
}