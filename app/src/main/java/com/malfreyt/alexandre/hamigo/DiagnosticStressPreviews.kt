package com.malfreyt.alexandre.hamigo


/** Synthetic boundary cases stay in the diagnostic model, never in course content or backups. */
internal object DiagnosticStressPreviews {
    val presets=listOf("Introduction · texte très long","Question · énoncé et réponses longs","Texte à trou · réponse très longue",
        "Mémo · fiche très longue","Moi · statistiques extrêmes","Équipe · noms très longs")
    private val lorem="Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. "
    val lesson=Lesson("diagnostic-stress-intro","Une introduction volontairement très longue pour examiner les limites de la mise en page",
        "Un résumé de démonstration particulièrement développé, avec plusieurs lignes pour contrôler les titres, les retours à la ligne et l’accès au contenu.",
        List(12){"${it+1}. "+lorem.repeat(4)},"U = R × I","diagnostic",emptyList())
    private val reference=RefCategory("diagnostic-stress-memo","Une fiche de référence avec un titre volontairement très développé",
        "Données fictives pour inspecter les retours à la ligne et le défilement.",List(24){RefRow("Entrée ${it+1} : un intitulé particulièrement long à consulter",lorem.repeat(2),lorem,group=if(it<12)"Faits" else "Exemples",kind=if(it<12)"fact" else "example")},false,
        intro=lorem.repeat(2))
    fun prepare(model:AppModel,preset:String) {
        check(model.diagnosticModel)
        when {
            preset.startsWith("Introduction")->model.previewLesson(lesson)
            preset.startsWith("Mémo ·")-> {model.route="resources";model.resource=reference}
            preset.startsWith("Question ·")->model.startQuestions("Énoncé long",listOf(Question("diagnostic-stress-question",lorem.repeat(5),List(4){"Réponse ${it+1}. "+lorem},0,lorem.repeat(2))))
            preset.startsWith("Texte à trou")-> {
                val answer="une correspondance électromagnétique particulièrement développée"
                model.startQuestions("Mot long",listOf(Question("diagnostic-stress-cloze","Le signal présente ___ dans cette démonstration.",listOf(answer,"une alternative"),0,lorem,kind="cloze")))
                model.session!!.responses[0]=SessionResponse(true,display=answer,choiceIndex=0)
            }
            preset=="Moi · statistiques extrêmes"-> {
                model.route="profile"
                DiagnosticData.seed(model,xp=999999,todayXp=9999,days=90,completed=97)
                model.progress.prefs.edit().putString("name","Alexandrine au très long pseudonyme Démo").commit();model.refresh()
            }
            preset=="Équipe · noms très longs"->{model.route="friends";model.friends=model.friends.mapIndexed {i,friend->friend.copy(progress=friend.progress.copy(
                name=if(i==0)"Camille et son pseudonyme vraiment très long" else "Alexandre de la station de démonstration",xp=987654,streak=999,weeklyXp=123456))}}
        }
    }
}
