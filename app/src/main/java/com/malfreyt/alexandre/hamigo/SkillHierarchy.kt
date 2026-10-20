package com.malfreyt.alexandre.hamigo

/** Editorial navigation only. Family IDs are never written as learning evidence. */
data class SkillFamily(val id:String,val title:String,val skillIds:List<String>,val parentId:String?=null)
data class SkillFamilySummary(val family:SkillFamily,val encountered:Int,val due:Int,val fragile:Int,val consolidated:Int)

object SkillHierarchy {
    private fun group(id:String,title:String,parent:String?,vararg skills:String)=SkillFamily("family:$id",title,skills.toList(),parent?.let {"family:$it"})
    val families=listOf(
        group("math","Nombres et calculs",null),
        group("numbers","Lire et comparer","math","number-basics","proportional-reasoning","powers-roots","binary-representation","hexadecimal"),
        group("calculations","Formules et unités","math","metric-prefixes","measurement-units","formula-reading","equation-rearrange","calculation-method"),
        group("electricity","Électricité",null),
        group("dc","Courant, tension et énergie","electricity","closed-circuit","dc-voltage","dc-current","ohm-law","electric-power","energy-duration","battery-capacity"),
        group("networks","Réseaux résistifs et mesures","electricity","series-resistors","parallel-resistors","voltage-divider","kirchhoff-laws","resistor-colours","resistivity","measurement-circuits"),
        group("storage","Condensateurs et bobines","electricity","capacitors","capacitor-ratings","inductors","inductor-switching","rc-transient","stored-energy"),
        group("signals","Signaux et filtres",null),
        group("waveforms","Temps, fréquence et amplitude","signals","radio-wave-basics","signal-shapes","period-frequency","rms-sine","rms-phase","signal-measurement"),
        group("filters","Impédance, résonance et sélection","signals","reactance-impedance","reactance-calculation","impedance-phase","lc-resonance","filter-cutoff","filter-families","filter-quality","transformer-ratios"),
        group("electronics","Électronique",null),
        group("components","Composants et alimentation","electronics","diode-polarity","diode-rectification","regulated-supply","transistor-bias","thermionic-tubes"),
        group("amplifiers","Amplification et boucles","electronics","amplifier-efficiency","amplifier-classes","op-amp-feedback","oscillator-feedback","frequency-stability"),
        group("digital","Logique et information numérique",null,"logic-basic","logic-combined","sampling","symbol-rate","error-control"),
        group("radio","Transmettre et recevoir",null),
        group("modulations","Du signal au message","radio","am-modulation","fm-modulation","ssb-cw","digital-modulation","emission-designation"),
        group("chains","Chaînes radio et défauts","radio","transmitter-chain","receiver-blocks","superheterodyne","receiver-quality","image-frequency","harmonic-spectrum","mixer-products","intermodulation","clean-transmission"),
        group("levels","Puissances, décibels et bruit","radio","decibel-landmarks","decibel-ratios","dbm-reference","signal-noise","noise-bandwidth"),
        group("links","Antennes et liaisons",null),
        group("antennas","Antennes et lignes","links","wavelength","antenna-length","antenna-gain","transmission-line","standing-wave-ratio","electrical-length","line-loss","antenna-measurement","dipole-project","antenna-experiment"),
        group("propagation","Propagation et choix de liaison","links","hf-propagation","satellite-doppler","link-budget","band-mode-choice"),
        group("operator","Pratique et réglementation",null),
        group("operating","Identité et échanges","operator","station-identification","nato-recall","q-codes","qso-procedure","signal-report","operating-records","identification-log"),
        group("rules","Cadre de l'activité","operator","amateur-service","band-allocation","emission-limits","amateur-messages","station-control","cept-travel","installation-rights","automatic-station","regulation-synthesis"),
        group("safety","Installer et diagnostiquer","operator","electrical-safety","rf-mechanical-safety","emc","diagnostic-method","station-plan","experiment-design"),
        group("exploration","Explorer la radio",null),
        group("portable","Sorties et espace","exploration","portable-power","pota-operation","sota-operation","iss-pass","satellite-links"),
        group("applications","Signaux, images et paquets","exploration","sdr-workflow","waterfall-reading","sstv-principles","sstv-reception","aprs-packet","weak-signal-modes"),
        group("morse","Morse",null,"morse-spacing","morse-characters","morse-punctuation","morse-words","morse-callsigns","morse-listening","morse-qso","morse-encoding"),
        group("learning","Apprendre et préparer le certificat",null,"exam-format","exam-method","technical-synthesis","spaced-recall","learning-routine","hamigo-tools")
    )
    private val byId=families.associateBy {it.id}
    private val leafFamily=families.flatMap {family->family.skillIds.map {it to family}}.toMap()
    fun familyFor(skillId:String):SkillFamily?=leafFamily[skillId]
    fun family(id:String):SkillFamily?=byId[id]
    fun descendants(id:String):List<String> {
        val node=byId[id] ?: return listOf(id)
        return node.skillIds+families.filter {it.parentId==id}.flatMap {descendants(it.id)}
    }
    fun ancestry(skillId:String):List<SkillFamily> {
        var current=leafFamily[skillId]
        val result=mutableListOf<SkillFamily>()
        while(current!=null){result.add(0,current);current=current.parentId?.let(byId::get)}
        return result
    }
    /** Difficulty is a choice hint, not a reconstructed score or a parent mastery. */
    fun difficulty(review:SkillReview):Int=when {
        review.last?.correct==false->3
        review.lapses>0&&review.repetitions==0->2
        !review.mastered&&review.attempts>=3&&review.successes*2L<review.attempts->1
        else->0
    }
    fun summary(family:SkillFamily,reviews:Map<String,SkillReview>,now:Long):SkillFamilySummary {
        val evidence=descendants(family.id).filter(SkillLearning::isSrsSkill).mapNotNull(reviews::get)
        return SkillFamilySummary(family,evidence.size,evidence.count {it.due<=now},evidence.count {difficulty(it)>0},evidence.count {it.mastered})
    }
    /** Finest families with observed difficulty. Unknown leaves provide no urgency. */
    fun difficultFamilies(reviews:Map<String,SkillReview>):List<SkillFamily> = families.filter {family->
        family.skillIds.any {skill->SkillLearning.isSrsSkill(skill)&&reviews[skill]?.let {difficulty(it)>0}==true}
    }.sortedWith(compareByDescending<SkillFamily> {family->family.skillIds.sumOf {reviews[it]?.let(::difficulty) ?: 0}}.thenBy {it.id})
}
