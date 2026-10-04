package com.malfreyt.alexandre.hamigo.platform

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.net.Uri
import androidx.core.content.FileProvider
import com.malfreyt.alexandre.hamigo.MascotMood
import com.malfreyt.alexandre.hamigo.MascotPose
import com.malfreyt.alexandre.hamigo.PicoRenderer
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Share cards are drawn locally, with the same Pico artwork as the app and notifications. */
object NativeShare {
    fun resultsImage(context:Context,results:ShareResults) {
        shareFile(context,renderResultsImage(context,results),"image/png","Mes résultats Hamigo",
            "${cleanName(results.name)} : ${results.correct}/${results.total} sur ${results.title}, avec Hamigo 📻")
    }

    fun renderResultsImage(context:Context,results:ShareResults):File=posterFile(context,"hamigo-resultats-") {card ->
        card.brand("MES RÉSULTATS")
        PicoRenderer.draw(card.canvas,RectF(800f,54f,1030f,284f),
            if(results.successful)MascotMood.CELEBRATE else MascotMood.DETERMINED,
            if(results.successful)MascotPose.JUMP else MascotPose.POINT)
        card.text("Une nouvelle étape",64f,215f,57f,Ink)
        card.fittedText(cleanName(results.name),64f,274f,37f,Teal,716f)
        card.fittedText(results.title,64f,317f,27f,Muted,950f,heavy=false)
        val total=results.total.coerceAtLeast(1)
        val correct=results.correct.coerceIn(0,total)
        val minutes=results.elapsedMillis.coerceAtLeast(0)/60_000
        val seconds=results.elapsedMillis.coerceAtLeast(0)/1000%60
        card.stat(RectF(64f,350f,532f,467f),"$correct / ${results.total.coerceAtLeast(0)}","bonnes réponses",Mint,48f)
        card.stat(RectF(548f,350f,1016f,467f),"${minutes}m ${seconds}s","temps de réflexion",GoldLight,48f)
        card.stat(RectF(64f,483f,532f,600f),"+ ${format(results.gainedXp)}","XP gagnés",Mist,48f)
        card.stat(RectF(548f,483f,1016f,600f),format(results.unanswered),"sans réponse",Peach,48f)
        card.panel(RectF(64f,629f,1016f,1180f))
        if(results.exam) {
            card.text("Deux épreuves, deux repères",96f,691f,36f,Ink)
            card.text("Objectif : au moins 10 / 20 dans chacune",96f,732f,24f,Teal,heavy=false)
            val plot=RectF(194f,798f,953f,1070f)
            listOf(0,5,10,15,20).forEach {score ->
                val y=plot.bottom-plot.height()*score/20f
                card.rounded(RectF(plot.left,y-1,plot.right,y+1),Mist,0f)
                card.text(score.toString(),plot.left-20f,y+8f,21f,Muted,heavy=false,align=Paint.Align.RIGHT)
            }
            listOf("Réglementation" to results.regulationScore!!,"Technique" to results.techniqueScore!!).forEachIndexed {index,entry ->
                val x=plot.left+plot.width()*(index+.5f)/2f
                val value=entry.second.coerceIn(0,20)
                val height=plot.height()*value/20f
                card.rounded(RectF(x-75f,plot.bottom-height,x+75f,plot.bottom),if(index==0)Teal else Coral,14f)
                card.text("$value / 20",x,plot.bottom-height-18f,29f,Ink,align=Paint.Align.CENTER)
                card.text(entry.first,x,1122f,25f,Ink,align=Paint.Align.CENTER)
            }
        } else {
            card.text("Mon signal se précise",96f,695f,38f,Ink)
            val percentage=100*correct/total
            card.text("$percentage %",540f,854f,92f,Teal,align=Paint.Align.CENTER)
            val unanswered=results.unanswered.coerceIn(0,total-correct)
            val wrong=total-correct-unanswered
            var x=111f
            listOf(correct to Teal,wrong to Coral,unanswered to GoldLight).forEach {entry ->
                val end=x+858f*entry.first/total
                if(entry.first>0)card.rounded(RectF(x,919f,end,974f),entry.second,8f)
                x=end
            }
            card.text("$correct justes · $wrong à consolider",540f,1050f,29f,Ink,align=Paint.Align.CENTER)
            card.text("$unanswered sans réponse",540f,1098f,24f,Muted,heavy=false,align=Paint.Align.CENTER)
        }
        card.text(if(results.successful)"Le signal passe. On garde le cap !" else "Chaque essai prépare la prochaine réussite.",
            540f,1250f,29f,Ink,align=Paint.Align.CENTER)
        card.footer()
    }

