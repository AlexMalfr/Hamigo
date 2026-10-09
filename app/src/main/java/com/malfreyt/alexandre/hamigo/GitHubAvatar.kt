package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.GitHubIdentity
import java.util.Locale

@Composable
fun GitHubAvatar(identity: GitHubIdentity?, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val url = identity?.avatarUrl
    val images=remember(context){GitHubAvatarImages.cache(context)}
    val bitmap by key(url) {
        produceState<Bitmap?>(initialValue=images.peek(url),url) {
            value=url?.let {images.load(it){cached->value=cached}}
        }
    }
    Box(modifier.clip(CircleShape).background(Mist).clearAndSetSemantics {
        contentDescription = "Photo GitHub de $name"
    },contentAlignment = Alignment.Center) {
        if(bitmap!=null) Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Text(name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.take(1) }.uppercase(Locale.ROOT).ifBlank { "?" },
            color=Teal,fontWeight=FontWeight.ExtraBold,fontSize=18.sp)
    }
}

/** Public avatars have their own unauthenticated transport and bounded private app cache. */
private object GitHubAvatarImages {
    @Volatile private var images:AvatarImageCache?=null
    fun cache(context:Context):AvatarImageCache=images ?: synchronized(this) {
        images ?: AvatarImageCache.create(context.applicationContext).also {images=it}
    }
}
