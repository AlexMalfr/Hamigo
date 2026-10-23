package com.malfreyt.alexandre.hamigo

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** Opening a fresh confirmation always starts a real five-second reading delay. */
@Composable internal fun DelayedDestructiveButton(label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    var progress by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(Unit) {
        val start=SystemClock.elapsedRealtime()
        while(progress<1f) {
            delay(40)
            progress=((SystemClock.elapsedRealtime()-start)/5_000f).coerceIn(0f,1f)
        }
    }
    val ready=progress>=1f
    val seconds=ceil((1f-progress)*5f).toInt().coerceAtLeast(1)
    val fill=MaterialTheme.colorScheme.error.copy(alpha=.18f)
    Button(feedbackClick {if(ready)onClick()},modifier,enabled=ready,contentPadding=PaddingValues(0.dp),
        colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,
            contentColor=MaterialTheme.colorScheme.onError,disabledContainerColor=MaterialTheme.colorScheme.errorContainer,
            disabledContentColor=MaterialTheme.colorScheme.onErrorContainer)) {
        Box(Modifier.heightIn(min=48.dp).drawBehind {
            if(!ready)drawRect(fill,size=Size(size.width*progress,size.height))
        }.padding(horizontal=20.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
            // Reserve the countdown's width so enabling the action never shifts it.
            Text("$label (5 s)",color=Color.Transparent,modifier=Modifier.clearAndSetSemantics {})
            Text(if(ready)label else "$label (${seconds} s)")
        }
    }
}