    fun progressImage(context: Context, progress: ShareProgress) {
        shareFile(context, renderProgressImage(context, progress), "image/png", "Ma progression Hamigo",
            "${progress.name} : ${progress.xp} XP, ${progress.streak} jours de série sur Hamigo 📻")
    }

    fun teamImage(context: Context, own: ShareProgress, others: List<ShareProgress>) {
        shareFile(context, renderTeamImage(context, own, others), "image/png", "Notre progression Hamigo",
            "Sur la même fréquence : notre équipe progresse ensemble sur Hamigo 📻")
    }

    fun inviteImage(context: Context, link: String) {
        shareFile(context, renderInviteImage(context, link), "image/png", "Mon invitation Hamigo",
            "Rejoins mon équipe Hamigo 📻\n$link")
    }

    fun nudge(context: Context, name: String) {
        val message = "Hé ${cleanName(name)} ! Une petite session Hamigo aujourd'hui ? 📻 " +
            "On garde nos séries et on se rapproche du certificat radioamateur ensemble !"
        text(context, message, "Envoyer une petite onde")
    }

    fun text(context: Context, text: String, title: String = "Partager Hamigo") {
        launchChooser(context, Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text), title)
    }

    /** The complete backup is only sent to the destination selected in Android's share sheet. */
    fun backup(context: Context, json: String) {
        val file = newFile(context, "hamigo-sauvegarde-", ".json")
        file.writeText(json, Charsets.UTF_8)
        shareFile(context, file, "application/json", "Sauvegarder ma progression Hamigo")
    }

    fun renderProgressImage(context: Context, progress: ShareProgress): File = posterFile(context, "hamigo-progression-") { card ->
        card.brand("MON CARNET DE BORD")
        PicoRenderer.draw(card.canvas, RectF(778f, 48f, 1030f, 300f), MascotMood.CELEBRATE, MascotPose.JUMP)
        card.text("Ma progression", 64f, 206f, 58f, Ink)
        card.fittedText(cleanName(progress.name), 64f, 269f, 43f, Ink, 696f)
        card.text("Une petite onde chaque jour.", 64f, 316f, 28f, Teal, heavy = false)

        card.stat(RectF(64f, 352f, 532f, 476f), format(progress.xp), "XP collectionnés", Mint)
        card.stat(RectF(548f, 352f, 1016f, 476f), format(progress.streak), "jours de série", Peach)
        card.stat(RectF(64f, 492f, 532f, 616f), format(progress.lessons), "leçons terminées", GoldLight)
        card.stat(RectF(548f, 492f, 1016f, 616f), "+${format(progress.weeklyXp)}", "XP cette semaine", Mist)

        card.panel(RectF(64f, 646f, 1016f, 1184f))
        card.text("Chaque jour compte", 96f, 710f, 39f, Ink)
        val days = dailySeries(progress)
        val interval = "Du ${days.first().day.format(ShortDate)} au ${days.last().day.format(ShortDate)}"
        card.text("XP quotidiens · $interval", 96f, 753f, 24f, Teal, heavy = false)
        card.activityChart(days, RectF(169f, 817f, 982f, 1061f), progress.dailyXp.isEmpty())
        if (days.any { it.xp == null } && progress.dailyXp.isNotEmpty()) {
            card.text("— : journée sans données", 96f, 1158f, 20f, Muted, heavy = false)
        }
        card.text("À mon rythme, vers le certificat.", 540f, 1248f, 31f, Ink, align = Paint.Align.CENTER)
        card.footer()
    }

    /** Weekly rank is computed over everybody, even when only six rows fit on the image. */
    fun renderTeamImage(context: Context, own: ShareProgress, others: List<ShareProgress>): File =
        posterFile(context, "hamigo-equipe-") { card ->
            val members = (listOf(Member(own, true, 0)) + others.mapIndexed { index, progress -> Member(progress, false, index + 1) })
                .sortedWith(compareByDescending<Member> { it.progress.weeklyXp }
                    .thenByDescending { it.isOwn }.thenBy { it.progress.name.lowercase(Locale.FRENCH) })
            val ownRank = members.indexOfFirst { it.isOwn }
            // Always include the sharer: show the top five plus their real rank when outside the top six.
            val visible = if (ownRank > 5) members.take(5) + members[ownRank] else members.take(6)
            val hidden = members.size - visible.size
            val totalWeekly = members.sumOf { it.progress.weeklyXp.coerceAtLeast(0).toLong() }
            val active = members.count { it.progress.weeklyXp > 0 }
            card.brand("NOTRE ÉQUIPE")
            PicoRenderer.draw(card.canvas, RectF(810f, 55f, 1030f, 275f), MascotMood.GOOFY, MascotPose.DANCE)
            card.text("Sur la même fréquence", 64f, 205f, 53f, Ink)
            card.fittedText("${cleanName(own.name)} et son équipe", 64f, 265f, 33f, Teal, 720f, heavy = false)
            card.text("On apprend. On se motive. On rayonne.", 64f, 309f, 26f, Muted, heavy = false)
            card.stat(RectF(64f, 342f, 368f, 452f), format(members.size), "membres", Mint, 45f)
            card.stat(RectF(384f, 342f, 688f, 452f), format(totalWeekly), "XP ensemble", GoldLight, 45f)
            card.stat(RectF(704f, 342f, 1016f, 452f), format(active), "actifs cette semaine", Peach, 45f)

            card.panel(RectF(64f, 477f, 1016f, 1055f))
            card.text("Les ondes de la semaine", 96f, 537f, 37f, Ink)
            card.text(if (ownRank > 5) "Les 5 premiers et toi · XP hebdomadaires" else "Notre classement · XP hebdomadaires",
                96f, 577f, 23f, Teal, heavy = false)
            val mostXp = max(1, members.maxOfOrNull { it.progress.weeklyXp } ?: 0).toFloat()
            visible.forEachIndexed { index, member ->
                val baseline = 632f + index * 65f
                val rank = if (member.isOwn) ownRank + 1 else members.indexOf(member) + 1
                card.circle(117f, baseline + 5f, 21f, if (member.isOwn) Teal else Mist)
                card.text(rank.toString(), 117f, baseline + 12f, 21f, if (member.isOwn) Color.WHITE else Ink,
                    align = Paint.Align.CENTER)
                val name = cleanName(member.progress.name) + if (member.isOwn) " · toi" else ""
                card.fittedText(name, 158f, baseline, 26f, Ink, 566f)
                card.text("${format(member.progress.weeklyXp)} XP", 980f, baseline, 24f,
                    if (member.isOwn) Teal else Ink, align = Paint.Align.RIGHT)
                card.rounded(RectF(158f, baseline + 13f, 980f, baseline + 30f), Mist, 9f)
                val amount = member.progress.weeklyXp.coerceAtLeast(0)
                if (amount > 0) card.rounded(RectF(158f, baseline + 13f,
                    158f + max(8f, 822f * amount / mostXp), baseline + 30f), if (member.isOwn) Teal else Coral, 9f)
            }
            val note = when {
                hidden > 0 -> "+ $hidden ${if (hidden == 1) "autre membre" else "autres membres"} dans l’équipe"
                others.isEmpty() -> "Une équipe commence par une invitation."
                totalWeekly == 0L -> "La première onde de la semaine reste à envoyer !"
                else -> "Un peu d’entraide, beaucoup de progrès."
            }
            card.text(note, 96f, 1030f, 23f, Muted, heavy = false)
            card.panel(RectF(64f, 1080f, 1016f, 1258f))
            card.text("Mon rythme", 96f, 1140f, 30f, Ink)
            card.text("XP / jour · 7 jours", 96f, 1180f, 22f, Teal, heavy = false)
            card.miniActivity(dailySeries(own), RectF(474f, 1119f, 984f, 1212f), own.dailyXp.isEmpty())
            card.footer()
        }

    fun renderInviteImage(context: Context, link: String): File {
        // Validate before drawing or exporting: an invitation must never encode an arbitrary URL.
        require(FriendInvite.parse(link) != null && link.startsWith(FriendInvite.HTTPS_BASE)) { "Invitation Hamigo invalide." }
        return posterFile(context, "hamigo-invitation-") { card ->
            card.brand("UNE INVITATION SUR NOS ONDES")
            PicoRenderer.draw(card.canvas, RectF(806f, 48f, 1030f, 272f), MascotMood.HAPPY, MascotPose.POINT)
            card.text("Ajoute-moi sur Hamigo", 64f, 225f, 54f, Ink)
            card.text("Une onde à la fois, on progresse ensemble.", 64f, 286f, 28f, Teal, heavy = false)
            card.panel(RectF(100f, 344f, 980f, 1213f))
            val qr = FriendInvite.qr(link, 700)
            try { card.canvas.drawBitmap(qr, 190f, 384f, null) } finally { qr.recycle() }
            card.text("Scanne avec l’appareil photo", 540f, 1131f, 32f, Ink, align = Paint.Align.CENTER)
            card.text("ou ouvre le lien partagé avec cette image.", 540f, 1174f, 25f, Teal,
                heavy = false, align = Paint.Align.CENTER)
            card.footer()
        }
    }

    private data class Member(val progress: ShareProgress, val isOwn: Boolean, val identity: Int)
    private data class DayBar(val day: LocalDate, val xp: Int?)

    private fun dailySeries(progress: ShareProgress): List<DayBar> {
        val points = progress.dailyXp.mapNotNull { point ->
            runCatching { LocalDate.parse(point.day) to point.xp.coerceAtLeast(0) }.getOrNull()
        }.toMap()
        val end = points.keys.maxOrNull() ?: runCatching {
            Instant.parse(progress.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrDefault(LocalDate.now())
        return (6L downTo 0L).map { daysAgo -> end.minusDays(daysAgo).let { DayBar(it, points[it]) } }
    }

    private class Poster(val canvas: Canvas) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bold = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

        init { canvas.drawColor(Cream) }

        fun text(value: String, x: Float, y: Float, size: Float, color: Int, heavy: Boolean = true,
                 align: Paint.Align = Paint.Align.LEFT) {
            paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size
            paint.typeface = if (heavy) bold else regular; paint.textAlign = align
            canvas.drawText(value, x, y, paint)
        }

        fun fittedText(value: String, x: Float, y: Float, size: Float, color: Int, width: Float, heavy: Boolean = true) {
            paint.textSize = size; paint.typeface = if (heavy) bold else regular
            text(ellipsize(value, width), x, y, size, color, heavy)
        }

        /** Truncate at grapheme boundaries, retaining surrogate pairs and composed names. */
        private fun ellipsize(value: String, width: Float): String {
            if (paint.measureText(value) <= width) return value
            val iterator = BreakIterator.getCharacterInstance(Locale.FRENCH).apply { setText(value) }
            val ends = mutableListOf<Int>()
            var boundary = iterator.first()
            while (boundary != BreakIterator.DONE) { ends.add(boundary); boundary = iterator.next() }
            var low = 0; var high = ends.lastIndex
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (paint.measureText(value.substring(0, ends[mid]).trimEnd() + "…") <= width) low = mid else high = mid - 1
            }
            return value.substring(0, ends[low]).trimEnd() + "…"
        }

        fun rounded(bounds: RectF, color: Int, radius: Float = 30f) {
            paint.color = color; paint.style = Paint.Style.FILL
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }

        fun circle(x: Float, y: Float, radius: Float, color: Int) {
            paint.color = color; paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, radius, paint)
        }

        fun panel(bounds: RectF) {
            rounded(RectF(bounds.left, bounds.top + 5f, bounds.right, bounds.bottom + 5f), Color.rgb(229, 231, 216))
            rounded(bounds, Color.WHITE)
        }

        fun brand(subtitle: String) {
            text("HAMIGO", 64f, 108f, 36f, Teal)
            text(subtitle, 64f, 148f, 19f, Muted, heavy = false)
        }

        fun footer() = text("APPRENDS · RÉVISE · RAYONNE", 540f, 1310f, 22f, Teal, align = Paint.Align.CENTER)

        fun stat(bounds: RectF, value: String, label: String, color: Int, numberSize: Float = 55f) {
            rounded(bounds, color)
            paint.typeface = bold; paint.textSize = numberSize
            val width = bounds.width() - 64f
            val size = if (paint.measureText(value) > width) numberSize * width / paint.measureText(value) else numberSize
            text(value, bounds.left + 30f, bounds.top + if (numberSize < 50f) 53f else 62f, size, Ink)
            fittedText(label, bounds.left + 30f, bounds.bottom - 24f, 23f, Ink, width, heavy = false)
        }

        fun activityChart(days: List<DayBar>, plot: RectF, noHistory: Boolean) {
            val axisMax = axisMaximum(days)
            repeat(5) { index ->
                val y = plot.bottom - plot.height() * index / 4f
                paint.color = Mist; paint.strokeWidth = 2f
                canvas.drawLine(plot.left, y, plot.right, y, paint)
                if (!noHistory) text(axisLabel(axisMax * index / 4.0), plot.left - 22f, y + 8f,
                    21f, Muted, heavy = false, align = Paint.Align.RIGHT)
            }
            val slot = plot.width() / days.size
            days.forEachIndexed { index, day ->
                val x = plot.left + slot * (index + .5f)
                if (!noHistory) {
                    val height = plot.height() * (day.xp ?: 0) / axisMax.toFloat()
                    if (day.xp != null) {
                        rounded(RectF(x - 31f, plot.bottom - max(4f, height), x + 31f, plot.bottom),
                            if (index == days.lastIndex) Coral else Teal, 10f)
                        text(axisLabel(day.xp.toDouble()), x, plot.bottom - height - 14f, 22f, Ink, align = Paint.Align.CENTER)
                    } else text("—", x, plot.bottom - 14f, 22f, Muted, heavy = false, align = Paint.Align.CENTER)
                }
                text(day.day.format(Weekday).lowercase(Locale.FRENCH), x, plot.bottom + 40f,
                    22f, Ink, heavy = false, align = Paint.Align.CENTER)
                text(day.day.format(ShortDate), x, plot.bottom + 71f, 19f, Muted,
                    heavy = false, align = Paint.Align.CENTER)
            }
            if (noHistory) {
                rounded(RectF(plot.left + 12f, plot.top + 52f, plot.right - 12f, plot.bottom - 52f), Color.WHITE, 18f)
                text("Historique quotidien indisponible", plot.centerX(), plot.centerY() - 7f, 28f, Ink,
                    align = Paint.Align.CENTER)
                text("Il apparaîtra après la prochaine synchronisation.", plot.centerX(), plot.centerY() + 33f,
                    21f, Muted, heavy = false, align = Paint.Align.CENTER)
            }
        }

        fun miniActivity(days: List<DayBar>, plot: RectF, noHistory: Boolean) {
            if (noHistory) {
                text("Historique quotidien", plot.centerX(), plot.centerY() - 8f, 23f, Muted,
                    heavy = false, align = Paint.Align.CENTER)
                text("indisponible", plot.centerX(), plot.centerY() + 24f, 23f, Muted,
                    heavy = false, align = Paint.Align.CENTER)
                return
            }
            val axisMax = axisMaximum(days)
            val slot = plot.width() / days.size
            paint.color = Mist; paint.strokeWidth = 2f
            canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, paint)
            days.forEachIndexed { index, day ->
                val x = plot.left + slot * (index + .5f)
                val height = plot.height() * (day.xp ?: 0) / axisMax.toFloat()
                if (day.xp != null) {
                    rounded(RectF(x - 21f, plot.bottom - max(3f, height), x + 21f, plot.bottom),
                        if (index == days.lastIndex) Coral else Teal, 6f)
                    text(axisLabel(day.xp.toDouble()), x, plot.bottom - height - 8f, 17f, Ink, align = Paint.Align.CENTER)
                } else text("—", x, plot.bottom - 8f, 19f, Muted, align = Paint.Align.CENTER)
                text(day.day.format(Weekday).lowercase(Locale.FRENCH), x, plot.bottom + 29f,
                    17f, Muted, heavy = false, align = Paint.Align.CENTER)
            }
        }
    }

    private fun axisMaximum(days: List<DayBar>): Double {
        val largest = max(20, days.maxOfOrNull { it.xp ?: 0 } ?: 0).toDouble()
        val roughStep = largest / 4
        val magnitude = 10.0.pow(floor(log10(roughStep)))
        val unit = roughStep / magnitude
        val step = when { unit <= 1 -> 1.0; unit <= 2 -> 2.0; unit <= 5 -> 5.0; else -> 10.0 } * magnitude
        return 4 * step
    }

    private fun axisLabel(value: Double): String = when {
        value >= 1_000_000 -> "${String.format(Locale.FRENCH, "%.1f", value / 1_000_000).removeSuffix(",0")} M"
        value >= 1_000 -> "${String.format(Locale.FRENCH, "%.1f", value / 1_000).removeSuffix(",0")} k"
        else -> format(value.toLong())
    }

    private fun format(value: Number): String = NumberFormat.getIntegerInstance(Locale.FRANCE).format(value.toLong().coerceAtLeast(0))
    private fun cleanName(name: String): String = name.replace(Regex("[\\p{Cntrl}\\r\\n]"), " ").trim().ifEmpty { "Pilote des ondes" }

    private fun posterFile(context: Context, prefix: String, draw: (Poster) -> Unit): File {
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        try {
            draw(Poster(Canvas(bitmap)))
            val file = newFile(context, prefix, ".png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return file
        } finally { bitmap.recycle() }
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

    private val Ink = Color.rgb(23, 63, 66)
    private val Teal = Color.rgb(8, 127, 130)
    private val Muted = Color.rgb(87, 110, 106)
    private val Cream = Color.rgb(255, 249, 233)
    private val Mint = Color.rgb(193, 235, 212)
    private val Mist = Color.rgb(229, 241, 235)
    private val Peach = Color.rgb(255, 211, 179)
    private val GoldLight = Color.rgb(255, 232, 180)
    private val Coral = Color.rgb(255, 120, 105)
    private val ShortDate = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE)
    private val Weekday = DateTimeFormatter.ofPattern("EEE", Locale.FRANCE)
}
