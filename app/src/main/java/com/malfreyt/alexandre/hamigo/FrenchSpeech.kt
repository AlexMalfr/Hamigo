package com.malfreyt.alexandre.hamigo

/** Spoken French only: never modifies displayed questions, answers or mathematical meaning. */
internal object FrenchSpeech {
    // Patterns depend on the notation rules, never on a user's input. Reuse them instead of
    // compiling dozens again for every sentence (especially while inspecting a whole bank).
    private val patterns=java.util.concurrent.ConcurrentHashMap<Pair<String,RegexOption?>,Regex>()
    private fun pattern(source:String,option:RegexOption?=null):Regex=patterns.getOrPut(source to option) {
        if(option==null)Regex(source) else Regex(source,option)
    }
    private val digits=listOf("zéro","un","deux","trois","quatre","cinq","six","sept","huit","neuf")
    private val letters=mapOf("U" to "u","I" to "i","R" to "ère","P" to "pé","W" to "double vé","E" to "e",
        "L" to "elle","C" to "cé","S" to "esse","Z" to "zède","V" to "vé","A" to "a","F" to "effe",
        // Let the French engine name Q itself: "ku" can be pronounced /ku/ instead of /ky/.
        "X" to "ixe","Y" to "i grec","T" to "té","Q" to "Q","H" to "ache","B" to "bé","D" to "dé","G" to "gé","J" to "ji","K" to "ka","M" to "emme","N" to "enne","O" to "o",
        "f" to "effe","r" to "ère","t" to "té","u" to "u","l" to "elle","s" to "esse")
    private val greek=mapOf('π' to "pi",'λ' to "lambda",'ω' to "oméga",'ρ' to "rho",'τ' to "tau",'φ' to "phi",'ϕ' to "phi",'θ' to "thêta",'Γ' to "gamma",'Φ' to "phi",'Δ' to "delta",'δ' to "delta",'β' to "bêta",'α' to "alpha",'η' to "êta",'μ' to "mu",'µ' to "mu")
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
    internal fun isUnit(symbol:String)=symbol in units
    private val unitPattern=units.keys.sortedByDescending(String::length).joinToString("|") {Regex.escape(it)}
    private fun unitName(symbol:String,singular:Boolean=false)=units.getValue(symbol).split(' ').mapIndexed {i,word->if(singular&&i==0&&word!="siemens")word.removeSuffix("s") else word}.joinToString(" ")
    private val superDigits="⁰¹²³⁴⁵⁶⁷⁸⁹";private val subDigits="₀₁₂₃₄₅₆₇₈₉"
    private fun morse(code:String,separator:String=", ")=normalizedMorse(code).trim().split(pattern("\\s*/\\s*")).joinToString(", pause entre mots, ") {word->
        word.trim().split(pattern("\\s+")).joinToString(", pause entre lettres, ") {letter->letter.map {if(it=='.')"point" else "trait"}.joinToString(separator)}
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
        val definitions=source.trim().split(pattern("\\s*[·;]\\s*"))
        val pattern=pattern("(point|trait|pause interne|entre lettres|entre mots)\\s+(\\d+)",RegexOption.IGNORE_CASE)
        val entries=definitions.map {pattern.matchEntire(it) ?: return null}
        if(entries.size<2 || entries.none {it.groupValues[1].equals("trait",true)})return null
        return entries.joinToString(" ") {entry->
            val subject=when(entry.groupValues[1].lowercase()) {
                "point"->"Un point";"trait"->"Un trait";"pause interne"->"La pause entre deux éléments";"entre lettres"->"La pause entre deux lettres";else->"La pause entre deux mots"
            }
            val n=entry.groupValues[2];"$subject dure ${if(n=="1")"une unité" else "$n unités"}."
        }
    }
    private fun number(text:String)=text.replace(pattern("(?<=\\d)[.,](\\d+)")) {m->" virgule "+m.groupValues[1].map {digits[it.digitToInt()]}.joinToString(" ")}
    private fun exponent(text:String)=when(text) {"2"->"au carré";"3"->"au cube";else->"puissance ${number(text)}"}
    private fun formula(node:Formula):String=when(node) {
        is Formula.Atom -> if(node.value=="−")"moins" else greek[node.value.singleOrNull()] ?: letters[node.value] ?: number(node.value)
        is Formula.Fraction -> when {
            node.numerator==Formula.Atom("1",false)&&node.denominator==Formula.Atom("2",false)->"un demi"
            node.numerator==Formula.Atom("1",false)&&node.denominator==Formula.Atom("4",false)->"un quart"
            node.numerator==Formula.Atom("3",false)&&node.denominator==Formula.Atom("4",false)->"trois quarts"
            else->"${formula(node.numerator)} divisé par ${formula(node.denominator)}"
        }
        is Formula.Root -> "racine carrée de ${formula(node.value)}"
        is Formula.Power -> "${formula(node.value)} ${exponent(formula(node.exponent))}"
        is Formula.Index -> "${formula(node.value)} "+when((node.index as? Formula.Atom)?.value) {"eff"->"efficace";"max"->"maximum";"min"->"minimum";"moy"->"moyen";else->"indice ${formula(node.index)}"}
        is Formula.Group -> "ouvrir la parenthèse, ${formula(node.value)}, fermer la parenthèse"
        is Formula.Join -> {
            val unit=(node.right as? Formula.Atom)?.value?.let(units::get)
            if(node.symbol.isEmpty()&&(node.left as? Formula.Atom)?.value=="X"&&(node.right as? Formula.Atom)?.value in listOf("L","C"))
                "${formula(node.left)} indice ${formula(node.right)}"
            else if(node.symbol.isEmpty() && node.left is Formula.Atom && !node.left.italic && unit!=null) "${formula(node.left)} $unit"
            else "${formula(node.left)} ${when(node.symbol){"="->"égale";"+"->"plus";"−"->"moins";"×",""->if((node.left as? Formula.Atom)?.value=="−")"" else "fois";else->node.symbol}} ${formula(node.right)}"
        }
    }
    private fun mathSource(text:String)=text.replace("½","1/2 ").replace("¼","1/4 ").replace("¾","3/4 ")
    private fun spokenEquation(text:String):Formula? {
        val parts=text.split('=')
        if(parts.size<2)return null
        val operands=parts.map {part->(MathFormula.parse("U = ${part.trim()}") as? Formula.Join)?.right ?: return null}
        return operands.reduce {left,right->Formula.Join(left,"=",right)}
    }
    fun option(source:String):String {
        if(source.matches(pattern("[A-Za-z]")))return letters[source.uppercase()] ?: source
        if(source.any {it in "/×*−-+√²³^½¼¾"}||source.matches(pattern("\\d+(?:[.,]\\d+)?[UIRPWELSCZVAFXYTQHufrtls](?:eff|max|min|moy|\\d+)?"))) {
            val expression=(MathFormula.parse("U = ${mathSource(source)}") as? Formula.Join)?.right
            if(expression!=null)return formula(expression)
        }
        return source
    }
    fun prepare(source:String):String {
        if(source.isBlank())return source
        timingList(source)?.let {return it}
        MorseExamples.table(source).takeIf {it.isNotEmpty()}?.let {entries->return entries.joinToString(" ",transform=::exampleSpeech)}
        val stored=mutableListOf<String>()
        fun keep(text:String):String {stored+=text;return "\uE000${stored.lastIndex}\uE001"}
        var text=source.replace(pattern("https?://\\S+")) {keep("lien vers la source")}.replace(pattern("_{2,}"),"quelque chose")
        text=text.replace(pattern("\\|([^|\\n]{1,100})\\|")) {keep("la valeur absolue de "+prepare(it.groupValues[1]))}
            .replace(pattern("(?<=\\))\\.")) {keep(".")}
        text=text.replace(pattern("\\b([A-Z](?:,\\s*[A-Z]){2,})(?=[.!?;:])")) {m->
            val items=m.value.split(pattern(",\\s*"));keep(items.dropLast(1).map {letters[it] ?: it}.joinToString(", ")+" et "+letters.getValue(items.last()))
        }
        text=text.replace(pattern("(\\b(?:groupe|groupes|[Éé]pelle|indicatif)\\s+)([A-Z0-9]{2,8})(?![\\p{L}\\d])")) {m->
            m.groupValues[1]+keep(m.groupValues[2].map {if(it.isDigit())digits[it.digitToInt()] else letters.getValue(it.toString())}.joinToString(" "))
        }.replace(pattern("(caractère\\s+)/"),"$1barre oblique")
        // A typographic range is not a subtraction; a mark out of twenty is not a division task.
        text=text.replace(pattern("(\\d+(?:[.,]\\d+)?)\\s*[–—]\\s*(\\d+(?:[.,]\\d+)?)")) {m->"de ${m.groupValues[1]} à ${m.groupValues[2]}"}
            .replace(pattern("\\bde de (?=\\d)"),"de ")
            .replace(pattern("\\b(\\d{1,2})/20\\b")) {m->"${m.groupValues[1]} sur vingt"}

        if(pattern("(?i)\\bunités?\\b").containsMatchIn(source)) {
            text=text.replace(pattern("((?:[Oo]hms?|Ω)\\s+et\\s+)(F|H)(?![\\p{L}])")) {m->m.groupValues[1]+keep(unitName(m.groupValues[2]))}
        }
        // Protect labels first: '/' here names a character, not a transmitted word pause.
        val examples=MorseExamples.find(text)
        examples.asReversed().forEach {example->
            text=text.replaceRange(example.range,keep(exampleSpeech(example)))
        }
        text=text.replace(pattern("(?<![\\p{L}\\p{N}])[?/.=@,](?:\\s*[,·]\\s*[?/.=@,]){1,}")) {keep(punctuationList(it.value))}
        val prefixes=mapOf("k" to "kilo","M" to "méga","G" to "giga","m" to "milli","µ" to "micro","μ" to "micro","n" to "nano","p" to "pico")
        text=text.replace(pattern("\\b1\\s*([kMGmµμnp])\\s*=\\s*10(?:([⁻⁺$superDigits]+)|\\^\\s*([−-]?\\d+))(?![\\p{L}\\p{N}])")) {m->
            val power=m.groupValues[3].ifBlank {m.groupValues[2].map {when(it){'⁻'->'-';'⁺'->'+';else->('0'.code+superDigits.indexOf(it)).toChar()}}.joinToString("")}
            keep("Le préfixe ${prefixes.getValue(m.groupValues[1])} correspond à dix puissance ${power.replace("-","moins ").replace("−","moins ").replace("+","plus ")}.")
        }
        text=text.replace(pattern("(?<![\\p{L}\\d])([−-]?\\d+(?:[.,]\\d+)?)[eE]([+−-]?\\d+)(?![\\p{L}\\d])")) {m->
            keep(number(m.groupValues[1].replace("−","moins ").replace("-","moins "))+" fois dix puissance "+m.groupValues[2].replace("−","moins ").replace("-","moins ").replace("+","plus "))
        }.replace(pattern("(?<!\\d)([01]{2,})₂")) {m->keep(m.groupValues[1].map {digits[it.digitToInt()]}.joinToString(" ")+", en base deux")}
        fun derivative(symbol:String)="le taux de variation de "+(if(symbol.equals("i",true))"l’intensité" else "la tension")+" au cours du temps"
        text=text.replace(pattern("(?<![\\p{L}\\d])([UIRPWELSCZVAFXYTQH])\\s*d([iIuU])\\s*/\\s*dt\\b")) {m->keep(letters.getValue(m.groupValues[1])+" fois "+derivative(m.groupValues[2]))}
        text=text.replace(pattern("\\bd([iIuU])\\s*/\\s*dt\\b")) {m->keep(derivative(m.groupValues[1]))}
        text=text.replace(pattern("(?<![\\p{L}\\d])\\d+\\^\\([^()]{1,80}\\)")) {m->
            val expression=(spokenEquation("U = ${m.value}") as? Formula.Join)?.right
            if(expression==null)m.value else keep(formula(expression))
        }
        text=MathFormula.fragments(mathSource(text),::spokenEquation).joinToString("") {fragment->fragment.formula?.let {keep(formula(it))} ?: fragment.source}
        // An unparsed formula still must not turn its fraction slash into a Morse word gap.
        text=text.replace(pattern("(?<=\\))\\s*/\\s*(?=[(√\\p{L}\\d])")) {keep(" divisé par ")}
        text=morseTextParts(text).joinToString("") {part->if(part.code)keep(if(part.text=="/")"pause entre mots" else morse(part.text)) else part.text}
        // Do not ask the voice engine to guess superscripts, decimal points or SI prefixes.
        text=text.replace(pattern("[⁻⁺$superDigits]+")) {m->
            val power=m.value.map {when(it){'⁻'->'-';'⁺'->'+';else->('0'.code+superDigits.indexOf(it)).toChar()}}.joinToString("")
            " "+exponent(power.replace("-","moins ").replace("+","plus "))+" "
        }.replace(pattern("[$subDigits]+")) {m->" indice "+m.value.map {('0'.code+subDigits.indexOf(it)).toChar()}.joinToString("")+" "}
        text=text.replace("ⁿ"," puissance enne ")
        text=text.replace(pattern("\\^\\s*([−-]?\\d+)")) {m->" "+exponent(m.groupValues[1].replace("-","moins ").replace("−","moins "))+" "}
        text=text.replace(pattern("\\b\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})+\\b")) {it.value.filterNot(Char::isWhitespace)}
        text=text.replace(pattern("(?<![\\p{L}\\d])(m/s|km/h)(?![\\p{L}\\d])")) {m->keep(if(m.value=="m/s")"mètres par seconde" else "kilomètres par heure")}
        if(pattern("(?i)\\bunités?\\b").containsMatchIn(source)) {
            text=text.replace(pattern("(?<![\\p{L}\\d])($unitPattern)\\s*/\\s*($unitPattern)(?![\\p{L}\\d])")) {m->keep(unitName(m.groupValues[1])+" par "+unitName(m.groupValues[2],true))}
            text=text.replace(pattern("(?<=\\bdonne)\\s+($unitPattern)(?![\\p{L}’'])")) {m->" "+keep(unitName(m.groupValues[1]))}
        }
        text=text.replace(pattern("([−-]?\\d+(?:[.,]\\d+)?)\\s*($unitPattern)(?![\\p{L}’'])")) {m->
            keep(number(m.groupValues[1].replace("−","moins ").replace("-","moins "))+" "+unitName(m.groupValues[2],kotlin.math.abs(m.groupValues[1].replace('−','-').replace(',','.').toDouble())==1.0))
        }
        text=text.replace(pattern("(?<=\\b(?:en|unité|unités))\\s+($unitPattern)(?![\\p{L}’'])",RegexOption.IGNORE_CASE)) {m->
            val unit=units[m.groupValues[1]];if(unit==null)m.value else " "+keep(unit)
        }
        text=text.replace(pattern("(?<![\\p{L}])($unitPattern)(?![\\p{L}’'])")) {m->
            val symbol=m.value
            when {symbol.length>1 || symbol in listOf("Ω","%","°")->keep(units.getValue(symbol));else->m.value}
        }
        text=text.replace(pattern("(?<![\\p{L}\\d])[A-Z]{1,3}\\d[A-Z]{1,5}(?:/[A-Z0-9]+)?\\b")) {m->
            keep(m.value.map {when {it=='/'->"barre oblique";it.isDigit()->digits[it.digitToInt()];else->letters[it.toString()] ?: it.toString()}}.joinToString(" "))
        }
        text=text.replace(pattern("\\b(?:Q[A-Z]{2}|CQ|RST|RS|VHF|UHF|HF|BF|RF|CW|SSB|BLU|AM|FM|FSK|PSK|DSP|ADC|DAC|AOP|UTC|ANFR|ARCEP|UIT|CEPT|IARU|QCM|ROS|PAR|RF|FI|OL|RC|LC|FV|CAN|CNA|BPSK|QPSK|RLC|FET|PM|USB|LSB|SI|IEC|CEI|CRC|SDR|AGC|PIRE|LNA|BFO|VFO|SWR|PTT|SNR|RTTY|OFDM|NRZ)\\b")) {m->keep(m.value.map {letters[it.toString()] ?: it.toString()}.joinToString(" "))}
        text=text.replace(pattern("\\b[AFGHRJBCDNP][0-9][A-Z]\\b")) {m->keep(m.value.map {if(it.isDigit())digits[it.digitToInt()] else letters.getValue(it.toString())}.joinToString(" "))}
        text=text.replace(pattern("\\b(?:ET|OU|NON|NAND|NOR|XOR)\\b")) {m->when(m.value){"NAND"->"non et";"NOR"->"non ou";"XOR"->"ou exclusif";else->m.value.lowercase()}}
        val indexed=mapOf("XL" to "ixe indice elle","XC" to "ixe indice cé","EC" to "e indice cé","EL" to "e indice elle",
            "Rtot" to "résistance totale","Req" to "résistance équivalente","Us" to "tension de sortie","Ue" to "tension d’entrée",
            "Up" to "tension primaire","Ns" to "nombre de spires secondaires","Np" to "nombre de spires primaires",
            "Ip" to "courant primaire","Is" to "courant secondaire","fc" to "fréquence porteuse","fm" to "fréquence de modulation",
            "Fmax" to "fréquence maximale","f0" to "fréquence propre","GdB" to "gain en décibels")
        text=text.replace(pattern("(?<![\\p{L}\\d])("+indexed.keys.joinToString("|")+")(?![\\p{L}\\d])")) {keep(indexed.getValue(it.value))}
        // Never leave M. to be expanded as « Monsieur », or S as seconds outside a unit context.
        text=text.replace(pattern("log\\s*(?:indice\\s*)?10\\b"),"logarithme en base dix")
            .replace(pattern("\\bln\\b"),"logarithme népérien").replace(pattern("\\bcos\\b"),"cosinus").replace(pattern("\\bsin\\b"),"sinus").replace(pattern("\\btan\\b"),"tangente")
        text=text.map {greek[it]?.let {word->" $word "} ?: it.toString()}.joinToString("")
        text=text.replace("@"," arobase ").replace("√"," racine carrée de ").replace("≈"," environ égal à ").replace("≠"," différent de ")
            .replace("≤"," inférieur ou égal à ").replace("≥"," supérieur ou égal à ").replace("<"," inférieur à ").replace(">"," supérieur à ")
            .replace("±"," plus ou moins ").replace("×"," fois ").replace("÷"," divisé par ").replace("∞"," infini ")
            .replace("="," égale ").replace("+"," plus ").replace("−"," moins ").replace("→"," vers ").replace("↔"," correspond à ")
            .replace("∧"," et ").replace("∨"," ou ").replace("¬"," non ").replace("⊕"," ou exclusif ")
            .replace("Ω"," ohms ").replace("_", " ").replace("·", ". ")
        text=text.replace(pattern("(?<=[\\p{L}\\d)])\\s*/\\s*(?=[\\p{L}\\d(])")," divisé par ")
            .replace(pattern("(?<=\\d)\\s*[−-]\\s*(?=\\d)")," moins ")
            .replace(pattern("(?<![\\p{L}\\d])([A-Z])(eff|max|min|moy|c|L)?(?=\\s*(?:égale|fois|divisé|au carré|au cube))")) {m->letters[m.groupValues[1]].orEmpty()+when(m.groupValues[2]) {"eff"->" efficace";"max"->" maximum";"min"->" minimum";"moy"->" moyen";"c"->" cé";"L"->" elle";else->""}}
        text=text.replace(pattern("(?<![\\p{L}\\d])(\\d+[.,]\\d+)(?![\\p{L}\\d])")) {number(it.value)}
        if(source.contains('=')||source.any {it in "²³½¼¾^"}) {
            // Conservative fallback: spell an unresolved symbol group instead of letting the
            // engine reinterpret LI as 51. Do not invent a product or turn actual numbers into letters.
            text=text.replace(pattern("(?<![\\p{L}\\d])([UIRPWELSCZVAFXYTQH]{2,4})(?![\\p{L}\\d])")) {m->keep(m.value.map {letters.getValue(it.toString())}.joinToString(" "))}
        }
        text=text.replace(pattern("(?<![\\p{L}\\d])([UIRPWELSCZVAFXYTQHfrtul])(\\d+)(?![\\p{L}\\d])")) {m->keep(letters.getValue(m.groupValues[1])+" indice "+m.groupValues[2])}
        // Last, after operators have been verbalized: protecting letters earlier hides the slash.
        text=text.replace(pattern("(?<![\\p{L}\\d’'])([A-Z])(?![\\p{L}\\d’'])")) {m->keep(letters[m.value] ?: m.value)}
        text=text.replace(pattern("\uE000(\\d+)\uE001")) {stored[it.groupValues[1].toInt()]}
        return text.replace(pattern("\\.\\s+\\."),".").replace(pattern("[ \\t]+")," ").trim()
    }
}
