package com.malfreyt.alexandre.hamigo.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.malfreyt.alexandre.hamigo.PicoRenderer
import com.malfreyt.alexandre.hamigo.dayCount

/** Artwork uses the standard BigPictureStyle template, preserving Android's accessible controls. */
object ReminderArtwork {
    fun render(content: ReminderMessage): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ink = Color.rgb(23, 63, 66)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(content.background)
        paint.color = Color.argb(95, 255, 255, 255)
        canvas.drawCircle(974f, 130f, 268f, paint)
        canvas.drawCircle(110f, 552f, 158f, paint)
        paint.color = content.accent
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 31f
        canvas.drawText("LA PETITE ONDE DU JOUR", 48f, 67f, paint)

        // Genuine text remains outside the image too, in the notification title and summary.
        drawText(canvas, content.title, 48f, 100f, 639, 48f, ink, true)
        drawText(canvas, content.message, 48f, 236f, 643, 34f, ink, false)
        PicoRenderer.draw(canvas, RectF(728f, 125f, 1058f, 455f), content.mood, content.pose, .3f)

        paint.color = content.accent
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val metric = when (content.context) {
            ReminderContext.CONTINUE -> "${dayCount(content.streak)} de série"
            ReminderContext.RESTART -> "Une nouvelle série, à ton rythme"
            ReminderContext.START -> "Ta première série commence ici"
            else -> "${content.todayXp} / ${content.goal} XP aujourd'hui"
        }
        canvas.drawText(metric, 48f, 455f, paint)
        val track = RectF(48f, 479f, 684f, 493f)
        paint.color = Color.argb(135, 255, 255, 255)
        canvas.drawRoundRect(track, 7f, 7f, paint)
        val ratio = (content.todayXp.toFloat() / content.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (ratio > 0f) {
            paint.color = content.accent
            canvas.drawRoundRect(RectF(track.left, track.top, track.left + track.width() * ratio, track.bottom), 7f, 7f, paint)
        }
        return bitmap
    }

    fun avatar(content: ReminderMessage): Bitmap {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = content.background }
        canvas.drawCircle(96f, 96f, 96f, paint)
        PicoRenderer.draw(canvas, RectF(9f, 9f, 183f, 183f), content.mood, content.pose, .3f)
        return bitmap
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, width: Int,
                         size: Float, color: Int, bold: Boolean) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setLineSpacing(4f, 1f).setMaxLines(if (bold) 2 else 4).build()
        val save = canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restoreToCount(save)
    }
}
