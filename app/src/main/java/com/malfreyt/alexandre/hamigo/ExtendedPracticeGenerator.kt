package com.malfreyt.alexandre.hamigo

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.random.Random

/** Finite reproducible variants: every generated ID can be restored for a later SRS review. */
object ExtendedPracticeGenerator {
    private const val seeds=256
    private const val perSeed=20
    private val morse=linkedMapOf(
        "A" to ".-","B" to "-...","C" to "-.-.","D" to "-..","E" to ".","F" to "..-.","G" to "--.","H" to "....",
        "I" to "..","J" to ".---","K" to "-.-","L" to ".-..","M" to "--","N" to "-.","O" to "---","P" to ".--.",
        "Q" to "--.-","R" to ".-.","S" to "...","T" to "-","U" to "..-","V" to "...-","W" to ".--","X" to "-..-",
        "Y" to "-.--","Z" to "--..","0" to "-----","1" to ".----","2" to "..---","3" to "...--","4" to "....-",
        "5" to ".....","6" to "-....","7" to "--...","8" to "---..","9" to "----."
    )
    private val variants by lazy { (0 until seeds).flatMap(::build) }
    fun catalog():List<Question> = variants
    fun resolve(id:String):Question? {
        val parts=id.split('-')
        if(parts.size!=3||parts[0]!="extra")return null
        val seed=parts[1].toIntOrNull()?.takeIf{it in 0 until seeds} ?: return null
        val index=parts[2].toIntOrNull()?.takeIf{it in 0 until perSeed} ?: return null
        return variants[seed*perSeed+index]
    }
    fun create(seed:Long=Random.nextLong(),count:Int=12):List<Question> {
        val start=Math.floorMod(seed,seeds.toLong()).toInt()
        val wanted=count.coerceIn(1,seeds*perSeed)
        val random=Random(seed)
        return (0 until ((wanted+perSeed-1)/perSeed)).flatMap{build((start+it)%seeds).shuffled(random)}.take(wanted)
    }
    fun create(seed:Int,count:Int=12):List<Question> = create(seed.toLong(),count)

    /** Extra questions stay inside concepts already present in the authored lesson. */
    fun lessonExtras(lesson:Lesson,seed:Long=Random.nextLong()):List<Question> {
        val corpus=(lesson.topic+" "+lesson.title+" "+lesson.questions.joinToString(" "){it.prompt}).lowercase()
        val taughtMorse=lesson.questions.flatMap { q ->
            q.choices+q.pairs.map{it.left}+q.bands.mapNotNull{code->morse.entries.firstOrNull{it.value==code.trim()}?.key}
        }.map{it.trim().uppercase()}.filter{it in morse}.toSet()
        val candidates=when {
            "morse" in corpus -> if(taughtMorse.isEmpty())emptyList() else variants.filter{q->
                q.kind in setOf("morseListen","morseEncode")&&q.bands.firstOrNull() in taughtMorse.map(morse::getValue)&&
                    (q.kind!="morseListen"||q.choices.all{it in taughtMorse})
            }
            "décibel" in corpus||"decibel" in corpus||"dbm" in corpus -> variants.filter{it.topic=="Décibels"}
            "binaire" in corpus||"numérique" in corpus||"numerique" in corpus -> variants.filter{it.kind=="binary"}
            "modulation" in corpus||"forme" in corpus&&"signal" in corpus -> variants.filter{it.kind=="waveform"}
            "réactance" in corpus||"reactance" in corpus -> variants.filter{it.topic=="Réactances"}
            "longueur d'onde" in corpus||"longueur d’onde" in corpus -> variants.filter{it.topic=="Longueur d'onde"}
            "condensateur" in corpus&&"association" in corpus -> variants.filter{it.topic=="Associations de condensateurs"}
            ("association" in corpus||"série" in corpus||"parallèle" in corpus)&&"résistance" in corpus -> variants.filter{it.topic=="Associations de résistances"}
            "ohm" in corpus -> variants.filter{it.topic=="Loi d'Ohm"}
            "code" in corpus&&"couleur" in corpus -> variants.filter{it.kind=="resistor"}
            else -> emptyList()
        }
        return candidates.filter{it.id !in lesson.questions.map(Question::id)}.shuffled(Random(seed)).take(2)
    }

