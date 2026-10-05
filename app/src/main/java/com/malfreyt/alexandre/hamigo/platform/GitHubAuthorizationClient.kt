package com.malfreyt.alexandre.hamigo.platform

/** Production credential/transport boundary; tests use an isolated exchange without GitHub access. */
internal interface GitHubAuthorizationClient {
    val enabled: Boolean
    suspend fun exchange(session: GitHubPkce.Session, code: String): String
}

internal object DefaultGitHubAuthorizationClient : GitHubAuthorizationClient {
    override val enabled get() = GitHubApp.directConnectionEnabled
    override suspend fun exchange(session: GitHubPkce.Session, code: String) =
        GitHubPkce.exchange(GitHubApp.CLIENT_ID, GitHubApp.clientSecret, session, code)
}
