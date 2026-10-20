package com.malfreyt.alexandre.hamigo.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** In-process transport fixture: no DNS, external request, account or credential is used. */
@RunWith(AndroidJUnit4::class)
class GitHubBackupSizeInstrumentedTest {
    private suspend fun read(size:Int):Int {
        val http=OkHttpClient.Builder().addInterceptor {chain->
            assertNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("x".repeat(size).toResponseBody("application/json".toMediaType())).build()
        }.build()
        return GitHubHttpClient(http).rawBackup(
            "https://gist.githubusercontent.com/fixture-owner/abcde12345/raw/${"a".repeat(40)}/hamigo-backup.json",
            "fixture-owner","abcde12345","hamigo-backup.json").length
    }

    @Test fun theWholeTwelveMiBBackupEnvelopeIsReadableAndOneMoreByteIsRejected()=runBlocking {
        val limit=12*1024*1024
        assertEquals(limit,read(limit))
        try {
            read(limit+1)
            fail("A raw backup above the complete envelope must be rejected")
        } catch(expected:SocialException) {
            assertTrue(expected.message.orEmpty().contains("volumineuse"))
        }
    }
}
