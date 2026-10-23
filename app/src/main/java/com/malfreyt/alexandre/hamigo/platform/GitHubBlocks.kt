package com.malfreyt.alexandre.hamigo.platform

import kotlinx.coroutines.CancellationException
import org.json.JSONArray

/** Read-only mirror of GitHub decisions. Hamigo never creates or removes a block. */
internal class GitHubBlocks private constructor(private val accounts:List<GitHubIdentity>) {
    fun contains(identity:GitHubIdentity?):Boolean = identity!=null && accounts.any {
        if(it.id!=null && identity.id!=null) it.id==identity.id else it.login.equals(identity.login,true)
    }
    fun toJson()=JSONArray(accounts.map {it.toJson()}).toString()
    companion object {
        fun parse(raw:String):GitHubBlocks {
            val list=JSONArray(raw)
            require(list.length()<=10_000){"La liste de blocages GitHub est trop volumineuse."}
            return GitHubBlocks((0 until list.length()).map {GitHubIdentity.fromJson(list.getJSONObject(it))})
        }
        suspend fun read(gateway:GitHubGateway,token:String):GitHubBlocks {
            val result=JSONArray()
            try {
                for(page in 1..101) {
                    val list=JSONArray(gateway.api("GET","/user/blocks?per_page=100&page=$page",token))
                    require(list.length()<=100){"Liste de blocages GitHub inattendue."}
                    for(index in 0 until list.length()) {
                        val account=GitHubIdentity.fromJson(list.getJSONObject(index))
                        require(account.id!=null){"Identité d’un compte bloqué invalide."}
                        result.put(account.toJson())
                    }
                    require(result.length()<=10_000){"La liste de blocages GitHub est trop volumineuse."}
                    if(list.length()<100)return parse(result.toString())
                }
                throw SocialException("La liste de blocages GitHub est trop volumineuse.")
            } catch(e:CancellationException){throw e}
            catch(e:SocialException) {
                if(e.httpStatus in setOf(401,403,404) && e.message?.contains("limite")!=true)
                    throw SocialException("GitHub ne permet pas de lire tes blocages. Déconnecte puis reconnecte ton compte pour accorder l’autorisation Gists et profil.",e.httpStatus,e)
                throw e
            }
        }
    }
}
