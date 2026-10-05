package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun GitHubAvatar(identity: GitHubIdentity?, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val url = identity?.avatarUrl
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = null
        value = url?.let { GitHubAvatarImages.load(context,it) }
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
    private const val MAX_BYTES = 512 * 1024
    private val memory = object : LruCache<String,Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Volatile private var transport: OkHttpClient? = null
    private fun client(context: Context): OkHttpClient = transport ?: synchronized(this) {
        transport ?: OkHttpClient.Builder().cache(Cache(File(context.cacheDir,"github-avatars"),8L*1024*1024))
            .connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build().also { transport=it }
    }
    suspend fun load(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(url) ?: runCatching {
            val request=Request.Builder().url(url).header("User-Agent","Hamigo-Android").build()
            val bytes=runCatching { download(client(context),request) }.getOrElse {
                download(client(context),request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build())
            }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512)
            BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.also { memory.put(url,it) }
        }.getOrNull()
    }
    private fun download(client: OkHttpClient, request: Request): ByteArray = client.newCall(request).execute().use { response ->
        check(response.isSuccessful)
        val body=checkNotNull(response.body)
        check(body.contentLength()<=MAX_BYTES)
        body.byteStream().use { stream ->
            val output=ByteArrayOutputStream()
            val buffer=ByteArray(4096)
            while(true) {
                val read=stream.read(buffer)
                if(read<0) break
                check(output.size()+read<=MAX_BYTES)
                output.write(buffer,0,read)
            }
            output.toByteArray()
        }
    }
}
