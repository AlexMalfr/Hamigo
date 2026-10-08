package com.malfreyt.alexandre.hamigo.platform

import com.malfreyt.alexandre.hamigo.LearningRules
import com.malfreyt.alexandre.hamigo.MascotMood
import com.malfreyt.alexandre.hamigo.MascotPose
import com.malfreyt.alexandre.hamigo.Progress
import com.malfreyt.alexandre.hamigo.dayCount
import java.time.LocalDate

enum class ReminderContext { START, CONTINUE, RESTART, IN_PROGRESS, GOAL_REACHED }

/** Small, immutable input: constructing a reminder never loads the question bank. */
data class ReminderState(val todayXp: Int, val goal: Int, val activeDays: Set<String>)

data class ReminderMessage(
    val date: LocalDate,
    val context: ReminderContext,
    val title: String,
    val message: String,
    val mood: MascotMood,
    val pose: MascotPose,
    val background: Int,
    val accent: Int,
    val todayXp: Int,
    val goal: Int,
    val streak: Int
)

object ReminderContent {
    fun build(progress: Progress, date: LocalDate = LocalDate.now()): ReminderMessage = build(
        ReminderState(progress.dayXp(date), progress.dailyGoal, progress.activeDays.filterTo(mutableSetOf()) { day ->
            runCatching { progress.dayXp(LocalDate.parse(day)) > 0 }.getOrDefault(false)
        }), date
    )

