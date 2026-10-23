package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExamBankDownloadNoticeInstrumentedTest {
    @Test fun dynamicSizeUsesHeadersOnlyAndNoBankCanStartBeforeContinue()=runBlocking {
        val base=InstrumentationRegistry.getInstrumentation().targetContext;val suffix=UUID.randomUUID().toString()
        val context=object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("download-notice-$suffix-$name",mode)
        }
        val server=MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Length",1234))
        server.enqueue(MockResponse().setHeader("Content-Length",5678))
        server.start()
        try {
            assertFalse(ExamBankDownloads.allowed(context))
            assertEquals(6912L,ExamBankDownloads.size(OkHttpClient(),server.url("/assets/").toString()))
            val requests=listOf(server.takeRequest(),server.takeRequest())
            assertTrue(requests.all {it.method=="HEAD" && it.getHeader("Accept-Encoding")=="identity"})
            assertEquals(setOf("/assets/questions.json","/assets/questions.zip"),requests.map {it.path}.toSet())
            assertFalse(ExamBankDownloads.allowed(context))
            ExamBankDownloads.confirm(context)
            assertTrue(ExamBankDownloads.allowed(context))
        } finally {server.shutdown()}
    }
}
