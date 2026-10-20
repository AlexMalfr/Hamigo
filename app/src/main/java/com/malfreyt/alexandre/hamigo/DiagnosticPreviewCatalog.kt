package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.UUID

internal data class DiagnosticPreviewEntry(val title:String,val category:String,val description:String)
internal object DiagnosticPreviewCatalog {
    val entries=listOf(
        DiagnosticPreviewEntry("Onboarding","Accueil","Les étapes de première ouverture et le choix du Morse."),
        DiagnosticPreviewEntry("Guide Hamigo","Accueil","Le parcours guidé avec Pico et la vraie navigation."),
        DiagnosticPreviewEntry("Parcours · début","Parcours","Aucun cours terminé."),
        DiagnosticPreviewEntry("Parcours · chapitres terminés","Parcours","Des cours cochés et les sections du parcours."),
        DiagnosticPreviewEntry("Défis · aucun examen","Examens","Historique vide et examen personnalisé fermé."),
        DiagnosticPreviewEntry("Défis · deux examens","Examens","Deux examens complets, avant le seuil du personnalisé."),
        DiagnosticPreviewEntry("Défis · trois examens","Examens","Trois examens complets, personnalisé disponible si la banque l’est."),
        DiagnosticPreviewEntry("Historique · aucun examen","Examens","État vide de l’historique et des statistiques."),
        DiagnosticPreviewEntry("Historique · deux examens","Examens","Courbes avec deux points, sans prolongement de tendance."),
        DiagnosticPreviewEntry("Historique · trois examens","Examens","Courbes, sujets à reprendre, transcripts et seuil du personnalisé."),
        DiagnosticPreviewEntry("Fin d’examen","Examens","Bilan avec réponses, erreurs et corrections."),
        DiagnosticPreviewEntry("Mémo · bibliothèque","Mémo","Liste des fiches, recherche et outils."),
        DiagnosticPreviewEntry("Mémo · fiche","Mémo","Une vraie fiche de référence et ses outils."),
        DiagnosticPreviewEntry("Moi · début","Profil et équipe","Niveau initial, calendrier vide et progression."),
        DiagnosticPreviewEntry("Moi · série en cours","Profil et équipe","Une série et de l’activité quotidienne."),
        DiagnosticPreviewEntry("Moi · objectif atteint","Profil et équipe","Objectif atteint et journée remplie."),
        DiagnosticPreviewEntry("Moi · série cassée","Profil et équipe","Retour après interruption."),
        DiagnosticPreviewEntry("Équipe · aucun ami","Profil et équipe","Liste d’amis vide, sans invitation réelle."),
        DiagnosticPreviewEntry("Équipe · deux amis","Profil et équipe","Deux profils fictifs et leurs statistiques."),
        DiagnosticPreviewEntry("Paramètres","Paramètres","Réglages natifs dans le modèle isolé."),
        DiagnosticPreviewEntry("Fin de cours · réussite","Bilans de cours","Une séance validée."),
        DiagnosticPreviewEntry("Fin de cours · à consolider","Bilans de cours","Une séance à retravailler.")
    )+DiagnosticStressPreviews.presets.map {DiagnosticPreviewEntry(it,"Cas limites","Textes, noms ou statistiques développés pour inspecter la mise en page.")}

    fun search(query:String,category:String=""):List<DiagnosticPreviewEntry> {
        val words=NarrationAudit.fold(query).split(Regex("\\s+")).filter(String::isNotBlank)
        return entries.filter {entry->(category.isBlank()||entry.category==category)&&words.all {it in NarrationAudit.fold("${entry.title} ${entry.category} ${entry.description}")}}
    }

    /** Complete immutable transcripts, exclusively in DiagnosticContext memory. */
    fun seedExams(model:AppModel,count:Int) {
        check(model.diagnosticModel)
        require(count in 0..3)
        val history=JSONObject()
        val now=System.currentTimeMillis()
        repeat(count) {attemptIndex->
            val regulation=8+attemptIndex*3
            val technique=10+attemptIndex*2
            val responses=List(40) {index->
                val reg=index<20
                val correct=(if(reg)index else index-20)<(if(reg)regulation else technique)
                ExamResponse(index,"diagnostic-exam-$attemptIndex-$index",if(reg)"regulation" else "technique",
                    if(reg)listOf("Identification","Bandes","Pratique")[index%3] else listOf("Électricité","Antennes","Signaux")[index%3],
                    if(reg)"Exemple fictif : un indicatif identifie une station." else "Exemple fictif : 1 kΩ correspond à 1 000 Ω.",
                    listOf("Vrai","Faux"),0,if(correct)0 else 1,false,
                    if(reg)"L’indicatif permet d’identifier la station." else "Le préfixe kilo représente mille.","Diagnostics · examen simulé")
            }
            val at=now-(count-attemptIndex)*86_400_000L
            val attempt=ExamAttempt(UUID.nameUUIDFromBytes("diagnostic-exam-$attemptIndex".toByteArray()).toString(),at,at-900_000,900_000,
                regulation,technique,responses)
            history.put(attempt.id,ExamHistory.write(attempt))
        }
        val root=JSONObject(model.progress.prefs.getString("progress","{}") ?: "{}")
        root.put("examHistory",history)
        model.progress.prefs.edit().putString("progress",root.toString()).commit()
        model.refresh()
    }
}

@Composable internal fun DiagnosticPreviews(query:String,changeQuery:(String)->Unit,category:String,changeCategory:(String)->Unit,
    listState:LazyListState,choose:(String)->Unit) {
    val matches=remember(query,category){DiagnosticPreviewCatalog.search(query,category)}
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal=8.dp)) {
        OutlinedTextField(query,changeQuery,label={Text("Page, état ou catégorie")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,
            modifier=Modifier.fillMaxWidth().testTag("diagnostic-preview-search"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            (listOf("")+DiagnosticPreviewCatalog.entries.map {it.category}.distinct()).forEach {value->
                FilterChip(category==value,{changeCategory(value)},label={Text(value.ifBlank {"Tout"},fontSize=11.sp)})
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("diagnostic-preview-list"),state=listState,contentPadding=PaddingValues(top=4.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            item {Text("Données fictives en mémoire, sans connexion externe ni progression personnelle.",fontSize=12.sp,color=Muted)}
            matches.groupBy {it.category}.forEach {(category,entries)->
                item(key="category-$category"){Text(category,fontWeight=FontWeight.Bold,fontSize=14.sp,modifier=Modifier.padding(top=4.dp))}
                items(entries,key={it.title}) {entry->OutlinedButton({choose(entry.title)},Modifier.fillMaxWidth().testTag("diagnostic-preview-${entry.title}")) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(entry.title,fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Text(entry.description,fontSize=11.sp,lineHeight=15.sp,color=Muted)
                    }
                }}
            }
            if(matches.isEmpty())item {Text("Aucun aperçu trouvé.",color=Muted)}
        }
    }
}
