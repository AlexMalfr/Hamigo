package com.malfreyt.alexandre.hamigo

/** Describe the task and the available material, never the answer key or a hidden flashcard face. */
internal object QuestionNarration {
    private fun sentence(text:String)=text.trim().let {if(it.lastOrNull() in listOf('.','?','!'))it else "$it."}
    private fun alternatives(items:List<String>):String=when(items.size) {
        0->"";1->items.first();else->items.dropLast(1).joinToString(", ")+", ou "+items.last()
    }
    fun text(q:Question,displayedOrder:List<String> = initialQuestionOrder(q).map(q.choices::get),flashAnswer:Boolean=false):String {
        val prompt=q.prompt.replace(Regex("_{2,}"),"quelque chose").let {
            if(q.kind=="morseEncode")it.replace("avec les boutons point et trait","en Morse") else it
        }.let {
            Regex("^([−+-]?\\d+(?:[.,]\\d+)?(?:[eE][−+-]?\\d+)?\\s*)([\\p{L}µμΩ°%]+)\\s+en\\s+([\\p{L}µμΩ°%]+)\\s*\\?$").matchEntire(it)?.let {conversion->
                val (_,quantity,from,to)=conversion.groupValues
                if(FrenchSpeech.isUnit(from)&&FrenchSpeech.isUnit(to))"Convertis $quantity$from en $to." else it
            } ?: it
        }
        val start=sentence(if(q.kind=="flash")when {
            prompt.matches(Regex("[A-Z]"))->"La lettre $prompt"
            prompt.matches(Regex("[0-9]"))->"Le chiffre $prompt"
            else->prompt
        } else prompt)
        val choices=alternatives(q.choices.map {if(it.matches(Regex("[A-Z]")))"la lettre $it" else FrenchSpeech.option(it)})
        val task=when(q.kind) {
            "flash"->if(flashAnswer) "Réponse. ${sentence(FrenchSpeech.option(q.choices.firstOrNull().orEmpty()))}" else "Essaie de retrouver la réponse, puis retourne la carte pour la vérifier."
            "cloze"->"Complète avec une réponse parmi : $choices."
            "truefalse"->"Cette affirmation est-elle vraie ou fausse ?"
            "multiselect"->"Choisis toutes les réponses qui conviennent parmi : $choices. Plusieurs réponses sont possibles."
            "order","sort"->"Remets ces éléments dans l’ordre demandé : ${displayedOrder.joinToString(" ; ")}. Déplace les éléments pour les réordonner."
            "match"->"Dans le premier groupe : ${q.pairs.joinToString(" ; "){it.left}}. Dans le second groupe : ${q.pairs.indices.shuffled(kotlin.random.Random(q.id.hashCode())).joinToString(" ; "){q.pairs[it].right}}. Relie chaque élément du premier groupe à un élément du second."
            "number"->(scoreScale(q)?.let {"Saisis une note sur $it."}
                ?: "Saisis une valeur${q.unit.takeIf {it.isNotBlank()}?.let {" en $it"}.orEmpty()}.")+" ${precision(q)}"
            "frequency"->"Règle la fréquence avec le curseur, entre 143 et 148 mégahertz. ${precision(q.copy(unit="MHz"))}"
            "estimate"->"Déplace le curseur${if(q.bands.size>=2)" entre ${q.bands[0]} et ${q.bands[1]} ${q.unit}" else ""}. ${precision(q)}"
            "binary"->"Active ou désactive les bits, du poids le plus fort à gauche au plus faible à droite."
            "morseEncode"->"Compose le signal demandé avec le manipulateur Morse."
            "morseListen"->"Utilise le bouton Écouter le signal pour entendre le Morse. Puis choisis parmi : $choices."
            "resistor"->(if(q.bands.isNotEmpty()&&q.bands.any {!q.prompt.contains(it,true)})"Les anneaux sont, dans l’ordre : ${q.bands.joinToString(", ")}. " else "")+"Choisis une réponse parmi : $choices."
            else->if(choices.isNotEmpty())"Choisis une réponse parmi : $choices." else ""
        }
        val visual=if(q.image!=null||q.visual.isNotBlank())"Une illustration accompagne cette question. La lecture vocale ne décrit pas son schéma. " else ""
        return "$start $visual$task"
    }
    // A bare scale in the unit field is a mark, not an arithmetic division.
    private fun scoreScale(q:Question)=Regex("/\\s*([1-9]\\d*)").matchEntire(q.unit.trim())?.groupValues?.get(1)
    private fun precision(q:Question)=if(q.tolerance>0) {
        val unit=if(scoreScale(q)!=null)if(q.tolerance==1.0)" point" else " points"
            else q.unit.takeIf {it.isNotBlank()}?.let {" $it"}.orEmpty()
        "Une différence maximale de ${formatMeasuredNumber(q.tolerance,q.tolerance)}$unit est acceptée."
    } else "La valeur doit être exacte."
}

internal fun initialQuestionOrder(q:Question):List<Int> = q.choices.indices.shuffled(kotlin.random.Random(q.id.hashCode())).let {list->
    if(list.size>1&&list==q.choices.indices.toList())list.drop(1)+list.first() else list
}
