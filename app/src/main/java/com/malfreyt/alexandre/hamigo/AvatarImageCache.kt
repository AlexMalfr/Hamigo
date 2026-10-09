package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Public pictures only. Disk first, bounded storage, stale pictures remain visible while refreshing. */
internal class AvatarImageCache(private val directory:File,private val client:OkHttpClient,private val now:()->Long=System::currentTimeMillis) {
    private data class Picture(val bitmap:Bitmap,val fetched:Long)
    private val memory=object:LruCache<String,Picture>(4*1024*1024) {
        override fun sizeOf(key:String,value:Picture)=value.bitmap.byteCount
    }
    private val locks=ConcurrentHashMap<String,Mutex>()
    private val retryAfter=ConcurrentHashMap<String,Long>()
    fun peek(url:String?):Bitmap?=url?.let {memory.get(it)?.bitmap}
    private fun file(url:String)=File(directory,MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}+".img")
    suspend fun load(url:String,onCached:((Bitmap)->Unit)?=null):Bitmap?=withContext(Dispatchers.IO) {
        locks.computeIfAbsent(url){Mutex()}.withLock {
            val entry=file(url)
            val saved=memory.get(url) ?: runCatching {
                if(!entry.isFile||entry.length()>MAX_BYTES)null else Picture(decode(entry.readBytes()),entry.lastModified()).also {memory.put(url,it)}
            }.getOrNull()
            if(saved!=null) {
                if(onCached!=null)withContext(Dispatchers.Main){onCached(saved.bitmap)}
                if((now()-saved.fetched).coerceAtLeast(0)<FRESH_MS)return@withLock saved.bitmap
            }
            if((retryAfter[url] ?: 0)>now())return@withLock saved?.bitmap
            runCatching {
                val request=Request.Builder().url(url).header("User-Agent","Hamigo-Android")
                    .cacheControl(CacheControl.Builder().maxAge(0,TimeUnit.SECONDS).build()).build()
                val bytes=download(request);val bitmap=decode(bytes)
                store(entry,bytes);val stamp=now();entry.setLastModified(stamp)
                memory.put(url,Picture(bitmap,stamp));retryAfter.remove(url);bitmap
            }.getOrElse {retryAfter[url]=now()+5*60_000L;saved?.bitmap}
        }
    }
    private fun decode(bytes:ByteArray):Bitmap {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..512&&bounds.outHeight in 1..512)
        return requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size))
    }
    private fun download(request:Request):ByteArray=client.newCall(request).execute().use {response->
        check(response.isSuccessful);val body=checkNotNull(response.body);check(body.contentLength()<=MAX_BYTES)
        body.byteStream().use {stream->
            val out=ByteArrayOutputStream();val buffer=ByteArray(4096)
            while(true){val n=stream.read(buffer);if(n<0)break;check(out.size()+n<=MAX_BYTES);out.write(buffer,0,n)}
            out.toByteArray()
        }
    }
    private fun store(entry:File,bytes:ByteArray) {
        directory.mkdirs();val temporary=File(directory,"${UUID.randomUUID()}.tmp")
        try {temporary.writeBytes(bytes);check(temporary.renameTo(entry))} finally {temporary.delete()}
        val files=directory.listFiles()?.filter {it.extension=="img"}?.sortedBy {it.lastModified()}.orEmpty()
        var total=files.sumOf {it.length()}
        files.forEach {if(total>DISK_BYTES&&it!=entry){val size=it.length();if(it.delete())total-=size}}
    }
    companion object {
        private const val MAX_BYTES=512*1024
        private const val DISK_BYTES=8L*1024*1024
        internal const val FRESH_MS=24*60*60_000L
        fun create(context:Context):AvatarImageCache {
            val client=OkHttpClient.Builder().cache(Cache(File(context.cacheDir,"github-avatars"),8L*1024*1024))
                .connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build()
            return AvatarImageCache(File(context.cacheDir,"github-avatar-images"),client)
        }
    }
}
