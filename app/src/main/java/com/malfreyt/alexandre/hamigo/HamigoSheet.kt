package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Temporary reading/editing surfaces: one scroll, with actions always reachable above the IME. */
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable internal fun HamigoSheet(title:String,onDismiss:()->Unit,modifier:Modifier=Modifier,
    contentKey:Any?=Unit,icon:(@Composable ()->Unit)?=null,titleIcon:ImageVector?=null,actions:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit) {
    val density=LocalDensity.current
    val windowHeight=with(density){LocalWindowInfo.current.containerSize.height.toDp()}
    val imeHeight=with(density){WindowInsets.ime.getBottom(density).toDp()}
    val compact=imeHeight>0.dp&&windowHeight-imeHeight<360.dp
    val sideActions=compact&&LocalWindowInfo.current.containerSize.width>LocalWindowInfo.current.containerSize.height
    val scroll=key(contentKey){rememberScrollState()}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Cream,
        dragHandle=if(compact)null else {{BottomSheetDefaults.DragHandle()}}) {
        // Keep the editable subtree in the same composition when the IME changes the layout.
        Layout(modifier=modifier.fillMaxWidth().imePadding().then(if(compact)Modifier.statusBarsPadding() else Modifier)
            .heightIn(max=windowHeight*.85f).padding(horizontal=24.dp).semantics {paneTitle=title},content={
            Box {if(!compact)Column {
                if(icon!=null)Box(Modifier.fillMaxWidth().padding(bottom=12.dp),contentAlignment=androidx.compose.ui.Alignment.Center){icon()}
                Row(Modifier.padding(bottom=16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    if(titleIcon!=null){Icon(titleIcon,null,Modifier.size(24.dp),tint=Teal);Spacer(Modifier.width(12.dp))}
                    Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink,modifier=Modifier.weight(1f),
                        textAlign=if(icon!=null)TextAlign.Center else TextAlign.Start)
                }
            }}
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(bottom=if(compact)0.dp else 12.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            FlowRow(Modifier.fillMaxWidth().padding(bottom=if(sideActions)0.dp else if(compact)6.dp else 12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp,androidx.compose.ui.Alignment.End)){actions()}
        }) {children,constraints->
            val width=constraints.maxWidth
            val loose=constraints.copy(minWidth=width,minHeight=0)
            val header=children[0].measure(loose)
            if(sideActions) {
                val gap=16.dp.roundToPx();val half=(width-gap).coerceAtLeast(0)/2
                val actionWidth=width-half-gap
                val body=children[1].measure(loose.copy(minWidth=half,maxWidth=half))
                val buttons=children[2].measure(loose.copy(minWidth=actionWidth,maxWidth=actionWidth))
                val height=maxOf(body.height,buttons.height)
                layout(width,height) {body.placeRelative(0,(height-body.height)/2);buttons.placeRelative(half+gap,(height-buttons.height)/2)}
            } else {
                val buttons=children[2].measure(loose.copy(maxHeight=(constraints.maxHeight-header.height).coerceAtLeast(0)))
                val body=children[1].measure(loose.copy(maxHeight=(constraints.maxHeight-header.height-buttons.height).coerceAtLeast(0)))
                layout(width,header.height+body.height+buttons.height) {
                    header.placeRelative(0,0);body.placeRelative(0,header.height);buttons.placeRelative(0,header.height+body.height)
                }
            }
        }
    }
}
