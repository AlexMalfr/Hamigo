package com.malfreyt.alexandre.hamigo.platform

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Only the shareable statistics Gist ID belongs in an invitation; the complete backup ID never does. */
object FriendInvite {
    const val HTTPS_BASE = "https://alexmalfr.github.io/hamigo/"
    private val idPattern = Regex("[a-fA-F0-9]{5,64}")

    fun link(gistIdOrUrl: String): String = HTTPS_BASE + "?invite=" + GitHubSync.gistId(gistIdOrUrl).lowercase()

    fun parse(value: String): String? = runCatching {
        if (value.length > 512) return null
        val uri = Uri.parse(value)
        if (uri.userInfo != null || uri.port != -1 || uri.fragment != null) return null
        val expected = when {
            uri.scheme == "https" && uri.host == "alexmalfr.github.io" && uri.path in listOf("/hamigo/", "/hamigo") -> true
            uri.scheme == "hamigo" && uri.host == "join" && uri.path in listOf("", "/") -> true
            else -> false
        }
        if (!expected || uri.queryParameterNames != setOf("invite") || uri.getQueryParameters("invite").size != 1) return null
        uri.getQueryParameter("invite")?.takeIf { idPattern.matches(it) }?.lowercase()
    }.getOrNull()

    fun qr(link: String, size: Int = 640): Bitmap {
        require(parse(link) != null && link.startsWith(HTTPS_BASE)) { "Invitation Hamigo invalide." }
        require(size in 128..2048)
        val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, size, size, mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8"))
        val pixels = IntArray(size * size) { index -> if (matrix[index % size, index / size]) Color.rgb(23, 63, 66) else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
