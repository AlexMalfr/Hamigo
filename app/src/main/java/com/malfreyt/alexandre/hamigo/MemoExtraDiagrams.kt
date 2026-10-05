package com.malfreyt.alexandre.hamigo

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Diagrams supplement the factual rows; unknown kinds deliberately render nothing. */
@Composable fun MemoExtraDiagram(row: RefRow) {
    if (!row.visual.startsWith("extra:")) return
    val kind = row.visual.removePrefix("extra:")
    val table = ExtraTableData(kind)
    if (table != null) {
        if (kind == "logic-table") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(0, 1, 2, 3, 7), listOf(0, 1, 4, 5, 6)).forEachIndexed { index, columns ->
                    ExtraReferenceTable(table.copy(headings = columns.map(table.headings::get), rows = table.rows.map { row -> columns.map(row::get) }, caption = if (index == 1) table.caption else ""))
                }
            }
        } else ExtraReferenceTable(table)
        return
    }
    if (kind == "preferred-series") { ExtraPreferredSeries(row.term); return }
    val shared = when (kind) {
        "series" -> "series-r"
        "parallel" -> "parallel-r"
        "divider", "sine", "rc-charge", "transformer" -> kind
        "standing-wave" -> "reflection"
        "am-spectrum" -> "am-bandwidth"
        else -> null
    }
    if (shared != null) { TechnicalReferenceDiagram(row.copy(visual = shared)); return }
    val blocks = when (kind) {
        "power-supply" -> listOf("Alternatif", "Transformateur", "Redresseur", "Filtrage", "Régulateur", "Charge")
        "receiver-direct" -> listOf("Antenne", "Filtre / ampli RF", "Démodulateur", "Ampli audio", "Haut-parleur")
        "receiver-superhet" -> listOf("Antenne", "Filtre RF", "Mélangeur + OL", "Filtre / ampli FI", "Démodulateur", "Audio")
        "transmitter" -> listOf("Microphone", "Ampli audio", "Modulateur + OL", "Ampli RF", "Filtre final", "Antenne")
        "pll" -> listOf("Référence", "Comparateur", "Filtre de boucle", "VCO", "Diviseur N", "Retour comparateur")
        "dds" -> listOf("Horloge", "Accumulateur de phase", "Calcul / table", "CNA (DAC)", "Filtre passe-bas", "Signal analogique")
        "emc" -> listOf("Source", "Couplage conduit ou rayonné", "Appareil perturbé")
        else -> null
    }
    if (blocks != null) {
        ExtraBlockDiagram(blocks, if (kind == "pll") "Boucle fermée : fVCO / N rejoint la référence" else "Suivre les étapes dans l’ordre des numéros")
        return
    }
    if (kind !in extraSketchKinds) return
    val caption = when (kind) {
        "junction" -> "Courants entrants = courants sortants"
        "battery" -> "E : tension à vide · Ri : résistance interne · R : charge"
        "meter-voltage" -> "V mesure la tension en parallèle de R"
        "meter-current" -> "A est traversé par le courant de la branche"
        "filter-low", "filter-high" -> "Circuit RC idéal non chargé · courbe de puissance relative"
        "filter-band", "filter-notch" -> "Courbe de puissance relative en fonction de la fréquence"
        "resonance-series" -> "L et C en série : opposition minimale à f₀"
        "resonance-parallel" -> "L et C en parallèle : opposition maximale à f₀"
        "pi-network" -> "Deux capacités vers la masse, une inductance en série"
        "diode" -> "Sens conventionnel direct : anode A vers cathode K"
        "mixer" -> "Multiplier les signaux crée somme et différence de fréquences"
        "opamp-inverting" -> "R₂ renvoie la sortie sur l’entrée − ; entrée + à la référence"
        "direct-wave" -> "La hauteur et les obstacles influencent la visibilité radio"
        "ionosphere" -> "Réfraction ionosphérique et retour sur Terre : dessin de principe"
        "dipole" -> "Deux bras λ/4, alimentés au centre · total λ/2"
        "ground-plane" -> "Un brin λ/4 et des radians forment l’antenne"
        "yagi" -> "Réflecteur plus long · dipôle alimenté · directeurs plus courts"
        "coax" -> "Âme et blindage séparés par un diélectrique"
        "ssb-spectrum" -> "BLS représentée : une bande latérale, porteuse supprimée"
        "cw-wave" -> "CW : porteuse présente puis absente ; transitions adoucies"
        "fm-wave" -> "FM : amplitude constante, espacement des périodes variable"
        "transistor-symbols" -> "La flèche de l’émetteur sort pour NPN et entre pour PNP · B base, C collecteur, E émetteur"
        "filter-lc-low" -> "Passe-bas LC : L en série et C vers la masse"
        "filter-lc-high" -> "Passe-haut LC : C en série et L vers la masse"
        else -> "Schéma de principe"
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.fillMaxWidth().height(139.dp).semantics { contentDescription = "Schéma de ${row.term}. $caption" }) {
            ExtraSketch(kind)
        }
        Text(caption, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

private data class ExtraTable(val headings: List<String>, val rows: List<List<String>>, val caption: String)

private fun ExtraTableData(kind: String): ExtraTable? = when (kind) {
    "logic-table" -> ExtraTable(
        listOf("A", "B", "ET", "OU", "NON A", "NAND", "NOR", "XOR"),
        (0..3).map { pair ->
            val a = pair / 2; val b = pair % 2
            listOf(a, b, a and b, a or b, 1 - a, 1 - (a and b), 1 - (a or b), a xor b).map(Int::toString)
        },
        "0 = faux / niveau bas · 1 = vrai / niveau haut. NON n’utilise que A ; les autres portes utilisent A et B."
    )
    "binary-table" -> ExtraTable(
        listOf("Décimal", "Binaire", "Hexa"),
        (0..15).map { listOf(it.toString(), it.toString(2).padStart(4, '0'), it.toString(16).uppercase()) },
        "Quatre bits couvrent 0 à 15 : en hexadécimal, A = 10 jusqu’à F = 15."
    )
    "reflection-table" -> ExtraTable(
        listOf("ROS", "|Γ|", "TOS %", "P réfl. %"),
        listOf(listOf("1", "0", "0", "0"), listOf("1,1", "0,048", "4,8", "0,23"), listOf("1,25", "0,111", "11,1", "1,23"), listOf("1,5", "0,2", "20", "4"), listOf("2", "0,333", "33,3", "11,1"), listOf("3", "0,5", "50", "25")),
        "Convention du cours : TOS = 100 × |Γ|. La puissance réfléchie vaut 100 × |Γ|² % de la puissance incidente. Valeurs arrondies."
    )
    "s-meter-table" -> ExtraTable(
        listOf("Signal", "dB / S9", "Tension µV"),
        listOf(listOf("S0", "−54", "0,1"), listOf("S1", "−48", "0,2"), listOf("S2", "−42", "0,4"), listOf("S3", "−36", "0,8"), listOf("S4", "−30", "1,5"), listOf("S5", "−24", "3"), listOf("S6", "−18", "6"), listOf("S7", "−12", "12"), listOf("S8", "−6", "25"), listOf("S9", "0", "50"), listOf("S9 + 10", "+10", "160"), listOf("S9 + 20", "+20", "500"), listOf("S9 + 30", "+30", "1 600")),
        "Référence HF du cours, sous 50 Ω : 6 dB par point S, soit environ ×2 en tension. Un S-mètre réel n’est pas toujours étalonné ainsi."
    )
    "db-table" -> ExtraTable(
        listOf("Gain dB", "P₂ / P₁", "U₂ / U₁"),
        listOf(listOf("−20", "0,01", "0,1"), listOf("−10", "0,1", "0,316"), listOf("−6", "≈ 1/4", "≈ 1/2"), listOf("−3", "≈ 1/2", "≈ 0,707"), listOf("0", "1", "1"), listOf("+3", "≈ 2", "≈ 1,414"), listOf("+6", "≈ 4", "≈ 2"), listOf("+10", "10", "≈ 3,162"), listOf("+20", "100", "10")),
        "Pour comparer les tensions : même impédance. Les valeurs de 3 et 6 dB sont des approximations usuelles."
    )
    "db-unit-table" -> ExtraTable(
        listOf("dB", "Rapport P", "Dizaines dB", "Rapport P"),
        (0..9).map { index -> listOf(index.toString(), listOf("1", "1,26", "1,58", "≈ 2", "2,51", "3,16", "≈ 4", "≈ 5", "6,31", "≈ 8")[index], (index * 10).toString(), if (index == 0) "1" else "10" + listOf("", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹")[index]) },
        "Additionner les dB multiplie les rapports : pour 23 dB, 100 × 2 ≈ 200. La formule exacte est 10^(dB/10)."
    )
    "series-parallel-table" -> ExtraTable(
        listOf("Grandeur", "Série", "Parallèle"),
        listOf(listOf("R équiv.", "Σ Rᵢ", "1 / Σ (1/Rᵢ)"), listOf("Tension", "Uᵢ = U × Rᵢ/R", "Uᵢ = U"), listOf("Courant", "Iᵢ = I", "Iᵢ = I × R/Rᵢ"), listOf("Puissance", "Pᵢ = P × Rᵢ/R", "Pᵢ = P × R/Rᵢ")),
        "R, U, I et P désignent les valeurs de l’ensemble. Rᵢ désigne une résistance ; les relations supposent des résistances idéales."
    )
    "rlc-table" -> ExtraTable(
        listOf("Circuit à f₀", "Impédance", "Qualité Q"),
        listOf(listOf("Série R–L–C", "R", "√(L/C) / R"), listOf("L // C ; r série L", "≈ L / (C × r)", "≈ √(L/C) / r"), listOf("R // L // C", "R", "R / √(L/C)")),
        "f₀ = 1 / (2π√LC). Pour la deuxième ligne, les pertes de la bobine doivent être faibles : Q ≫ 1. Ne pas confondre résistance de pertes en série et résistance parallèle."
    )
    else -> null
}

@Composable private fun ExtraReferenceTable(table: ExtraTable) {
    val small = table.headings.size > 5
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Tableau : ${table.headings.joinToString()}. ${table.caption}" }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (listOf(table.headings) + table.rows).forEachIndexed { index, cells ->
            Surface(color = if (index == 0) Color(0xFFD9E9E3) else if (index % 2 == 0) Color(0xFFF0F4EF) else Color(0xFFFAFBF8), shape = RoundedCornerShape(5.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    cells.forEach { cell ->
                        Text(cell, Modifier.weight(1f).padding(horizontal = 1.dp), textAlign = TextAlign.Center, color = if (index == 0) Teal else Ink, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium, fontSize = if (small) 9.sp else 11.sp, lineHeight = if (small) 12.sp else 15.sp)
                    }
                }
            }
        }
        if (table.caption.isNotBlank()) Text(table.caption, Modifier.padding(top = 3.dp), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ExtraPreferredSeries(term: String) {
    val values = when (term) {
        "Série E6" -> listOf(10, 15, 22, 33, 47, 68)
        "Série E12" -> listOf(10, 12, 15, 18, 22, 27, 33, 39, 47, 56, 68, 82)
        "Série E24" -> listOf(10, 11, 12, 13, 15, 16, 18, 20, 22, 24, 27, 30, 33, 36, 39, 43, 47, 51, 56, 62, 68, 75, 82, 91)
        else -> return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value -> Surface(color = Color(0xFFE7F0ED), shape = RoundedCornerShape(8.dp)) { Text(value.toString(), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ExtraBlockDiagram(blocks: List<String>, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            blocks.forEachIndexed { index, label ->
                Surface(color = Color(0xFFE7F0ED), shape = RoundedCornerShape(10.dp)) {
                    Text("${index + 1}  $label", Modifier.padding(horizontal = 9.dp, vertical = 8.dp), color = Teal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (index != blocks.lastIndex) Text("→", Modifier.padding(top = 7.dp), color = Teal, fontSize = 15.sp)
            }
        }
        Text(caption, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

private val extraSketchKinds = setOf("junction", "battery", "meter-voltage", "meter-current", "filter-low", "filter-high", "filter-lc-low", "filter-lc-high", "filter-band", "filter-notch", "resonance-series", "resonance-parallel", "pi-network", "diode", "mixer", "opamp-inverting", "direct-wave", "ionosphere", "dipole", "ground-plane", "yagi", "coax", "ssb-spectrum", "cw-wave", "fm-wave", "transistor-symbols")

private fun DrawScope.ExtraSketch(kind: String) {
    val w = size.width; val h = size.height; val pen = 2.dp.toPx(); val accent = Color(0xFFD38C36)
    fun p(x: Float, y: Float) = Offset(w * x, h * y)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = Teal) = drawLine(color, p(x1,y1), p(x2,y2), pen)
    fun label(s: String, x: Float, y: Float, color: Color = Ink) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = 11.sp.toPx(); textAlign = Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.drawText(s,w*x,h*y,paint)
    }
    fun arrow(x1: Float,y1: Float,x2: Float,y2: Float,color: Color=Teal) {
        line(x1,y1,x2,y2,color); val v=p(x2-x1,y2-y1); val length=kotlin.math.hypot(v.x,v.y).coerceAtLeast(1f)
        val n=v/length; val tip=7.dp.toPx(); val e=p(x2,y2)
        drawLine(color,e,e-Offset(n.x*tip-n.y*tip/2,n.y*tip+n.x*tip/2),pen)
        drawLine(color,e,e-Offset(n.x*tip+n.y*tip/2,n.y*tip-n.x*tip/2),pen)
    }
    fun resistor(x: Float,y: Float,vertical: Boolean=false) {
        val sz=if(vertical)Size(w*.05f,h*.22f) else Size(w*.16f,h*.12f)
        drawRect(Teal,p(x,y)-Offset(sz.width/2,sz.height/2),sz,style=Stroke(pen))
    }
    fun capacitor(x: Float,y: Float,vertical: Boolean=false) {
        if(vertical){line(x-.045f,y-.025f,x+.045f,y-.025f);line(x-.045f,y+.025f,x+.045f,y+.025f)}
        else {line(x-.014f,y-.12f,x-.014f,y+.12f);line(x+.014f,y-.12f,x+.014f,y+.12f)}
    }
    fun coil(x: Float,y: Float,vertical: Boolean=false) {
        val path=Path()
        for(i in 0..80){val t=i/80f;val wave=sin(t*PI*8).toFloat()*.035f
            val point=if(vertical)p(x+wave,y-.14f+t*.28f)else p(x-.12f+t*.24f,y+wave)
            if(i==0)path.moveTo(point.x,point.y)else path.lineTo(point.x,point.y)}
        drawPath(path,Teal,style=Stroke(pen))
    }
    fun ground(x: Float,y: Float){line(x,y,x,y+.06f);line(x-.05f,y+.06f,x+.05f,y+.06f);line(x-.033f,y+.10f,x+.033f,y+.10f);line(x-.015f,y+.14f,x+.015f,y+.14f)}
    fun meter(s: String,x: Float,y: Float){drawCircle(Teal,13.dp.toPx(),p(x,y),style=Stroke(pen));label(s,x,y+.035f)}
    fun axis(){arrow(.09f,.80f,.94f,.80f,Muted);arrow(.09f,.80f,.09f,.13f,Muted);label("f",.94f,.95f);label("Niveau",.17f,.12f)}
    when(kind){
        "junction" -> {drawCircle(accent,5.dp.toPx(),p(.50f,.5f));arrow(.1f,.5f,.47f,.5f);arrow(.50f,.53f,.5f,.89f);arrow(.52f,.48f,.9f,.2f);label("I₁",.22f,.43f);label("I₂",.74f,.2f);label("I₃",.58f,.8f)}
        "battery" -> {line(.12f,.53f,.18f,.53f);line(.18f,.33f,.18f,.73f);line(.21f,.43f,.21f,.63f);line(.21f,.53f,.42f,.53f);resistor(.50f,.53f);line(.58f,.53f,.77f,.53f);resistor(.80f,.65f,true);line(.80f,.76f,.80f,.90f);line(.80f,.90f,.12f,.90f);line(.12f,.90f,.12f,.53f);label("E",.19f,.24f);label("Ri",.50f,.35f);label("R",.90f,.68f);arrow(.63f,.33f,.77f,.33f);label("I",.68f,.24f)}
        "meter-voltage", "meter-current" -> {
            line(.13f,.45f,.42f,.45f);resistor(.50f,.45f);line(.58f,.45f,.87f,.45f);label("R",.5f,.25f)
            if(kind=="meter-voltage"){line(.31f,.45f,.31f,.80f);line(.31f,.80f,.46f,.80f);meter("V",.5f,.8f);line(.54f,.80f,.70f,.80f);line(.70f,.80f,.70f,.45f)}
            else {meter("A",.24f,.45f);arrow(.63f,.70f,.83f,.70f);label("I",.7f,.92f)}
        }
        "filter-low", "filter-high" -> {
            val low=kind=="filter-low";line(.08f,.35f,.21f,.35f);line(.38f,.35f,.51f,.35f);line(.45f,.35f,.45f,.48f)
            if(low){resistor(.30f,.35f);capacitor(.45f,.54f,true)}else{capacitor(.30f,.35f);resistor(.45f,.59f,true)}
            line(.45f,.64f,.45f,.76f);ground(.45f,.76f);label("Entrée",.12f,.19f);label("Sortie",.47f,.2f)
            line(.65f,.75f,.97f,.75f,Muted);line(.65f,.75f,.65f,.2f,Muted);label("f",.94f,.94f)
            val path=Path();for(i in 0..80){val t=i/80f;val value=if(low)1f/(1f+exp((t-.45f)*10))else 1f-1f/(1f+exp((t-.45f)*10));val a=p(.65f+t*.30f,.73f-value*.47f);if(i==0)path.moveTo(a.x,a.y)else path.lineTo(a.x,a.y)};drawPath(path,accent,style=Stroke(pen))
        }
        "filter-band", "filter-notch" -> {axis();val path=Path();for(i in 0..100){val t=i/100f;val band=exp(-((t-.5f)*(t-.5f))*30);val value=if(kind=="filter-band")band else 1-band;val a=p(.1f+t*.8f,.76f-value*.5f);if(i==0)path.moveTo(a.x,a.y)else path.lineTo(a.x,a.y)};drawPath(path,Teal,style=Stroke(pen));label("f₀",.5f,.95f)}
        "resonance-series" -> {line(.08f,.5f,.19f,.5f);coil(.31f,.5f);line(.43f,.5f,.61f,.5f);capacitor(.64f,.5f);line(.66f,.5f,.91f,.5f);label("L",.31f,.3f);label("C",.64f,.3f);label("À f₀ : XL = XC",.5f,.88f)}
        "resonance-parallel" -> {line(.1f,.5f,.3f,.5f);line(.3f,.25f,.3f,.75f);line(.3f,.25f,.42f,.25f);coil(.54f,.25f);line(.66f,.25f,.78f,.25f);line(.78f,.25f,.78f,.75f);line(.78f,.5f,.92f,.5f);line(.3f,.75f,.52f,.75f);capacitor(.55f,.75f);line(.57f,.75f,.78f,.75f);label("L",.54f,.1f);label("C",.54f,.98f)}
        "pi-network" -> {line(.08f,.25f,.38f,.25f);coil(.5f,.25f);line(.62f,.25f,.92f,.25f);listOf(.23f,.78f).forEach{x->line(x,.25f,x,.5f);capacitor(x,.53f,true);line(x,.56f,x,.76f);ground(x,.76f)};label("C₁",.12f,.58f);label("C₂",.90f,.58f);label("L",.50f,.11f)}
        "diode" -> {line(.10f,.55f,.38f,.55f);val path=Path().apply{moveTo(w*.38f,h*.35f);lineTo(w*.38f,h*.75f);lineTo(w*.61f,h*.55f);close()};drawPath(path,Teal,style=Stroke(pen));line(.61f,.35f,.61f,.75f);line(.61f,.55f,.9f,.55f);label("A +",.18f,.33f);label("K −",.83f,.33f);arrow(.35f,.9f,.65f,.9f,accent)}
        "transistor-symbols" -> {
            listOf(.26f, .75f).forEachIndexed { index, x ->
                val base = x - .045f
                line(base, .36f, base, .68f); line(x - .17f, .52f, base, .52f)
                line(base, .43f, x + .075f, .28f); line(x + .075f, .28f, x + .075f, .16f)
                line(base, .61f, x + .075f, .77f); line(x + .075f, .77f, x + .075f, .87f)
                if (index == 0) arrow(x - .015f, .65f, x + .065f, .755f, accent) else arrow(x + .065f, .755f, x - .015f, .65f, accent)
                label("B", x - .19f, .55f); label("C", x + .13f, .23f); label("E", x + .13f, .84f)
                label(if (index == 0) "NPN" else "PNP", x, .1f, Teal)
            }
        }
        "filter-lc-low", "filter-lc-high" -> {
            val low = kind == "filter-lc-low"
            line(.07f,.28f,.20f,.28f)
            if (low) { coil(.32f,.28f); line(.44f,.28f,.75f,.28f); capacitor(.65f,.56f,true); line(.65f,.28f,.65f,.535f); line(.65f,.585f,.65f,.78f) }
            else { line(.20f,.28f,.295f,.28f); capacitor(.31f,.28f); line(.325f,.28f,.75f,.28f); line(.65f,.28f,.65f,.42f); coil(.65f,.56f,true); line(.65f,.70f,.65f,.78f) }
            ground(.65f,.78f); label("Entrée",.11f,.13f); label("Sortie",.82f,.31f); label(if(low) "L" else "C",.32f,.49f); label(if(low) "C" else "L",.77f,.58f)
        }
        "mixer" -> {label("f₁",.12f,.28f);label("f₂",.12f,.72f);arrow(.20f,.25f,.40f,.47f);arrow(.20f,.70f,.40f,.53f);drawCircle(Teal,21.dp.toPx(),p(.48f,.50f),style=Stroke(pen));label("×",.48f,.54f);arrow(.57f,.5f,.71f,.5f);label("f₁ + f₂",.82f,.40f);label("|f₁ − f₂|",.82f,.64f)}
        "opamp-inverting" -> {val t=Path().apply{moveTo(w*.48f,h*.4f);lineTo(w*.48f,h*.83f);lineTo(w*.73f,h*.61f);close()};drawPath(t,Teal,style=Stroke(pen));line(.1f,.49f,.24f,.49f);resistor(.32f,.49f);line(.40f,.49f,.48f,.49f);label("R₁",.32f,.68f);label("−",.52f,.53f);label("+",.52f,.77f);line(.73f,.61f,.92f,.61f);line(.82f,.61f,.82f,.20f);line(.82f,.20f,.65f,.20f);resistor(.57f,.20f);line(.49f,.20f,.43f,.20f);line(.43f,.20f,.43f,.49f);label("R₂",.57f,.11f);line(.48f,.73f,.36f,.73f);ground(.36f,.73f);label("Us",.88f,.82f);label("Ue",.12f,.35f)}
        "direct-wave", "ionosphere" -> {
            line(.08f,.82f,.92f,.82f,Muted);line(.16f,.82f,.16f,.62f);line(.84f,.82f,.84f,.62f);label("Station A",.18f,.97f);label("Station B",.82f,.97f)
            if(kind=="direct-wave")arrow(.17f,.62f,.83f,.62f)
            else{val roof=Path().apply{moveTo(w*.06f,h*.20f);quadraticBezierTo(w*.5f,h*.02f,w*.94f,h*.20f)};drawPath(roof,accent,style=Stroke(3.dp.toPx()));arrow(.18f,.61f,.5f,.13f);arrow(.5f,.13f,.82f,.61f);label("Région ionisée",.5f,.36f,accent)}
        }
        "dipole" -> {line(.1f,.4f,.48f,.4f);line(.52f,.4f,.9f,.4f);line(.48f,.4f,.48f,.8f);line(.52f,.4f,.52f,.8f);label("λ / 4",.29f,.26f);label("λ / 4",.71f,.26f);label("Alimentation",.5f,.97f);drawCircle(accent,3.dp.toPx(),p(.48f,.4f));drawCircle(accent,3.dp.toPx(),p(.52f,.4f))}
        "ground-plane" -> {line(.5f,.17f,.5f,.65f);line(.5f,.65f,.21f,.9f);line(.5f,.65f,.79f,.9f);line(.5f,.65f,.50f,.97f);label("λ / 4",.65f,.40f);label("Radians",.23f,.65f);label("Alimentation",.81f,.72f);drawCircle(accent,4.dp.toPx(),p(.5f,.65f))}
        "yagi" -> {line(.15f,.55f,.88f,.55f,Muted);listOf(.20f,.43f,.65f,.84f).forEachIndexed{i,x->val height=.36f-i*.05f;line(x,.55f-height,x,.55f+height)};label("Réflecteur",.2f,.10f);label("Dipôle",.43f,.17f);label("Directeurs",.75f,.23f);arrow(.57f,.94f,.92f,.94f,accent)}
        "coax" -> {drawRoundRect(Teal,p(.12f,.22f),Size(w*.76f,h*.60f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),style=Stroke(3.dp.toPx()));drawRect(Color(0xFFE8ECE5),p(.15f,.27f),Size(w*.70f,h*.50f));line(.13f,.52f,.88f,.52f,accent);label("Blindage",.33f,.15f);label("Diélectrique",.52f,.40f);label("Âme",.51f,.68f,accent)}
        "ssb-spectrum" -> {axis();line(.45f,.80f,.45f,.30f,Muted);val t=Path().apply{moveTo(w*.50f,h*.75f);lineTo(w*.79f,h*.32f);lineTo(w*.79f,h*.75f);close()};drawPath(t,Teal.copy(alpha=.2f));drawPath(t,Teal,style=Stroke(pen));label("fc",.45f,.96f);label("BLS",.70f,.23f);label("porteuse supprimée",.38f,.12f)}
        "cw-wave", "fm-wave" -> {line(.07f,.52f,.95f,.52f,Muted);val path=Path();var phase=0.0;for(i in 0..500){val t=i/500f;val amplitude=if(kind=="cw-wave")if(t<.32f||t>.57f)1f else 0f else 1f;phase+=if(kind=="fm-wave").21+.11*sin(t*PI*3)else .23;val a=p(.08f+t*.85f,.52f-sin(phase).toFloat()*.24f*amplitude);if(i==0)path.moveTo(a.x,a.y)else path.lineTo(a.x,a.y)};drawPath(path,Teal,style=Stroke(pen));label("Temps →",.80f,.94f);label(if(kind=="cw-wave")"ON          OFF          ON"else "Fréquence variable",.50f,.12f)}
    }
}
