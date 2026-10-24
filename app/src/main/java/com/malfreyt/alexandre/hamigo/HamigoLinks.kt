package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Public destinations shared by the app, invitations and the privacy website. */
object HamigoLinks {
    const val REPOSITORY = "https://github.com/AlexMalfr/Hamigo"
    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=com.malfreyt.alexandre.hamigo"
    const val PRIVACY = "https://alexandre.malfre.yt/projects/Hamigo/confidentialite.html"
    const val DATA_DELETION = "https://alexandre.malfre.yt/projects/Hamigo/donnees.html"
    const val COMMUNITY_RULES = "https://alexandre.malfre.yt/projects/Hamigo/conditions.html"
    const val FEEDBACK_RETURN = "https://alexandre.malfre.yt/projects/Hamigo/retour.html"

    fun openPlayStore(context: Context, review: Boolean = false) {
        val market = Uri.parse("market://details?id=com.malfreyt.alexandre.hamigo" + if(review) "&showAllReviews=true" else "")
        val intent = Intent(Intent.ACTION_VIEW, market).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage("com.android.vending")
        if(runCatching { context.startActivity(intent) }.isFailure) {
            openLink(context, PLAY_STORE + if(review) "&showAllReviews=true" else "")
        }
    }

    /** A return link only changes navigation; it never imports data or performs a social action. */
    fun isFeedbackReturn(value: String): Boolean = runCatching {
        val uri=Uri.parse(value)
        uri.scheme==BuildConfig.LINK_SCHEME && uri.host=="return" && uri.path in listOf("","/") &&
            uri.userInfo==null && uri.port==-1 && uri.query==null && uri.fragment==null
    }.getOrDefault(false)
}
