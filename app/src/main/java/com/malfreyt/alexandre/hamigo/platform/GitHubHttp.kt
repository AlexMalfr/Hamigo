package com.malfreyt.alexandre.hamigo.platform

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Fixed production origins. Neither invitations nor browser navigation choose a token's destination. */
internal object GitHubHttp : GitHubGateway {
    private val transport = GitHubHttpClient()
    private fun checkDiagnostics(){if(com.malfreyt.alexandre.hamigo.DiagnosticAccess.networkPaused)throw kotlinx.coroutines.CancellationException("Bac à sable actif")}
    override suspend fun api(method: String, path: String, token: String?, body: String?):String {checkDiagnostics();return transport.api(method,path,token,body)}
    override suspend fun rawBackup(rawUrl: String, owner: String, gist: String, fileName: String):String {checkDiagnostics();return transport.rawBackup(rawUrl,owner,gist,fileName)}
    suspend fun oauth(path: String, form: String):JSONObject {checkDiagnostics();return transport.oauth(path,form)}
}

/** Injectable origins are internal and used only by local transport tests. Release uses the defaults. */
internal class GitHubHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(true).build(),
    private val apiOrigin: String = "https://api.github.com",
    private val oauthOrigin: String = "https://github.com"
) : GitHubGateway {
    override suspend fun api(method:String,path:String,token:String?,body:String?):String {
        require(path.startsWith('/') && !path.startsWith("//") && '\\' !in path)
        return request(apiOrigin+path,method,token,body,"application/json",20*1024*1024,false)
    }
    suspend fun oauth(path:String,form:String):JSONObject = JSONObject(
        request(oauthOrigin+path,"POST",null,form,"application/x-www-form-urlencoded",128*1024,true)
    )
    override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String {
        val uri=Uri.parse(rawUrl); val parts=uri.pathSegments
        require(uri.scheme=="https" && uri.host=="gist.githubusercontent.com" && uri.userInfo==null && uri.port==-1 &&
            uri.query==null && uri.fragment==null && parts.size==5 && parts[0].equals(owner,true) &&
            parts[1].equals(gist,true) && parts[2]=="raw" && Regex("[a-fA-F0-9]{40,64}").matches(parts[3]) && parts[4]==fileName) {
            "Adresse de sauvegarde GitHub inattendue."
        }
        return request(rawUrl,"GET",null,null,"application/json",8*1024*1024,false)
    }
    private suspend fun request(url:String,method:String,token:String?,body:String?,type:String,limit:Int,oauth:Boolean):String =
        withContext(Dispatchers.IO) {
            val request=Request.Builder().url(url).header("Accept",if(oauth)"application/json" else "application/vnd.github+json")
                .header("User-Agent","Hamigo-Android")
            if(!oauth) request.header("X-GitHub-Api-Version","2026-03-10")
            token?.let {request.header("Authorization","Bearer $it")}
            request.method(method,body?.toRequestBody("$type; charset=utf-8".toMediaType()))
            try {
                client.newCall(request.build()).awaitContent(limit).let {response ->
                    currentCoroutineContext().ensureActive()
                    val content=response.content
                    // OAuth error replies can be HTTP 400 and still contain the polling protocol.
                    if(!response.isSuccessful && !(oauth && response.code==400 && runCatching {JSONObject(content).has("error")}.getOrDefault(false))) {
                        throw SocialException(httpMessage(response.code,response.remaining),response.code)
                    }
                    content
                }
            } catch(e:SocialException) {throw e}
            catch(e:IOException) {
                val message=when(e) {
                    is UnknownHostException -> "L’adresse de GitHub est inaccessible. Vérifie la connexion ou le DNS privé."
                    is SocketTimeoutException -> "GitHub met trop de temps à répondre. La connexion sera réessayée."
                    is SSLException -> "La connexion sécurisée à GitHub a échoué. Vérifie la date de l’appareil et le réseau."
                    else -> "La connexion à GitHub a été interrompue. Tes révisions restent enregistrées."
                }
                throw GitHubNetworkException(message,e)
            }
        }
    private fun httpMessage(status:Int,remaining:String?)=when(status) {
        401->"L’autorisation GitHub a expiré. Reconnecte ton compte."
        403->if(remaining=="0")"La limite GitHub est atteinte. Réessaie plus tard." else "GitHub refuse l’accès aux Gists. Relance l’autorisation."
        404->"Gist introuvable. Vérifie l’invitation ou reconnecte ton compte."
        422->"GitHub n’a pas accepté la sauvegarde. Tes révisions restent enregistrées."
        429->"Trop de demandes GitHub. Réessaie plus tard."
        in 500..599->"GitHub est momentanément indisponible. Tes révisions restent enregistrées."
        else->"GitHub a répondu avec une erreur HTTP $status."
    }
}

private data class GitHubReply(val code:Int,val remaining:String?,val content:String) {
    val isSuccessful get()=code in 200..299
}
private suspend fun Call.awaitContent(limit:Int):GitHubReply=suspendCancellableCoroutine {continuation ->
    continuation.invokeOnCancellation {cancel()}
    enqueue(object:Callback {
        override fun onFailure(call:Call,e:IOException) {if(!continuation.isCancelled)continuation.resumeWithException(e)}
        override fun onResponse(call:Call,response:Response) {
            try {
                response.use {
                    val raw=ByteArrayOutputStream()
                    response.body?.byteStream()?.use {input ->
                        val buffer=ByteArray(8192)
                        while(continuation.isActive) {
                            val size=input.read(buffer);if(size<0)break
                            if(raw.size()+size>limit)throw SocialException("La réponse GitHub est trop volumineuse.")
                            raw.write(buffer,0,size)
                        }
                    }
                    if(continuation.isActive)continuation.resume(GitHubReply(response.code,response.header("X-RateLimit-Remaining"),raw.toString("UTF-8")))
                }
            } catch(e:Exception) {if(continuation.isActive)continuation.resumeWithException(e)}
        }
    })
}