    private fun build(seed:Int):List<Question> {
        val random=Random(seed)
        val resistors=listOf(10,22,47,100,220,470,1000)
        val r1=resistors.random(random);val r2=resistors.random(random)
        val letter=morse.keys.random(random);val letter2=morse.keys.random(random)
        val f=listOf(30,50,75,100,150,300).random(random)
        val targetBits=random.nextInt(1,16)
        val truth=random.nextBoolean()
        val ratio=listOf(.1,.5,2.0,4.0,10.0,100.0).random(random)
        val waveform=listOf("sine","am","fm","square").shuffled(random)
        val wantedWave=waveform.random(random)
        val waveDescription=when(wantedWave){"am"->"l'amplitude change et la fréquence porteuse reste constante";"fm"->"l'amplitude reste constante et la fréquence instantanée change";"square"->"le signal saute entre deux niveaux";else->"l'oscillation est une sinusoïde régulière"}
        val digits=listOf("brun","rouge","orange","jaune","vert","bleu","violet","gris","blanc")
        val d1=random.nextInt(1,10);val d2=random.nextInt(1,10);val multiplier=random.nextInt(0,3)
        val resistance=(d1*10+d2)*Math.pow(10.0,multiplier.toDouble())
        val resistorChoices=listOf(resistance,resistance*10,resistance/10,resistance+10).distinct().shuffled(random).map{"${formatNumber(it)} Ω"}
        val fc=listOf(1000.0,2000.0,5000.0,10000.0).random(random)
        val cap=listOf(.1,.22,.47,1.0).random(random)
        val inductance=listOf(.1,.5,1.0,2.0).random(random)
        val powerMw=listOf(1,10,100,1000).random(random)
        val cap1=listOf(10,22,47,100).random(random);val cap2=listOf(10,22,47,100).random(random)
        fun q(index:Int,prompt:String,choices:List<String> = emptyList(),answer:Int=0,explanation:String,topic:String,kind:String="choice",value:Double?=null,unit:String="",tolerance:Double=.01,pairs:List<PairItem> = emptyList(),bands:List<String> = emptyList()) =
            Question("extra-$seed-$index",prompt,choices,answer,explanation,topic=topic,kind=kind,value=value,unit=unit,tolerance=tolerance,pairs=pairs,bands=bands,source="Hamigo · variante procédurale")
        val listenChoices=(listOf(letter)+morse.keys.filter{it!=letter}.shuffled(random).take(3)).shuffled(random)
        return listOf(
            q(0,"Deux résistances de $r1 Ω et $r2 Ω sont en série. Quelle résistance équivalente ?",explanation="En série, on additionne : R = $r1 + $r2 = ${r1+r2} Ω.",topic="Associations de résistances",kind="number",value=(r1+r2).toDouble(),unit="Ω"),
            q(1,"Deux résistances de $r1 Ω et $r2 Ω sont en parallèle. Quelle résistance équivalente ?",explanation="Pour deux résistances : R = R₁R₂ / (R₁ + R₂) = ${formatNumber(r1.toDouble()*r2/(r1+r2))} Ω. Le résultat est inférieur à chacune des deux résistances.",topic="Associations de résistances",kind="number",value=r1.toDouble()*r2/(r1+r2),unit="Ω",tolerance=.2),
            q(2,"La loi d'Ohm s'écrit U = ___ × I.",listOf("R","P","f","C"),0,"U = R × I : tension en volts, résistance en ohms, courant en ampères.","Loi d'Ohm",kind="cloze"),
            q(3,"Coche toutes les associations grandeur–unité électriques correctes.",listOf("Tension → volt","Courant → ampère","Puissance → watt","Résistance → hertz"),explanation="Volt : tension ; ampère : courant ; watt : puissance. Une résistance s'exprime en ohms, pas en hertz.",topic="Grandeurs électriques",kind="multiselect",bands=listOf("0","1","2")),
            q(4,"Forme le nombre $targetBits en binaire avec ces quatre interrupteurs.",explanation="Les poids sont 8, 4, 2 et 1. Additionne les poids allumés : ${targetBits.toString(2).padStart(4,'0')} = $targetBits.",topic="Numérique",kind="binary",value=targetBits.toDouble(),unit="4"),
            q(5,"À résistance constante, doubler la tension ${if(truth)"double"else"divise par deux"} le courant.",listOf("Vrai","Faux"),if(truth)0 else 1,"I = U / R. Si U est multipliée par deux et R reste fixe, I est aussi multiplié par deux.","Loi d'Ohm",kind="truefalse"),
            q(6,"Quelle lettre ou quel chiffre Pico transmet-il ?",listenChoices,listenChoices.indexOf(letter),"$letter se transmet ${morse.getValue(letter).replace(".","●").replace("-","━")}. Un trait dure trois points.","Morse",kind="morseListen",bands=listOf(morse.getValue(letter))),
            q(7,"Compose le code morse du caractère $letter2.",explanation="$letter2 = ${morse.getValue(letter2).replace(".","●").replace("-","━")}. Écoute ta réponse pour relier le geste et le rythme.",topic="Morse",kind="morseEncode",bands=listOf(morse.getValue(letter2))),
            q(8,"Quel tracé convient si $waveDescription ?",waveform.indices.map{"Tracé ${'A'+it}"},waveform.indexOf(wantedWave),"Une AM change l'amplitude ; une FM change la fréquence ; un carré alterne des niveaux ; une sinusoïde reste régulière.","Modulation",kind="waveform",bands=waveform),
            q(9,"Vise la longueur d'onde approximative d'un signal de $f MHz dans le vide.",explanation="λ ≈ 300 / f(MHz) = ${formatNumber(300.0/f)} m.",topic="Longueur d'onde",kind="estimate",value=300.0/f,unit="m",tolerance=.08,bands=listOf("0","11","0.05")),
            q(10,"La puissance de sortie vaut ${formatNumber(ratio)} fois la puissance d'entrée. Quel gain en dB ?",explanation="Pour un rapport de puissances : G = 10 log₁₀(Psortie/Pentrée) = ${formatNumber(10*log10(ratio))} dB.",topic="Décibels",kind="number",value=10*log10(ratio),unit="dB",tolerance=.1),
            q(11,"Décode cette résistance à quatre anneaux.",resistorChoices,resistorChoices.indexOf("${formatNumber(resistance)} Ω"),"Les deux premiers anneaux donnent $d1 et $d2. Le troisième multiplie par 10^$multiplier ; l'or indique ±5 %.","Code des couleurs",kind="resistor",bands=listOf(digits[d1-1],digits[d2-1],listOf("noir","brun","rouge")[multiplier],"or")),
            q(12,"Relie chaque grandeur à son symbole.",explanation="U représente la tension, I le courant, R la résistance et P la puissance.",topic="Grandeurs électriques",kind="match",pairs=listOf(PairItem("Tension","U"),PairItem("Courant","I"),PairItem("Résistance","R"),PairItem("Puissance","P"))),
            q(13,"Range ces fréquences de la plus petite à la plus grande.",listOf("${random.nextInt(1,100)} Hz","${random.nextInt(1,100)} kHz","${random.nextInt(1,100)} MHz","${random.nextInt(1,100)} GHz"),explanation="kilo = 10³, méga = 10⁶, giga = 10⁹. Ici chaque valeur de la famille suivante est plus grande que celles de la précédente.",topic="Unités et préfixes",kind="order"),
            q(14,"À ${formatNumber(fc)} Hz, quelle est la réactance d'un condensateur de ${formatNumber(cap)} µF ?",explanation="Xc = 1 / (2πfC), avec C en farads. C = ${formatNumber(cap)} × 10⁻⁶ F ; Xc ≈ ${formatNumber(1/(2*PI*fc*cap*1e-6))} Ω.",topic="Réactances",kind="number",value=1/(2*PI*fc*cap*1e-6),unit="Ω",tolerance=max(.1,1/(2*PI*fc*cap*1e-6)*.01)),
            q(15,"À ${formatNumber(fc)} Hz, quelle est la réactance d'une bobine de ${formatNumber(inductance)} mH ?",explanation="XL = 2πfL, avec L en henrys. L = ${formatNumber(inductance)} × 10⁻³ H ; XL ≈ ${formatNumber(2*PI*fc*inductance*1e-3)} Ω.",topic="Réactances",kind="number",value=2*PI*fc*inductance*1e-3,unit="Ω",tolerance=max(.15,2*PI*fc*inductance*1e-3*.01)),
            q(16,"Une puissance vaut $powerMw mW. Combien cela représente-t-il en dBm ?",explanation="PdBm = 10 log₁₀(PmW). La référence de 0 dBm vaut 1 mW ; $powerMw mW = ${formatNumber(10*log10(powerMw.toDouble()))} dBm.",topic="Décibels",kind="number",value=10*log10(powerMw.toDouble()),unit="dBm",tolerance=.1),
            q(17,"Deux condensateurs de $cap1 nF et $cap2 nF sont en série. Quelle capacité équivalente ?",explanation="En série, C = C₁C₂ / (C₁ + C₂) = ${formatNumber(cap1.toDouble()*cap2/(cap1+cap2))} nF. La capacité équivalente diminue.",topic="Associations de condensateurs",kind="number",value=cap1.toDouble()*cap2/(cap1+cap2),unit="nF",tolerance=.2),
            q(18,"Si la fréquence double, la longueur d'onde est ___.",listOf("divisée par deux","doublée","inchangée","multipliée par quatre"),0,"λ = c/f : fréquence et longueur d'onde sont inversement proportionnelles.","Longueur d'onde",kind="cloze"),
            q(19,"Coche tous les matériaux normalement utilisés comme isolants électriques.",listOf("Verre","Cuivre","Plastique","Aluminium"),explanation="Le verre et les plastiques sont isolants dans leur usage habituel. Le cuivre et l'aluminium sont conducteurs.",topic="Grandeurs électriques",kind="multiselect",bands=listOf("0","2"))
        )
    }
}
