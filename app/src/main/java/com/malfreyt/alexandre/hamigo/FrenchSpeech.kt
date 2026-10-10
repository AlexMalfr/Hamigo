package com.malfreyt.alexandre.hamigo

/** Spoken French only: never modifies displayed questions, answers or mathematical meaning. */
internal object FrenchSpeech {
    private val digits=listOf("zéro","un","deux","trois","quatre","cinq","six","sept","huit","neuf")
    private val letters=mapOf("U" to "u","I" to "i","R" to "ère","P" to "pé","W" to "double vé","E" to "e",
        "L" to "elle","C" to "cé","S" to "esse","Z" to "zède","V" to "vé","A" to "a","F" to "effe",
        "X" to "ixe","Y" to "i grec","T" to "té","Q" to "ku","H" to "ache","B" to "bé","D" to "dé","G" to "gé","J" to "ji","K" to "ka","M" to "emme","N" to "enne","O" to "o",
        "f" to "effe","r" to "ère","t" to "té","u" to "u","l" to "elle","s" to "esse")
    private val greek=mapOf('π' to "pi",'λ' to "lambda",'ω' to "oméga",'ρ' to "rho",'τ' to "tau",'φ' to "phi",'ϕ' to "phi",'θ' to "thêta",'Δ' to "delta",'δ' to "delta",'β' to "bêta",'α' to "alpha",'η' to "êta",'μ' to "mu",'µ' to "mu")
    private val units=linkedMapOf("dBm" to "décibels milliwatt","dBW" to "décibels watt","dB" to "décibels",
        "GHz" to "gigahertz","MHz" to "mégahertz","kHz" to "kilohertz","mHz" to "millihertz","Hz" to "hertz",
        "kWh" to "kilowattheures","Wh" to "wattheures","MΩ" to "mégaohms","kΩ" to "kiloohms","mΩ" to "milliohms","Ω" to "ohms",
        "µF" to "microfarads","μF" to "microfarads","uF" to "microfarads","nF" to "nanofarads","pF" to "picofarads","mF" to "millifarads",
        "µH" to "microhenrys","μH" to "microhenrys","mH" to "millihenrys","nH" to "nanohenrys",
        "µA" to "microampères","μA" to "microampères","mA" to "milliampères","kA" to "kiloampères",
        "µC" to "microcoulombs","μC" to "microcoulombs","mC" to "millicoulombs","nC" to "nanocoulombs","pC" to "picocoulombs",
        "mV" to "millivolts","µV" to "microvolts","μV" to "microvolts","kV" to "kilovolts",
        "mW" to "milliwatts","µW" to "microwatts","kW" to "kilowatts","MW" to "mégawatts",
        "kJ" to "kilojoules","µs" to "microsecondes","μs" to "microsecondes","ms" to "millisecondes","ns" to "nanosecondes",
        "km" to "kilomètres","cm" to "centimètres","mm" to "millimètres","µm" to "micromètres",
        "°C" to "degrés Celsius","°" to "degrés","%" to "pour cent",
        "V" to "volts","A" to "ampères","W" to "watts","J" to "joules","F" to "farads","H" to "henrys","C" to "coulombs","S" to "siemens","K" to "kelvins","s" to "secondes","m" to "mètres","h" to "heures","min" to "minutes")
    private val unitPattern=units.keys.sortedByDescending(String::length).joinToString("|") {Regex.escape(it)}
    private fun unitName(symbol:String,singular:Boolean=false)=units.getValue(symbol).split(' ').mapIndexed {i,word->if(singular&&i==0&&word!="siemens")word.removeSuffix("s") else word}.joinToString(" ")
    private val superDigits="⁰¹²³⁴⁵⁶⁷⁸⁹";private val subDigits="₀₁₂₃₄₅₆₇₈₉"
    private fun morse(code:String,separator:String=", ")=normalizedMorse(code).trim().split(Regex("\\s*/\\s*")).joinToString(", pause entre mots, ") {word->
        word.trim().split(Regex("\\s+")).joinToString(", pause entre lettres, ") {letter->letter.map {if(it=='.')"point" else "trait"}.joinToString(separator)}
    }
    private fun label(value:String):String = when {
        value.length>1->"Le groupe "+value.map {letters[it.toString()] ?: if(it.isDigit())digits[it.digitToInt()] else it.toString()}.joinToString(" ")
        value[0].isDigit()->"Le chiffre "+digits[value[0].digitToInt()]
        value[0].isLetter()->"La lettre "+(letters[value] ?: if(value=="É")"é accent aigu" else value)
        else->"Le caractère "+MorseReference.characterName(value).lowercase()
    }
    private fun exampleSpeech(example:MorseExample)="${label(example.label)} ${if(example.label.length==1)"se lit" else "se transmet"} ${morse(example.code," ")}."
    private fun punctuationList(value:String):String {
        val names=mapOf('?' to "le point d’interrogation",'/' to "la barre oblique",'.' to "le point final",'=' to "le signe égal",',' to "la virgule",'@' to "l’arobase")
        val result=mutableListOf<String>();var i=0
        while(i<value.length) {
            while(i<value.length&&value[i].isWhitespace())i++
            if(i>=value.length)break
            result+=names.getValue(value[i++])
            while(i<value.length&&value[i].isWhitespace())i++
            if(i<value.length)i++ // The next comma/middle dot separates entries, even for a comma entry.
        }
        return result.dropLast(1).joinToString(", ")+" et "+result.last()
    }
    private fun timingList(source:String):String? {
        val definitions=source.trim().split(Regex("\\s*[·;]\\s*"))
        val pattern=Regex("(point|trait|pause interne|entre lettres|entre mots)\\s+(\\d+)",RegexOption.IGNORE_CASE)
        val entries=definitions.map {pattern.matchEntire(it) ?: return null}
        if(entries.size<2 || entries.none {it.groupValues[1].equals("trait",true)})return null
        return entries.joinToString(" ") {entry->
            val subject=when(entry.groupValues[1].lowercase()) {
                "point"->"Un point";"trait"->"Un trait";"pause interne"->"La pause entre deux éléments";"entre lettres"->"La pause entre deux lettres";else->"La pause entre deux mots"
            }
            val n=entry.groupValues[2];"$subject dure ${if(n=="1")"une unité" else "$n unités"}."
        }
    }
    private fun number(text:String)=text.replace(Regex("(?<=\\d)[.,](\\d+)")) {m->" virgule "+m.groupValues[1].map {digits[it.digitToInt()]}.joinToString(" ")}
    private fun exponent(text:String)=when(text) {"2"->"au carré";"3"->"au cube";else->"puissance ${number(text)}"}
    private fun formula(node:Formula):String=when(node) {
        is Formula.Atom -> if(node.value=="−")"moins" else greek[node.value.singleOrNull()] ?: letters[node.value] ?: number(node.value)
        is Formula.Fraction -> "${formula(node.numerator)} divisé par ${formula(node.denominator)}"
        is Formula.Root -> "racine carrée de ${formula(node.value)}"
        is Formula.Power -> "${formula(node.value)} ${exponent(formula(node.exponent))}"
        is Formula.Index -> "${formula(node.value)} "+when((node.index as? Formula.Atom)?.value) {"eff"->"efficace";"max"->"maximum";"min"->"minimum";"moy"->"moyen";else->"indice ${formula(node.index)}"}
        is Formula.Group -> "ouvrir la parenthèse, ${formula(node.value)}, fermer la parenthèse"
        is Formula.Join -> {
            val unit=(node.right as? Formula.Atom)?.value?.let(units::get)
            if(node.symbol.isEmpty() && node.left is Formula.Atom && !node.left.italic && unit!=null) "${formula(node.left)} $unit"
            else "${formula(node.left)} ${when(node.symbol){"="->"égale";"+"->"plus";"−"->"moins";"×",""->if((node.left as? Formula.Atom)?.value=="−")"" else "fois";else->node.symbol}} ${formula(node.right)}"
        }
    }
    fun prepare(source:String):String {
        if(source.isBlank())return source
        timingList(source)?.let {return it}
        MorseExamples.table(source).takeIf {it.isNotEmpty()}?.let {entries->return entries.joinToString(" ",transform=::exampleSpeech)}
        val stored=mutableListOf<String>()
        fun keep(text:String):String {stored+=text;return "\uE000${stored.lastIndex}\uE001"}
        var text=source.replace(Regex("https?://\\S+")) {keep("lien vers la source")}.replace(Regex("_{2,}"),"à compléter")
        if(Regex("(?i)\\bunités?\\b").containsMatchIn(source)) {
            text=text.replace(Regex("((?:[Oo]hms?|Ω)\\s+et\\s+)(F|H)(?![\\p{L}])")) {m->m.groupValues[1]+keep(unitName(m.groupValues[2]))}
        }
        // Protect labels first: '/' here names a character, not a transmitted word pause.
        val examples=MorseExamples.find(text)
        examples.asReversed().forEach {example->
            text=text.replaceRange(example.range,keep(exampleSpeech(example)))
        }
        text=text.replace(Regex("(?<![\\p{L}\\p{N}])[?/.=@,](?:\\s*[,·]\\s*[?/.=@,]){1,}")) {keep(punctuationList(it.value))}
        val prefixes=mapOf("k" to "kilo","M" to "méga","G" to "giga","m" to "milli","µ" to "micro","μ" to "micro","n" to "nano","p" to "pico")
        text=text.replace(Regex("\\b1\\s*([kMGmµμnp])\\s*=\\s*10(?:([⁻⁺$superDigits]+)|\\^\\s*([−-]?\\d+))(?![\\p{L}\\p{N}])")) {m->
            val power=m.groupValues[3].ifBlank {m.groupValues[2].map {when(it){'⁻'->'-';'⁺'->'+';else->('0'.code+superDigits.indexOf(it)).toChar()}}.joinToString("")}
            keep("Le préfixe ${prefixes.getValue(m.groupValues[1])} correspond à dix puissance ${power.replace("-","moins ").replace("−","moins ").replace("+","plus ")}.")
        }
        text=text.replace(Regex("(?<![\\p{L}\\d])([−-]?\\d+(?:[.,]\\d+)?)[eE]([+−-]?\\d+)(?![\\p{L}\\d])")) {m->
            keep(number(m.groupValues[1].replace("−","moins ").replace("-","moins "))+" fois dix puissance "+m.groupValues[2].replace("−","moins ").replace("-","moins ").replace("+","plus "))
        }.replace(Regex("(?<!\\d)([01]{2,})₂")) {m->keep(m.groupValues[1].map {digits[it.digitToInt()]}.joinToString(" ")+", en base deux")}
        text=MathFormula.fragments(text).joinToString("") {fragment->fragment.formula?.let {keep(formula(it))} ?: fragment.source}
        text=morseTextParts(text).joinToString("") {part->if(part.code)keep(if(part.text=="/")"pause entre mots" else morse(part.text)) else part.text}
        // Do not ask the voice engine to guess superscripts, decimal points or SI prefixes.
        text=text.replace(Regex("[⁻⁺$superDigits]+")) {m->
            val power=m.value.map {when(it){'⁻'->'-';'⁺'->'+';else->('0'.code+superDigits.indexOf(it)).toChar()}}.joinToString("")
            " "+exponent(power.replace("-","moins ").replace("+","plus "))+" "
        }.replace(Regex("[$subDigits]+")) {m->" indice "+m.value.map {('0'.code+subDigits.indexOf(it)).toChar()}.joinToString("")+" "}
        text=text.replace("ⁿ"," puissance enne ")
        text=text.replace(Regex("\\^\\s*([−-]?\\d+)")) {m->" "+exponent(m.groupValues[1].replace("-","moins ").replace("−","moins "))+" "}
        text=text.replace(Regex("\\b\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})+\\b")) {it.value.filterNot(Char::isWhitespace)}
        text=text.replace(Regex("(?<![\\p{L}\\d])(m/s|km/h)(?![\\p{L}\\d])")) {m->keep(if(m.value=="m/s")"mètres par seconde" else "kilomètres par heure")}
        if(Regex("(?i)\\bunités?\\b").containsMatchIn(source)) {
            text=text.replace(Regex("(?<![\\p{L}\\d])($unitPattern)\\s*/\\s*($unitPattern)(?![\\p{L}\\d])")) {m->keep(unitName(m.groupValues[1])+" par "+unitName(m.groupValues[2],true))}
            text=text.replace(Regex("(?<=\\bdonne)\\s+($unitPattern)(?![\\p{L}’'])")) {m->" "+keep(unitName(m.groupValues[1]))}
        }
        text=text.replace(Regex("([−-]?\\d+(?:[.,]\\d+)?)\\s*($unitPattern)(?![\\p{L}’'])")) {m->
            keep(number(m.groupValues[1].replace("−","moins ").replace("-","moins "))+" "+unitName(m.groupValues[2],kotlin.math.abs(m.groupValues[1].replace('−','-').replace(',','.').toDouble())==1.0))
        }
        text=text.replace(Regex("(?<=\\b(?:en|unité|unités))\\s+($unitPattern)(?![\\p{L}’'])",RegexOption.IGNORE_CASE)) {m->
            val unit=units[m.groupValues[1]];if(unit==null)m.value else " "+keep(unit)
        }
        text=text.replace(Regex("(?<![\\p{L}])($unitPattern)(?![\\p{L}’'])")) {m->
            val symbol=m.value
            when {symbol.length>1 || symbol in listOf("Ω","%","°")->keep(units.getValue(symbol));else->m.value}
        }
        text=text.replace(Regex("(?<![\\p{L}\\d])[A-Z]{1,3}\\d[A-Z]{1,5}(?:/[A-Z0-9]+)?\\b")) {m->
            keep(m.value.map {when {it=='/'->"barre oblique";it.isDigit()->digits[it.digitToInt()];else->letters[it.toString()] ?: it.toString()}}.joinToString(" "))
        }
        text=text.replace(Regex("\\b(?:QRM|QRN|QRP|QRO|QRS|QRQ|QTH|QSL|QSY|QRT|QSO|CQ|RST|RS|VHF|UHF|HF|BF|RF|CW|SSB|AM|FM|FSK|PSK|DSP|ADC|DAC|AOP|UTC|ANFR|ARCEP|UIT|CEPT|IARU)\\b")) {m->keep(m.value.map {letters[it.toString()] ?: it.toString()}.joinToString(" "))}
        text=text.replace(Regex("log\\s*(?:indice\\s*)?10\\b"),"logarithme en base dix")
            .replace(Regex("\\bln\\b"),"logarithme népérien").replace(Regex("\\bcos\\b"),"cosinus").replace(Regex("\\bsin\\b"),"sinus").replace(Regex("\\btan\\b"),"tangente")
        text=text.map {greek[it]?.let {word->" $word "} ?: it.toString()}.joinToString("")
        text=text.replace("@"," arobase ").replace("√"," racine carrée de ").replace("≈"," environ égal à ").replace("≠"," différent de ")
            .replace("≤"," inférieur ou égal à ").replace("≥"," supérieur ou égal à ").replace("<"," inférieur à ").replace(">"," supérieur à ")
            .replace("±"," plus ou moins ").replace("×"," fois ").replace("÷"," divisé par ").replace("∞"," infini ")
            .replace("="," égale ").replace("+"," plus ").replace("→"," vers ").replace("↔"," correspond à ")
            .replace("∧"," et ").replace("∨"," ou ").replace("¬"," non ").replace("⊕"," ou exclusif ")
            .replace("Ω"," ohms ").replace("_", " ").replace("·", ". ")
        text=text.replace(Regex("(?<=[\\p{L}\\d)])\\s*/\\s*(?=[\\p{L}\\d(])")," divisé par ")
            .replace(Regex("(?<=\\d)\\s*[−-]\\s*(?=\\d)")," moins ")
            .replace(Regex("(?<![\\p{L}\\d])([A-Z])(?:eff|max|min|moy|c|L)?(?=\\s*(?:égale|fois|divisé|au carré|au cube))")) {m->letters[m.groupValues[1]].orEmpty()+when {m.value.endsWith("eff")->" efficace";m.value.endsWith("max")->" maximum";m.value.endsWith("min")->" minimum";m.value.endsWith("moy")->" moyen";m.value.endsWith("c")->" cé";m.value.endsWith("L")->" elle";else->""}}
        text=text.replace(Regex("(?<![\\p{L}\\d])(\\d+[.,]\\d+)(?![\\p{L}\\d])")) {number(it.value)}
        text=text.replace(Regex("\uE000(\\d+)\uE001")) {stored[it.groupValues[1].toInt()]}
        return text.replace(Regex("\\.\\s+\\."),".").replace(Regex("[ \\t]+")," ").trim()
    }
}
