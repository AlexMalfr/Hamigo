package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AvatarImageCacheInstrumentedTest {
    @Test fun picturesSurviveMemoryLossAndOfflineRefreshWithoutRefetchingEveryView()=runBlocking {
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"avatar-fixture-${UUID.randomUUID()}")
        val picture=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.CYAN)}
        val bytes=ByteArrayOutputStream().also {picture.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();picture.recycle()
        val calls=AtomicInteger();var offline=false;var clock=System.currentTimeMillis()
        val client=OkHttpClient.Builder().addInterceptor {chain->
            calls.incrementAndGet();assertNull(chain.request().header("Authorization"))
            if(offline)throw IOException("Offline fixture")
            Thread.sleep(60)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Cache-Control","no-store").body(bytes.toResponseBody()).build()
        }.build()
        try {
            val url="https://avatars.githubusercontent.com/u/42?s=128&v=4"
            val first=AvatarImageCache(dir,client){clock}
            val loaded=(1..8).map {async(Dispatchers.Default){first.load(url)}}.awaitAll()
            assertEquals(1,calls.get());assertTrue(loaded.all {it?.width==32});assertSame(loaded.first(),first.peek(url))
            val restarted=AvatarImageCache(dir,client){clock}
            assertNotNull(restarted.load(url));assertEquals(1,calls.get())
            clock+=AvatarImageCache.FRESH_MS+1;offline=true
            var shownBeforeRefresh=false
            assertNotNull(restarted.load(url){shownBeforeRefresh=true})
            assertTrue(shownBeforeRefresh);assertEquals(2,calls.get())
            assertNotNull(restarted.load(url));assertEquals(2,calls.get())
        } finally {dir.deleteRecursively();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll()}
    }
}
