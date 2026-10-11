package com.malfreyt.alexandre.hamigo.platform

import com.malfreyt.alexandre.hamigo.dayCount
import java.time.LocalDate

/** The share sheet and its illustration use the same public, cached statistics. */
internal data class EncouragementContent(val headline:String,val detail:String,val stats:String,val week:String,val message:String,val ratio:Float?,val reached:Boolean) {
    companion object {
        fun from(profile:ShareProgress,date:LocalDate=SocialActivity.date(profile)):EncouragementContent {
            val today=SocialActivity.todayXp(profile,date);val goal=profile.dailyGoal
            val reached=today!=null&&goal!=null&&today>=goal
            val ratio=if(today!=null&&goal!=null)(today.toFloat()/goal).coerceIn(0f,1f) else null
            val headline=when {reached->"Objectif atteint !";today!=null&&goal!=null->"Encore ${goal-today} XP";today!=null&&today>0->"$today XP aujourd’hui";else->"On révise ensemble ?"}
            val detail=when {reached->"$today / $goal XP aujourd’hui";today!=null&&goal!=null->"pour ton objectif de $goal XP";today!=null->"Une petite session Hamigo ?";else->"Ton dernier bilan partagé"}
            val lesson="${profile.lessons} leçon${if(profile.lessons==1)" terminée" else "s terminées"}"
            val stats="${dayCount(profile.streak)} de série · $lesson"
            val days=SocialActivity.week(profile,date)
            val week=if(days.any {it.second!=null})"${days.sumOf {it.second?.toLong() ?: 0L}} XP ${if(days.all {it.second!=null})"sur les 7 derniers jours" else "enregistrés sur les 7 derniers jours"}"
                else "${profile.weeklyXp} XP dans ton dernier bilan hebdo"
            val name=profile.name.replace(Regex("[\\p{Cntrl}\\r\\n]")," ").trim()
            val daily=when {reached->"Bravo, tu as atteint ton objectif du jour : $today / $goal XP ! 🎉";today!=null&&goal!=null->"Tu as gagné $today XP aujourd’hui sur ton objectif de $goal XP. Encore ${goal-today} XP pour l’atteindre !";today!=null->"Tu as gagné $today XP aujourd’hui.";else->"D’après ta dernière progression partagée :"}
            val invitation=if(reached)"On continue à progresser ensemble !" else "Une petite session Hamigo ensemble ?"
            return EncouragementContent(headline,detail,stats,week,"Hé $name ! 📻\n\n$daily\n$stats.\n$week.\n\n$invitation",ratio,reached)
        }
    }
}
