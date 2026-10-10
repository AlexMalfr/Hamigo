package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Only local HTTP and disposable files. No credentials, learning data or external bank in Git. */
@RunWith(AndroidJUnit4::class)
class ExamBankInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun raw(version:String="one",changed:Boolean=false):String=JSONObject().put("version",version).put("nbQuestions",64)
        .put("themes",JSONArray().put(JSONObject().put("num",201).put("nom","Électricité ")).put(JSONObject().put("num",301).put("nom","Réglementation ")))
        .put("questions",JSONArray((0..63).map {i->JSONObject().put("num",if(i==0)"31025" else "${100_000+i}")
            .put("question",if(changed)"Question modifiée $i" else "Question $i").put("propositions",JSONArray(listOf("Oui","Non")))
            .put("reponse",1).put("themeNum",if(i<32)201 else 301).put("commentaire",JSONObject.NULL)})).toString()
    private fun archive(missing:Boolean=false,malicious:Boolean=false,color:Int=Color.BLACK):ByteArray {
        val bitmap=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
        val png=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
        val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use {zip->
            for(i in 0..63) {if(missing&&i==63)continue;zip.putNextEntry(ZipEntry(if(i==0)"31025.png" else "${100_000+i}.png"));zip.write(png);zip.closeEntry()}
            if(malicious){zip.putNextEntry(ZipEntry("../escape.png"));zip.write(png);zip.closeEntry()}
        }
        return bytes.toByteArray()
    }
    private fun queue(server:MockWebServer,json:String=raw(),zip:ByteArray=archive(),etag:String="one") {
        server.enqueue(MockResponse().setBody(json).setHeader("ETag",etag))
        server.enqueue(MockResponse().setHeader("ETag",etag).setHeader("Content-Length",zip.size))
        server.enqueue(MockResponse().setBody(Buffer().write(zip)))
    }
    private fun scenario(block:suspend (ExamBankStore,MockWebServer,ExamBankRepository)->Unit)=runBlocking {
        val directory=File(context.cacheDir,"exam-bank-test-${UUID.randomUUID()}")
        val server=MockWebServer();server.start()
        try {val store=ExamBankStore(directory);block(store,server,ExamBankRepository(store,baseUrl=server.url("/assets/").toString()))}
        finally {server.shutdown();directory.deleteRecursively()}
    }
    @Test fun firstDownloadPublishesOnlyAfterValidationAndStoresOneCompressedArchive()=scenario {store,server,repo->
        val bytes=archive();queue(server,zip=bytes)
        val seen=mutableListOf<ExamBankStatus>()
        val collector=CoroutineScope(currentCoroutineContext()).launch(Dispatchers.Unconfined){repo.status.collect {seen.add(it)}}
        try {assertTrue(repo.refresh());val snapshot=repo.status.value.snapshot!!
            assertTrue(seen.any {it.updating&&!it.ready});assertTrue(seen.any {it.percent!=null});assertFalse(repo.status.value.updating)
            assertEquals(64,snapshot.questions.size);assertEquals("Électricité",snapshot.questions.first().topic)
            assertTrue("31025" in snapshot.excluded);assertEquals(1,snapshot.questions.first().answer)
            val files=store.directory.walkTopDown().filter {it.isFile}.toList()
            assertEquals(1,files.count {it.extension=="zip"});assertEquals(0,files.count {it.extension=="png"})
            assertEquals(bytes.size.toLong(),files.single {it.extension=="zip"}.length())
            assertEquals(snapshot,store.snapshot());assertNotNull(store.openImage(snapshot.questions.first().image!!).use(android.graphics.BitmapFactory::decodeStream))
        } finally {collector.cancel()}
    }
    @Test fun unchangedAndManualChecksDoNotDownloadTheArchiveAgain()=scenario {_,server,repo->
        queue(server);assertTrue(repo.refresh());val generation=repo.status.value.snapshot!!.generation
        val requests=server.requestCount
        assertTrue(repo.refresh());assertEquals(requests,server.requestCount)
        server.enqueue(MockResponse().setBody(raw()).setHeader("ETag","one"))
        server.enqueue(MockResponse().setHeader("ETag","one").setHeader("Content-Length",archive().size))
        assertTrue(repo.refresh(force=true));assertEquals(requests+2,server.requestCount)
        assertEquals(generation,repo.status.value.snapshot!!.generation)
    }
    @Test fun jsonOnlyCorrectionsReuseTheVerifiedIllustrationsWithoutAnotherNetworkTransfer()=scenario {store,server,repo->
        val bytes=archive();queue(server,zip=bytes);assertTrue(repo.refresh());val previous=repo.status.value.snapshot!!
        server.enqueue(MockResponse().setBody(raw("two",changed=true)).setHeader("ETag","two"))
        server.enqueue(MockResponse().setHeader("ETag","one").setHeader("Content-Length",bytes.size))
        val requests=server.requestCount
        assertTrue(repo.refresh(force=true));assertEquals(requests+2,server.requestCount)
        val fresh=repo.status.value.snapshot!!;assertNotEquals(previous.generation,fresh.generation)
        assertArrayEquals(store.openImage(previous.questions.first().image!!).use {it.readBytes()},store.openImage(fresh.questions.first().image!!).use {it.readBytes()})
    }
    @Test fun updatesPreserveIdsAndOldSessionImagesAndDoNotRebuildCoreContent()=scenario {store,server,repo->
        queue(server);assertTrue(repo.refresh());val old=repo.status.value.snapshot!!
        val oldBytes=store.openImage(old.questions.first().image!!).use {it.readBytes()}
        val core=Content(context,null);val before=core.withExam(old)
        val progress=Progress(DiagnosticContext(context),sideEffects=false)
        progress.answer(old.questions.first().id,true)
        val saved=progress.export()
        queue(server,raw("two",changed=true),archive(color=Color.RED),"two");assertTrue(repo.refresh(force=true))
        val fresh=repo.status.value.snapshot!!;val updated=before.withExam(fresh)
        assertNotEquals(old.generation,fresh.generation)
        assertEquals(old.questions.map {it.id},fresh.questions.map {it.id})
        assertEquals(saved,progress.export());assertTrue(old.questions.first().id in progress.reviews)
        assertArrayEquals(oldBytes,store.openImage(old.questions.first().image!!).use {it.readBytes()})
        assertSame(core.courseQuestions,updated.courseQuestions);assertSame(core.references,updated.references);assertSame(core.procedural,updated.procedural)
        assertEquals(2,store.directory.listFiles()!!.count {it.isDirectory})
        store.pruneAtStartup();assertEquals(1,store.directory.listFiles()!!.count {it.isDirectory});assertEquals(fresh,store.snapshot())
    }
    @Test fun invalidUpdatesCannotReplaceTheUsableBankAndFirstFailureCanRetry()=scenario {store,server,repo->
        queue(server,zip=archive(missing=true));assertFalse(repo.refresh());assertFalse(repo.status.value.ready)
        assertNull(store.snapshot());assertFalse(store.directory.listFiles().orEmpty().any {it.name.startsWith("incoming-")})
        queue(server);assertTrue(repo.refresh());val old=repo.status.value.snapshot!!
        queue(server,raw("two"),archive(malicious=true),"two");assertFalse(repo.refresh(force=true))
        assertEquals(old,repo.status.value.snapshot);assertEquals(old,store.snapshot());assertNotNull(repo.status.value.error)
        assertFalse(File(store.directory.parentFile,"escape.png").exists())
        server.enqueue(MockResponse().setResponseCode(503));assertFalse(repo.refresh(force=true));assertEquals(old,store.snapshot())
    }
    @Test fun interruptedDownloadAndInvalidJsonLeaveNoPartialBank()=scenario {store,server,repo->
        server.enqueue(MockResponse().setBody("{\"version\":\"broken\"}"));assertFalse(repo.refresh());assertEquals(1,server.requestCount)
        val interrupt=java.util.concurrent.atomic.AtomicBoolean(true);val bytes=archive()
        // Route-based responses also handle OkHttp reconnects after a broken pooled connection.
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:okhttp3.mockwebserver.RecordedRequest):MockResponse=when {
                request.path!!.endsWith("questions.json")->MockResponse().setBody(raw()).setHeader("ETag","one")
                request.method=="HEAD"->MockResponse().setHeader("ETag","one").setHeader("Content-Length",bytes.size)
                else->MockResponse().setBody(Buffer().write(bytes)).apply {if(interrupt.getAndSet(false))setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)}
            }
        }
        assertFalse(repo.refresh());assertNull(store.snapshot());assertFalse(store.directory.listFiles().orEmpty().any {it.name.startsWith("incoming-")})
        assertTrue(repo.refresh())
    }
    @Test fun corruptedCacheIsRecognizedAndRepairedWithTheSameStableGeneration()=scenario {store,server,repo->
        queue(server);assertTrue(repo.refresh());val old=repo.status.value.snapshot!!
        val zip=File(store.directory,"${old.generation}/images.zip");val bytes=zip.readBytes();bytes[20]=(bytes[20].toInt() xor 1).toByte();zip.writeBytes(bytes)
        assertNull(store.snapshot())
        val restarted=ExamBankRepository(store,baseUrl=server.url("/assets/").toString());queue(server)
        assertTrue(restarted.refresh());assertEquals(old,restarted.status.value.snapshot);assertEquals(old,store.snapshot())
    }
    @Test fun conditionalChecksAndPausedDiagnosticNetworkAvoidFullTransfers()=runBlocking {
        val directory=File(context.cacheDir,"exam-bank-test-${UUID.randomUUID()}");val store=ExamBankStore(directory)
        val server=MockWebServer();server.start();var time=1_000_000L;var allowed=true
        val repo=ExamBankRepository(store,baseUrl=server.url("/assets/").toString(),now={time},allowed={allowed})
        try {queue(server);assertTrue(repo.refresh());time+=25*60*60_000L
            server.enqueue(MockResponse().setResponseCode(304))
            server.enqueue(MockResponse().setHeader("ETag","one").setHeader("Content-Length",archive().size))
            assertTrue(repo.refresh());repeat(3){server.takeRequest(2,TimeUnit.SECONDS)}
            assertEquals("one",server.takeRequest(2,TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
            val count=server.requestCount;allowed=false;assertFalse(repo.refresh(force=true));assertEquals(count,server.requestCount)
        } finally {server.shutdown();directory.deleteRecursively()}
    }
}
