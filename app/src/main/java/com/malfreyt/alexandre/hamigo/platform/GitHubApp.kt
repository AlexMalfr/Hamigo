package com.malfreyt.alexandre.hamigo.platform

import com.malfreyt.alexandre.hamigo.BuildConfig

/** Shared OAuth application. PKCE protects the code; its native client secret is not confidential. */
object GitHubApp {
    const val CLIENT_ID = "Ov23lihnAxjWv1FWyk5D"
    internal val clientSecret get() = BuildConfig.GITHUB_CLIENT_SECRET
    internal val directConnectionEnabled get() = clientSecret.isNotBlank()
}
