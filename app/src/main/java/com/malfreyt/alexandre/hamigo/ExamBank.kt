package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** A session keeps its generation's image paths even when a new bank becomes available. */
data class ExamBankSnapshot(val generation:String, val version:String, val questions:List<Question>, val excluded:Set<String>)

internal data class ExamBankStatus(
    val snapshot:ExamBankSnapshot?=null, val updating:Boolean=false, val percent:Int?=null,
    val error:String?=null, val checkedAt:Long=0L
) { val ready get()=snapshot!=null }

/** Independent reader of the upstream JSON schema. Original answers and numeric IDs are retained. */
internal object ExamBankFormat {
    const val MAX_JSON=16L*1024*1024
    const val MAX_ZIP=128L*1024*1024
    const val MAX_IMAGE=2L*1024*1024
    private val imageName=Regex("[0-9]{1,10}\\.png")
    private val historical=setOf("31025","34705","34713","34714","34715","34716","34717","34718","34766","35061","23814")
    private val deprecated=Regex("suppression de cette disposition|aurait.*dû être retirée|ne devrait plus y avoir|résolution a été abrogée",RegexOption.IGNORE_CASE)
    fun parse(raw:String,generation:String):ExamBankSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size<=MAX_JSON)
        val root=JSONObject(raw.removePrefix("\uFEFF"))
        val version=root.getString("version").also {require(it.isNotBlank()&&it.length<=128)}
        val themes=root.getJSONArray("themes").objects().associate {it.getInt("num") to it.getString("nom").trim()}
        require(themes.isNotEmpty()&&themes.values.all {it.isNotBlank()})
        val items=root.getJSONArray("questions")
        require(items.length() in 40..20_000&&items.length()==root.getInt("nbQuestions"))
        val questions=items.objects().map {q->
            val id=q.get("num").toString();require(id.matches(Regex("[0-9]{1,10}")))
            val prompt=q.getString("question");require(prompt.isNotBlank())
            val choices=q.getJSONArray("propositions").strings();require(choices.size in 2..12)
            val answer=q.getInt("reponse");require(answer in choices.indices)
            val theme=q.getInt("themeNum");val topic=themes.getValue(theme)
            Question(id,prompt,choices,answer,if(q.isNull("commentaire"))"" else q.optString("commentaire"),
                topic=topic,section=if(theme in 200..299)"technique" else "regulation",
                image="exam-bank/$generation/$id.png",source="https://exam1.r-e-f.org/questions-listing?questionId=$id")
        }
        require(questions.map {it.id}.toSet().size==questions.size)
        val excluded=historical+questions.filter {deprecated.containsMatchIn(it.explanation)}.map {it.id}
        require(listOf("regulation","technique").all {section->questions.count {it.section==section&&it.id !in excluded}>=20})
        return ExamBankSnapshot(generation,version,questions,excluded)
    }
    /** Bound both compressed and expanded data; verify every CRC, PNG header and required entry. */
    suspend fun validateArchive(file:File,snapshot:ExamBankSnapshot) {
        require(file.length() in 1..MAX_ZIP)
        ZipFile(file).use {zip->
            val names=hashSetOf<String>();var expanded=0L
            val entries=zip.entries()
            while(entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val e=entries.nextElement()
                require(!e.isDirectory&&imageName.matches(e.name)&&names.add(e.name)&&names.size<=20_000)
                require(e.size in 24..MAX_IMAGE);expanded+=e.size;require(expanded<=256L*1024*1024)
                val bytes=zip.getInputStream(e).use {readBounded(it,MAX_IMAGE)}
                require(bytes.size.toLong()==e.size&&CRC32().apply {update(bytes)}.value==e.crc)
                require(bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,13,10,26,10)))
                fun dimension(start:Int)=(0..3).fold(0){n,i->(n shl 8) or (bytes[start+i].toInt() and 255)}
                val width=dimension(16);val height=dimension(20)
                require(width in 1..4096&&height in 1..4096&&width.toLong()*height<=8_000_000)
            }
            require(snapshot.questions.all {"${it.id}.png" in names})
        }
    }
    fun readBounded(input:InputStream,maximum:Long):ByteArray {
        val result=ByteArrayOutputStream();val buffer=ByteArray(16*1024)
        while(true){val n=input.read(buffer);if(n<0)break;require(result.size().toLong()+n<=maximum);result.write(buffer,0,n)}
        return result.toByteArray()
    }
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun hash(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {stream->val buffer=ByteArray(64*1024);while(true){val n=stream.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
}

/** No extracted image tree and no backup/progress data. Only a fully checked generation is published. */
internal class ExamBankStore(val directory:File) {
    private val pointer=AtomicFile(File(directory,"current.json"))
    fun metadata():JSONObject?=runCatching {pointer.openRead().use {JSONObject(it.bufferedReader().readText())}}.getOrNull()
    fun snapshot():ExamBankSnapshot?=runCatching {
        val info=metadata()?:return null
        val generation=info.getString("generation");require(generation.matches(Regex("[a-f0-9]{64}")))
        val folder=File(directory,generation);require(File(folder,"images.zip").length()==info.getLong("zipBytes"))
        require(ExamBankFormat.hash(File(folder,"images.zip"))==info.getString("zipHash"))
        val raw=File(folder,"questions.json").readText();require(ExamBankFormat.hash(raw.toByteArray())==info.getString("jsonHash"))
        val bank=ExamBankFormat.parse(raw,generation)
        ZipFile(File(folder,"images.zip")).use {zip->require(bank.questions.all {zip.getEntry("${it.id}.png")!=null})}
        bank
    }.getOrNull()
    fun publish(metadata:JSONObject) {
        directory.mkdirs();val stream=pointer.startWrite()
        try {stream.write(metadata.toString().toByteArray());pointer.finishWrite(stream)}
        catch(e:Exception){pointer.failWrite(stream);throw e}
    }
    /** Called at process initialization, before any sessions can hold older image paths. */
    fun pruneAtStartup() {
        val current=metadata()?.optString("generation")
        directory.listFiles()?.filter {it.isDirectory&&(it.name.matches(Regex("[a-f0-9]{64}"))||it.name.startsWith("incoming-"))&&it.name!=current}
            ?.forEach {it.deleteRecursively()}
    }
    fun openImage(path:String):InputStream {
        val match=Regex("exam-bank/([a-f0-9]{64})/([0-9]{1,10}\\.png)").matchEntire(path) ?: error("Invalid image path")
        return ZipFile(File(directory,"${match.groupValues[1]}/images.zip")).use {zip->
            val entry=requireNotNull(zip.getEntry(match.groupValues[2]));require(entry.size in 1..ExamBankFormat.MAX_IMAGE)
            ByteArrayInputStream(zip.getInputStream(entry).use {ExamBankFormat.readBounded(it,ExamBankFormat.MAX_IMAGE)})
        }
    }
    companion object {
        fun forContext(context:Context)=ExamBankStore(File(context.noBackupFilesDir,"exam-bank"))
    }
}

/** Shared by the foreground and WorkManager; a failed update never removes the usable bank. */
internal class ExamBankRepository(
    private val store:ExamBankStore,
    private val client:OkHttpClient=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(30,TimeUnit.SECONDS).callTimeout(8,TimeUnit.MINUTES).build(),
    private val baseUrl:String="https://exam1.r-e-f.org/assets/",
    private val now:()->Long=System::currentTimeMillis,
    private val allowed:()->Boolean={true}
) {
    private val mutable=MutableStateFlow(ExamBankStatus())
    val status=mutable.asStateFlow()
    private val mutex=Mutex()
    private var initialized=false
    suspend fun initialize()=withContext(Dispatchers.IO) {
        mutex.lock()
        try {if(!initialized){store.pruneAtStartup();mutable.value=ExamBankStatus(store.snapshot(),checkedAt=store.metadata()?.optLong("checkedAt")?:0);initialized=true}}
        finally {mutex.unlock()}
    }
    suspend fun refresh(force:Boolean=false):Boolean=withContext(Dispatchers.IO) {
        initialize()
        if(!allowed()||!mutex.tryLock())return@withContext false
        var temporary:File?=null
        try {
            val before=mutable.value
            if(!force&&before.ready&&(now()-before.checkedAt).coerceAtLeast(0)<DAY)return@withContext true
            mutable.value=before.copy(updating=true,percent=null,error=null)
            val prior=store.metadata()
            val request=Request.Builder().url(baseUrl+"questions.json").header("User-Agent","Hamigo-Android")
            if(before.ready&&!force)prior?.optString("jsonEtag")?.takeIf {it.isNotBlank()}?.let {request.header("If-None-Match",it)}
            val response=client.newCall(request.build()).execute()
            var jsonEtag=""
            val raw=response.use {r->
                require(r.isSuccessful||r.code==304)
                jsonEtag=r.header("ETag").orEmpty()
                if(r.code==304){require(before.ready);File(store.directory,"${before.snapshot!!.generation}/questions.json").readBytes()}
                else requireNotNull(r.body).let {body->require(body.contentLength()<=ExamBankFormat.MAX_JSON);body.byteStream().use {ExamBankFormat.readBounded(it,ExamBankFormat.MAX_JSON)}}
            }
            currentCoroutineContext().ensureActive();check(allowed())
            val rawHash=ExamBankFormat.hash(raw)
            // Read JSON before downloading images so incompatible upstream formats fail cheaply.
            val parsed=ExamBankFormat.parse(raw.toString(Charsets.UTF_8),"pending")
            val fingerprint=client.newCall(Request.Builder().url(baseUrl+"questions.zip").head().build()).execute().use {r->
                if(!r.isSuccessful)"" else listOf(r.header("ETag").orEmpty(),r.header("Last-Modified").orEmpty(),r.header("Content-Length").orEmpty()).let {fields->if(fields.all {it.isBlank()})"" else fields.joinToString("|")}
            }
            val unchanged=before.ready&&prior?.optString("jsonHash")==rawHash&&fingerprint.isNotBlank()&&fingerprint==prior.optString("zipFingerprint")
            if(unchanged) {
                val metadata=checkNotNull(prior)
                val info=JSONObject(metadata.toString()).put("checkedAt",now()).put("jsonEtag",jsonEtag.ifBlank {metadata.optString("jsonEtag")})
                store.publish(info);mutable.value=before.copy(updating=false,percent=null,error=null,checkedAt=now());return@withContext true
            }
            store.directory.mkdirs();temporary=File(store.directory,"incoming-${UUID.randomUUID()}").also {check(it.mkdir())}
            val zip=File(temporary,"images.zip")
            if(before.ready&&fingerprint.isNotBlank()&&fingerprint==prior?.optString("zipFingerprint")) {
                // A JSON-only correction must not redownload all the illustrations.
                File(store.directory,"${before.snapshot!!.generation}/images.zip").inputStream().use {input->zip.outputStream().use {input.copyTo(it)}}
                mutable.value=mutable.value.copy(percent=95)
            } else client.newCall(Request.Builder().url(baseUrl+"questions.zip").header("User-Agent","Hamigo-Android").build()).execute().use {r->
                check(r.isSuccessful);val body=requireNotNull(r.body);val expected=body.contentLength()
                require(expected<=ExamBankFormat.MAX_ZIP)
                body.byteStream().use {input->zip.outputStream().use {output->
                    val buffer=ByteArray(64*1024);var total=0L;var percent=-1
                    while(true){
                        currentCoroutineContext().ensureActive();check(allowed())
                        val n=input.read(buffer);if(n<0)break;total+=n;require(total<=ExamBankFormat.MAX_ZIP);output.write(buffer,0,n)
                        val next=if(expected>0)(total*95/expected).toInt().coerceIn(0,95) else -1
                        if(next!=percent){percent=next;mutable.value=mutable.value.copy(percent=next.takeIf {it>=0})}
                    }
                    require(total>0&&(expected<0||total==expected))
                }}
            }
            mutable.value=mutable.value.copy(percent=96)
            ExamBankFormat.validateArchive(zip,parsed)
            val zipHash=ExamBankFormat.hash(zip);val generation=ExamBankFormat.hash((rawHash+zipHash).toByteArray())
            File(temporary,"questions.json").writeBytes(raw)
            val folder=File(store.directory,generation)
            if(folder.exists()) {
                // Identical generation. Replace a corrupted cache atomically, not a user's data.
                val existing=File(folder,"images.zip")
                if(runCatching {ExamBankFormat.hash(existing)}.getOrNull()!=zipHash)check(zip.renameTo(existing))
                val jsonFile=AtomicFile(File(folder,"questions.json"));val out=jsonFile.startWrite()
                try {out.write(raw);jsonFile.finishWrite(out)} catch(e:Exception){jsonFile.failWrite(out);throw e}
            } else check(temporary.renameTo(folder))
            currentCoroutineContext().ensureActive();check(allowed())
            val info=JSONObject().put("generation",generation).put("version",parsed.version).put("jsonHash",rawHash)
                .put("jsonEtag",jsonEtag).put("zipHash",zipHash).put("zipBytes",File(folder,"images.zip").length())
                .put("zipFingerprint",fingerprint).put("checkedAt",now()).put("zipVerifiedAt",now())
            store.publish(info)
            mutable.value=ExamBankStatus(parsed.copy(generation=generation,questions=parsed.questions.map {it.copy(image="exam-bank/$generation/${it.id}.png")}),checkedAt=now())
            true
        } catch(e:CancellationException) {mutable.value=mutable.value.copy(updating=false,percent=null);throw e}
        catch(_:Exception) {mutable.value=mutable.value.copy(updating=false,percent=null,error="Exam’1 est momentanément inaccessible. Réessaie avec une connexion Internet.");false}
        finally {temporary?.takeIf {it.name.startsWith("incoming-")}?.deleteRecursively();mutex.unlock()}
    }
    companion object {
        private const val DAY=24*60*60_000L
        private val instances=ConcurrentHashMap<String,ExamBankRepository>()
        fun forContext(context:Context):ExamBankRepository {
            val app=context.applicationContext;val store=ExamBankStore.forContext(app)
            return instances.computeIfAbsent(store.directory.absolutePath){ExamBankRepository(store,allowed={app !is DiagnosticContext&&!DiagnosticAccess.syncPaused(app)})}
        }
    }
}
