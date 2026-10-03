package com.malfreyt.alexandre.hamigo.platform

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object NativeShare {
    fun progressImage(context: Context, progress: ShareProgress) {
        val image = renderProgressImage(context, progress)
        shareFile(context, image, "image/png", "Ma progression Hamigo",
            "${progress.name} : ${progress.xp} XP, ${progress.streak} jours de série sur Hamigo 📻")
    }

    fun nudge(context: Context, name: String) {
        val text = "Hé ${name.trim().take(48)} ! Une petite session Hamigo aujourd'hui ? 📻 " +
            "On garde nos séries et on se rapproche du certificat radioamateur ensemble !"
        launchChooser(context, Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text), "Envoyer une petite onde")
    }

    fun text(context: Context, text: String, title: String = "Partager Hamigo") {
        launchChooser(context, Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text), title)
    }

    /** Full backup is only sent to the app explicitly selected in Android's share sheet. */
    fun backup(context: Context, json: String) {
        val file = newFile(context, "hamigo-sauvegarde-", ".json")
        file.writeText(json, Charsets.UTF_8)
        shareFile(context, file, "application/json", "Sauvegarder ma progression Hamigo")
    }

    fun snapshot(context: Context, progress: ShareProgress) {
        val file = newFile(context, "hamigo-progression-", ".json")
        file.writeText(progress.toJson(), Charsets.UTF_8)
        shareFile(context, file, "application/json", "Partager ma progression Hamigo")
    }

    /** Standalone Canvas card: no network or permission required. */
    fun renderProgressImage(context: Context, progress: ShareProgress): File {
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val teal = Color.rgb(17, 111, 100)
        val ink = Color.rgb(24, 69, 65)
        val mint = Color.rgb(190, 236, 213)
        val coral = Color.rgb(246, 124, 101)
        val cream = Color.rgb(255, 249, 233)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        val regular = Typeface.create("sans-serif", Typeface.NORMAL)
        fun text(value: String, x: Float, y: Float, size: Float, color: Int, heavy: Boolean = true,
                 align: Paint.Align = Paint.Align.LEFT) {
            paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size
            paint.typeface = if (heavy) bold else regular; paint.textAlign = align
            canvas.drawText(value, x, y, paint)
        }
        fun rounded(left: Float, top: Float, right: Float, bottom: Float, color: Int, radius: Float = 42f) {
            paint.color = color; paint.style = Paint.Style.FILL
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
        }
        canvas.drawColor(cream)
        rounded(40f, 40f, 1040f, 660f, teal, 58f)
        text("HAMIGO", 92f, 133f, 40f, cream)
        text("La bonne fréquence", 92f, 192f, 30f, mint, false)
        // A friendly radio with an animated-wave silhouette, matching the learning theme.
        rounded(340f, 275f, 740f, 528f, Color.rgb(6, 83, 75), 60f)
        rounded(326f, 258f, 726f, 510f, mint, 60f)
        paint.color = mint; paint.strokeWidth = 20f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(416f, 265f, 382f, 205f, paint)
        paint.color = cream; paint.style = Paint.Style.FILL
        canvas.drawCircle(436f, 348f, 43f, paint); canvas.drawCircle(610f, 348f, 43f, paint)
        paint.color = ink
        canvas.drawCircle(445f, 350f, 16f, paint); canvas.drawCircle(601f, 350f, 16f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 11f
        val smile = Path().apply { moveTo(475f, 414f); quadTo(530f, 467f, 584f, 414f) }
        canvas.drawPath(smile, paint)
        paint.color = coral; paint.style = Paint.Style.FILL
        canvas.drawOval(RectF(372f, 391f, 422f, 414f), paint)
        canvas.drawOval(RectF(630f, 391f, 680f, 414f), paint)
        paint.color = mint; paint.style = Paint.Style.STROKE; paint.strokeWidth = 12f
        canvas.drawArc(RectF(194f, 300f, 302f, 460f), 115f, 130f, false, paint)
        canvas.drawArc(RectF(751f, 300f, 860f, 460f), -65f, 130f, false, paint)
        canvas.drawArc(RectF(141f, 268f, 267f, 490f), 115f, 130f, false, paint)
        canvas.drawArc(RectF(789f, 268f, 915f, 490f), -65f, 130f, false, paint)
        text("Cap sur le certificat !", 540f, 600f, 46f, cream, align = Paint.Align.CENTER)
        val cleanName = progress.name.replace(Regex("[\\p{Cntrl}\\r\\n]"), " ").trim().take(48)
        paint.textSize = 46f; paint.typeface = bold
        var displayName = cleanName.ifEmpty { "Radioamateur en devenir" }
        while (paint.measureText(displayName) > 860f && displayName.length > 2)
            displayName = displayName.dropLast(2).trimEnd() + "…"
        text(displayName, 92f, 749f, 46f, ink)
        text("Une petite onde chaque jour.", 92f, 806f, 30f, teal, false)
        fun stat(left: Float, top: Float, right: Float, value: String, label: String, color: Int) {
            rounded(left, top + 8f, right, top + 211f, Color.rgb(225, 231, 213), 32f)
            rounded(left, top, right, top + 200f, color, 32f)
            text(value, left + 34f, top + 88f, 58f, ink)
            text(label, left + 34f, top + 148f, 25f, ink, false)
        }
        stat(80f, 868f, 528f, "${progress.xp.coerceAtLeast(0)}", "XP collectionnés", mint)
        stat(554f, 868f, 1000f, "${progress.streak.coerceAtLeast(0)} jours", "de série", Color.rgb(255, 192, 153))
        rounded(80f, 1107f, 1000f, 1224f, Color.WHITE, 30f)
        text("${progress.lessons.coerceAtLeast(0)} leçons", 116f, 1180f, 37f, ink)
        text("+${progress.weeklyXp.coerceAtLeast(0)} XP cette semaine", 964f, 1178f, 28f, teal,
            false, Paint.Align.RIGHT)
        text("APPRENDS • RÉVISE • RAYONNE", 540f, 1295f, 22f, teal, align = Paint.Align.CENTER)
        val file = newFile(context, "hamigo-progression-", ".png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun newFile(context: Context, prefix: String, suffix: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        dir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        return File.createTempFile(prefix, suffix, dir)
    }

    private fun shareFile(context: Context, file: File, mime: String, title: String, text: String? = null) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(title, uri)
        text?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        launchChooser(context, intent, title)
    }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
