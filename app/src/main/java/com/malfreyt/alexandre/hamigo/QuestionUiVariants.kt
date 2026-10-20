package com.malfreyt.alexandre.hamigo

data class QuestionUiVariant(val id:String,val title:String,val description:String)

/** Shared vocabulary for finding actual native compositions, independent from question identity. */
object QuestionUiVariants {
    val variants=listOf(
        QuestionUiVariant("standard","Présentation courante","Composition habituelle du type de question."),
        QuestionUiVariant("number-signed","Nombre signé · bouton + / −","Champ numérique avec signe inversible : bouton + / − lié au champ, sans signe dans le texte. Valeur négative, par exemple une atténuation."),
        QuestionUiVariant("sstv-slant","SSTV · inclinaison","Choix avec une mire simulée régulièrement inclinée."),
        QuestionUiVariant("sstv-loss","SSTV · lignes manquantes","Choix avec une mire simulée et une bande absente."),
        QuestionUiVariant("image","Illustration","Énoncé avec une image agrandissable."),
        QuestionUiVariant("rows-text","Réponses libres par ligne","Plusieurs cartes, chacune avec sa saisie écrite."),
        QuestionUiVariant("rows-dropdown","Listes par ligne","Plusieurs cartes, chacune avec un menu de réponses."),
        QuestionUiVariant("rows-mixed","Lignes mixtes","Saisie libre et menus dans une même question."),
        QuestionUiVariant("spoken-short","Oral · réponse courte","Micro facultatif et repli au clavier sur une ligne."),
        QuestionUiVariant("spoken-long","Oral · réponse développée","Micro facultatif et repli au clavier multiligne."),
        QuestionUiVariant("text","Écrit · réponse courte","Réponse à retrouver sans banque de mots, sur une ligne si la largeur le permet."),
        QuestionUiVariant("text-long","Écrit · réponse développée","Réponse longue sans banque de mots ; champ multiligne selon la largeur disponible."),
        QuestionUiVariant("flash-morse","Flashcard · Morse graphique","Carte à retourner, avec le signal Morse dessiné sur le verso."),
        QuestionUiVariant("logic-single","Logique · une porte","Un seul symbole logique, sans dupliquer les variantes selon le nom de la porte."),
        QuestionUiVariant("logic-column","Logique · plusieurs portes","Plusieurs symboles logiques disposés en colonne pour les comparer."),
        QuestionUiVariant("morse-written","Morse · écoute et réponse écrite","Signal à écouter, puis réponse au clavier sans choix affichés."),
        QuestionUiVariant("morse-spoken","Morse · écoute et épellation orale","Signal à écouter, puis réponse au micro ou au clavier."),
        QuestionUiVariant("morse-comprehension","Morse · échange complet","Écoute puis choix d’un résumé en français."),
        QuestionUiVariant("morse-characters","Morse · caractères","Écoute ou composition de caractères et de groupes."),
        QuestionUiVariant("circuit","Circuit à compléter","Composants déplaçables et alternative accessible."),
        QuestionUiVariant("circuit-measurement","Montage · appareils de mesure","Voltmètre et ampèremètre sur les branches du circuit."),
        QuestionUiVariant("circuit-series","Montage · maquette série","Chaîne basse tension avec résistance et DEL."),
        QuestionUiVariant("circuit-receiver","Montage · réception","Synoptique de réception et sens de la chaîne du signal."),
        QuestionUiVariant("visual","Schéma natif","Illustration pédagogique dessinée dans l’application.")
    )
    fun ids(q:Question):List<String> = buildList {
        if(q.kind=="circuit"){
            add(when(q.visual){"circuit:measurement"->"circuit-measurement";"circuit:series"->"circuit-series";"circuit:receiver"->"circuit-receiver";else->"circuit"});return@buildList
        }
        if(q.kind in setOf("text","spoken")&&q.template.contains("morse-")&&q.bands.isNotEmpty()){
            add(if(q.kind=="text")"morse-written" else "morse-spoken");return@buildList
        }
        if(q.kind=="number"&&(q.value ?: 0.0)<0)add("number-signed")
        if(q.visual=="sstv:slant")add("sstv-slant")
        if(q.visual=="sstv:loss")add("sstv-loss")
        if(q.image!=null)add("image")
        if(q.kind=="rows")add(when {
            q.rows.all {it.choices.isEmpty()}->"rows-text"
            q.rows.all {it.choices.isNotEmpty()}->"rows-dropdown"
            else->"rows-mixed"
        })
        when(q.kind) {
            "spoken"->add(if((q.acceptedAnswers.maxOfOrNull {it.length} ?: 0)>55)"spoken-long" else "spoken-short")
            "text"->add(if((q.acceptedAnswers.maxOfOrNull {it.length} ?: 0)>55)"text-long" else "text")
            "flash"->if(q.topic=="morse")add("flash-morse")
            "morseListen","morseEncode"->add(if(q.morseComprehension)"morse-comprehension" else "morse-characters")
        }
        when {
            q.visual in setOf("logic:basic","logic:inverted","logic:nand-nor")->add("logic-column")
            q.visual in setOf("logic:and","logic:or","logic:not","logic:nand","logic:nor","logic:xor")->add("logic-single")
            q.visual.isNotBlank()&&!q.visual.startsWith("sstv:")->add("visual")
        }
        if(isEmpty())add("standard")
    }
    fun label(id:String)=variants.firstOrNull {it.id==id}?.title ?: if('+' in id)id.split('+').joinToString(" · "){part->variants.firstOrNull {it.id==part}?.title ?: part} else id
    fun searchable(q:Question)=ids(q).flatMap {id->val v=variants.first {it.id==id};listOf(id,v.title,v.description)}.joinToString(" ")
    fun compositionKey(q:Question)=q.kind+":"+ids(q).sorted().joinToString("+")
    /** A combination such as an illustrated diagram is one composition, not two duplicate examples. */
    fun representatives(questions:List<Question>):List<Pair<QuestionUiVariant,Question>> = questions.distinctBy(::compositionKey).map {q->
        val parts=ids(q).map {id->variants.first {it.id==id}}
        val variant=if(parts.size==1)parts.single() else QuestionUiVariant(
            parts.map {it.id}.sorted().joinToString("+"),parts.joinToString(" · "){it.title},parts.joinToString(" "){it.description})
        variant to q
    }.sortedBy {(_,q)->ids(q).minOf {id->variants.indexOfFirst {it.id==id}}}
    fun matches(q:Question,variantId:String)=if('+' in variantId)ids(q).sorted().joinToString("+")==variantId else variantId in ids(q)
    fun allCompositions(questions:List<Question>):List<Question> = if(questions.isEmpty())emptyList() else (listOf(primary(questions))+questions).distinctBy(::compositionKey)
    /** Pick an ordinary composition before illustrated, long-form or signed alternatives. */
    fun primary(questions:List<Question>):Question = questions.minBy {q->
        val ids=ids(q)
        when {
            "standard" in ids->0
            q.image==null&&ids.none {it in setOf("number-signed","spoken-long","text-long","flash-morse","rows-mixed","morse-comprehension","morse-written","morse-spoken","logic-column")}->1
            else->2
        }
    }
}
