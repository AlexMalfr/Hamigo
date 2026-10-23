package com.malfreyt.alexandre.hamigo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Metadata only before the first explicit Continue. The actual bank still uses WorkManager. */
internal object ExamBankDownloads {
    // Last observed upstream JSON + ZIP, 24 October 2026. Clearly labelled as an estimate offline.
    const val ESTIMATED_BYTES=36_119_152L
    private val client=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS)
        .callTimeout(5,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    fun allowed(context:Context)=context.getSharedPreferences("hamigo_exam_download",Context.MODE_PRIVATE).getBoolean("confirmed",false) ||
        context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getBoolean("welcomed",false)
    fun confirm(context:Context) {
        check(context.getSharedPreferences("hamigo_exam_download",Context.MODE_PRIVATE).edit().putBoolean("confirmed",true).commit())
    }
    suspend fun size(client:OkHttpClient=this.client,baseUrl:String="https://exam1.r-e-f.org/assets/"):Long=coroutineScope {
        val lengths=listOf("questions.json" to ExamBankFormat.MAX_JSON.toLong(),"questions.zip" to ExamBankFormat.MAX_ZIP.toLong()).map {(file,limit)->
            async {withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(baseUrl+file).head().header("Accept-Encoding","identity")
                    .header("User-Agent","Hamigo-Android").build()).execute().use {response->
                    check(response.isSuccessful)
                    requireNotNull(response.header("Content-Length")?.toLongOrNull()).also {require(it in 1..limit)}
                }
            }}
        }
        lengths.sumOf {it.await()}
    }
}