    fun build(state: ReminderState, date: LocalDate = LocalDate.now()): ReminderMessage {
        val activeDays = state.activeDays.filterTo(mutableSetOf()) {
            runCatching { !LocalDate.parse(it).isAfter(date) }.getOrDefault(false)
        }
        val goal = state.goal.coerceAtLeast(1)
        val todayXp = state.todayXp.coerceAtLeast(0)
        val streak = LearningRules.streak(activeDays, date)
        val context = when {
            todayXp >= goal -> ReminderContext.GOAL_REACHED
            todayXp > 0 -> ReminderContext.IN_PROGRESS
            streak > 0 -> ReminderContext.CONTINUE
            activeDays.isNotEmpty() -> ReminderContext.RESTART
            else -> ReminderContext.START
        }
        val remaining = (goal - todayXp).coerceAtLeast(0)
        // Stable for a given local day, different on consecutive days, without a persistent counter.
        val variant = Math.floorMod(date.toEpochDay(), 9L).toInt()
        val messages: List<String>
        val titles: List<String>
        val moods: List<MascotMood>
        val poses: List<MascotPose>
        val background: Int
        val accent: Int
        when (context) {
            ReminderContext.START -> {
                titles = listOf("Prêt pour ta première onde ?", "Pico règle son antenne", "On démarre ensemble ?")
                messages = listOf(
                    "Une petite leçon pour lancer ta première série ? Pico est prêt !",
                    "Ton premier signal t'attend. Quelques questions, et c'est parti !",
                    "Pas besoin de tout savoir : on commence par une petite onde.",
                    "Pico a trouvé la fréquence du jour : celle de ta première leçon !",
                    "Un bouton, une leçon, un premier déclic. À toi de jouer !",
                    "Ta première série commence avec une seule petite révision.",
                    "On fait connaissance avec les ondes ? Pico te montre le chemin.",
                    "Pico chauffe les transistors. Tu lances ta première leçon ?",
                    "Aujourd'hui peut être le jour de ton tout premier signal !"
                )
                moods = listOf(MascotMood.HAPPY, MascotMood.GOOFY, MascotMood.THINKING)
                poses = listOf(MascotPose.WAVE, MascotPose.POINT, MascotPose.DANCE)
                background = 0xFFE1F2EF.toInt(); accent = 0xFF087F82.toInt()
            }
            ReminderContext.CONTINUE -> {
                titles = listOf("Ta série capte toujours !", "Une onde pour garder le rythme", "Pico est sur ta fréquence")
                messages = listOf(
                    "${dayCount(streak)} de série ! Une petite révision pour continuer ?",
                    "Ton antenne garde le rythme : ${dayCount(streak)} de série, et la suite t'attend.",
                    "Pico vérifie les connexions : ta série de ${dayCount(streak)} est bien là !",
                    "Une petite leçon aujourd'hui, et ta série continue son voyage.",
                    "On garde la bonne fréquence ? Ta série de ${dayCount(streak)} attend la suite.",
                    "Pico a préparé une petite onde pour prolonger ton rythme.",
                    "Ta série fait bip-bip : une révision pour lui répondre ?",
                    "${dayCount(streak)}, plein de déclics. On en ajoute un aujourd'hui ?",
                    "Pas besoin d'un marathon : une petite révision entretient ta série."
                )
                moods = listOf(MascotMood.HAPPY, MascotMood.DETERMINED, MascotMood.GOOFY)
                poses = listOf(MascotPose.WAVE, MascotPose.POINT, MascotPose.JUMP)
                background = 0xFFFFECCB.toInt(); accent = 0xFF99500D.toInt()
            }
            ReminderContext.RESTART -> {
                titles = listOf("On reprend tranquillement ?", "Pico rallume son antenne", "Une nouvelle série t'attend")
                messages = listOf(
                    "Une pause, ça arrive. Une petite leçon lance une nouvelle série.",
                    "Tes connaissances sont toujours là. On reprend avec une petite révision ?",
                    "Pico a gardé ta place : une nouvelle série peut commencer aujourd'hui.",
                    "La série précédente est terminée ; tes progrès, eux, restent bien là.",
                    "On rebranche l'antenne ? Quelques questions pour retrouver le rythme.",
                    "Un nouveau départ, à ton rythme. Pico t'accompagne pour la suite.",
                    "Tu n'as rien à rattraper : une petite onde suffit pour reprendre.",
                    "Pico fait un petit signe. Ta prochaine leçon t'attend quand tu es prêt.",
                    "Une pause ne supprime pas tes acquis. On repart sur une nouvelle série ?"
                )
                moods = listOf(MascotMood.DETERMINED, MascotMood.HAPPY, MascotMood.GOOFY)
                poses = listOf(MascotPose.HUG, MascotPose.WAVE, MascotPose.POINT)
                background = 0xFFEDE8FA.toInt(); accent = 0xFF6B52A1.toInt()
            }
            ReminderContext.IN_PROGRESS -> {
                titles = listOf("Ton signal est déjà lancé !", "Encore une petite onde ?", "Pico suit tes progrès")
                messages = listOf(
                    "Déjà $todayXp XP aujourd'hui ! Il reste $remaining XP pour ton objectif.",
                    "Tu as commencé ! Encore $remaining XP pour boucler la mission du jour.",
                    "Pico a compté : $todayXp XP gagnés, $remaining XP jusqu'à ton objectif.",
                    "La machine à déclics tourne déjà. Une petite révision pour la suite ?",
                    "Ton antenne est chaude : $remaining XP et la mission du jour est remplie.",
                    "Un beau début aujourd'hui ! Pico te propose encore quelques questions.",
                    "$todayXp XP au compteur. On rapproche l'aiguille de ton objectif ?",
                    "Une petite onde de plus ? Il te reste $remaining XP pour la mission du jour.",
                    "Tu es sur la bonne fréquence : $todayXp XP déjà gagnés aujourd'hui."
                )
                moods = listOf(MascotMood.DETERMINED, MascotMood.THINKING, MascotMood.HAPPY)
                poses = listOf(MascotPose.POINT, MascotPose.WAVE, MascotPose.DANCE)
                background = 0xFFE2EEF9.toInt(); accent = 0xFF336890.toInt()
            }
            ReminderContext.GOAL_REACHED -> {
                titles = listOf("Mission du jour remplie !", "Pico fait sa danse de victoire", "Quelle belle onde aujourd'hui !")
                messages = listOf(
                    "$todayXp XP aujourd'hui : objectif atteint ! Pico célèbre, puis on souffle.",
                    "Mission accomplie ! Ton antenne a bien travaillé aujourd'hui.",
                    "Objectif du jour validé. Pico danse ; tu peux savourer tes progrès !",
                    "$todayXp XP au compteur : une belle journée de découvertes.",
                    "Bip-bip de victoire ! La mission du jour est déjà remplie.",
                    "Pico applaudit avec ses petites antennes. Objectif atteint !",
                    "Tu as fait le plein de déclics aujourd'hui. Bravo pour la mission !",
                    "Signal reçu cinq sur cinq : ton objectif quotidien est atteint.",
                    "Mission cochée, antenne contente. Pico te dit bravo !"
                )
                moods = listOf(MascotMood.CELEBRATE, MascotMood.GOOFY, MascotMood.HAPPY)
                poses = listOf(MascotPose.DANCE, MascotPose.JUMP, MascotPose.WAVE)
                background = 0xFFFFE4DF.toInt(); accent = 0xFFAB4D3D.toInt()
            }
        }
        return ReminderMessage(date, context, titles[variant % titles.size], messages[variant],
            moods[variant % moods.size], poses[(variant / 3 + variant) % poses.size],
            background, accent, todayXp, goal, streak)
    }
}
