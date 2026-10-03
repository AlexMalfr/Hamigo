package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun openLink(context:android.content.Context,url:String) {runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
@Composable fun FriendsScreen(model:AppModel) {
    val context=LocalContext.current
    var gist by remember{mutableStateOf("")}
    var pasted by remember{mutableStateOf("")}
    var pasteOpen by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)model.task {model.incoming=withContext(Dispatchers.IO){readImport(context.contentResolver,uri)}}
    }
    val own=model.progress.snapshot()
    val ranking=(listOf(own)+model.friends.map{it.progress}).sortedByDescending{it.weeklyXp}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {BigTitle("Sur la même fréquence","À deux, c'est plus facile de garder le signal.")}
        item {Panel(color=Mist){Row(verticalAlignment=Alignment.CenterVertically){Pico(Modifier.size(85.dp));Column(Modifier.weight(1f)){Text("Ton équipe radio",fontSize=22.sp,fontWeight=FontWeight.Bold);Text("Un défi amical, à votre rythme.",color=Muted,fontSize=13.sp)}};Action("Partager mon image"){NativeShare.progressImage(context,own)};OutlinedButton({NativeShare.snapshot(context,own)},Modifier.fillMaxWidth()){Text("Envoyer mon profil pour comparaison")}}}
        item {Panel {Text("Le sprint des 7 jours",fontSize=20.sp,fontWeight=FontWeight.Bold)
            ranking.forEachIndexed{index,profile->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${index+1}",fontSize=20.sp,fontWeight=FontWeight.ExtraBold,color=if(index==0)Coral else Teal,modifier=Modifier.width(35.dp));Column(Modifier.weight(1f)){Text(profile.name+if(profile===own)" · toi" else "",fontWeight=FontWeight.Bold);Text("🔥 ${profile.streak} jours · ${profile.lessons} leçons",fontSize=12.sp,color=Muted)};Text("${profile.weeklyXp} XP",fontWeight=FontWeight.ExtraBold,color=Teal)}}
            if(model.friends.isEmpty())Text("Ajoute le profil de ton ami pour comparer vos progressions.",fontSize=12.sp,color=Muted)
        }}
        items(model.friends){friend->Panel {Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(friend.progress.name,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("${friend.progress.xp} XP · ${friend.progress.lessons} leçons",color=Muted,fontSize=13.sp)};IconButton({model.removeFriend(friend)}){Icon(Icons.Rounded.Close,"Retirer cet ami")}}
            Text("Mis à jour le "+runCatching{DateTimeFormatter.ofPattern("dd/MM à HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(friend.progress.updatedAt))}.getOrDefault("—"),fontSize=11.sp,color=Muted)
            OutlinedButton({NativeShare.nudge(context,friend.progress.name)},Modifier.fillMaxWidth()){Icon(Icons.Rounded.WavingHand,null);Spacer(Modifier.width(8.dp));Text("Envoyer un petit coup d'antenne")}
        }}
        item {Panel {Text("Inviter un équipier",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Importe son profil JSON, colle son profil ou ajoute son lien Gist pour l'actualiser automatiquement.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            OutlinedButton({picker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},Modifier.fillMaxWidth()){Text("Importer un fichier de profil")}
            TextButton({pasteOpen=true}){Text("Coller un profil JSON")}
            OutlinedTextField(gist,{gist=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Lien Gist de ton ami")})
            Action("Ajouter ce lien",enabled=gist.isNotBlank()&&!model.busy){model.readFriend(gist)}
            OutlinedButton({model.refreshSocial(true)},Modifier.fillMaxWidth(),enabled=!model.busy){Text(if(model.busy)"Actualisation…" else "Actualiser les progressions")}
        }}
        item {TextButton({model.route="settings"}){Text("Configurer ma synchronisation GitHub")}}
    }
    if(pasteOpen) AlertDialog(onDismissRequest={pasteOpen=false},title={Text("Un profil reçu")},text={OutlinedTextField(pasted,{pasted=it},label={Text("JSON du profil")},minLines=4,maxLines=7)},confirmButton={TextButton({model.importFriend(pasted);pasteOpen=false}){Text("Ajouter")}},dismissButton={TextButton({pasteOpen=false}){Text("Annuler")}})
}

@Composable fun SettingsScreen(model:AppModel) {
    val context=LocalContext.current
    val p=model.progress
    var name by remember{mutableStateOf(p.name)}
    var hour by remember{mutableStateOf(p.prefs.getInt("reminderHour",20).toString())}
    var minute by remember{mutableStateOf(p.prefs.getInt("reminderMinute",0).toString().padStart(2,'0'))}
    var enabled by remember{mutableStateOf(p.prefs.getBoolean("reminderEnabled",false))}
    var token by remember{mutableStateOf("")}
    var clientId by remember{mutableStateOf(p.prefs.getString("oauthClient","")!!)}
    var oauth by remember{mutableStateOf<DeviceOAuth.Session?>(null)}
    var advanced by remember{mutableStateOf(false)}
    var permissionForTest by remember{mutableStateOf(false)}
    val connected=runCatching{model.sync.tokens.get()!=null}.getOrDefault(false)
    fun configure(on:Boolean) {
        if(!on) {enabled=false;DailyReminder.configure(context,false,p.prefs.getInt("reminderHour",20),p.prefs.getInt("reminderMinute",0));model.refresh();return}
        val h=hour.toIntOrNull();val m=minute.toIntOrNull()
        if(h !in 0..23 || m !in 0..59) {model.message="Choisis une heure de 00:00 à 23:59.";return}
        enabled=on;DailyReminder.configure(context,on,h!!,m!!);model.refresh()
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted) {if(permissionForTest)DailyReminder.showTest(context) else configure(true)} else {if(!permissionForTest)configure(false);model.message="L'autorisation de notification n'a pas été accordée."}
        permissionForTest=false
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)model.task{model.incoming=withContext(Dispatchers.IO){readImport(context.contentResolver,uri)}}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item{PageHeader("À ta fréquence","Tes préférences et tes sauvegardes."){model.route="profile"}}
        item{Panel {Text("Ton identité radio",fontSize=20.sp,fontWeight=FontWeight.Bold);OutlinedTextField(name,{name=it.take(40)},label={Text("Pseudo")},singleLine=true,modifier=Modifier.fillMaxWidth());Action("Enregistrer le pseudo"){p.name=name;model.refresh();model.message="Pseudo enregistré."}
            Text("Objectif quotidien",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf(20,30,50).forEach{goal->FilterChip(p.dailyGoal==goal,{p.prefs.edit().putInt("dailyGoal",goal).apply();model.refresh()},label={Text("$goal XP")})}}
        }}
        item{Panel {Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Le rendez-vous radio",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Un rappel quotidien, à l'heure locale.",fontSize=12.sp,color=Muted)};Switch(enabled,{on->if(on&&Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else configure(on)})}
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(hour,{hour=it.take(2)},label={Text("Heure")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(minute,{minute=it.take(2)},label={Text("Minute")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f),singleLine=true)}
            OutlinedButton({configure(enabled)},Modifier.fillMaxWidth()){Text("Enregistrer l'heure")}
            TextButton({if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {permissionForTest=true;permission.launch(Manifest.permission.POST_NOTIFICATIONS)} else DailyReminder.showTest(context)}){Text("Tester la notification")}
            Text("Android peut décaler légèrement le rappel pour préserver la batterie.",fontSize=11.sp,color=Muted)
        }}
        item{Panel {Text("Sauvegarder mon voyage",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Le fichier contient tes leçons, tes révisions et ton XP. Garde-le pour changer de téléphone.",fontSize=13.sp,color=Muted,lineHeight=20.sp);Action("Exporter ma sauvegarde"){NativeShare.backup(context,p.export())};OutlinedButton({picker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},Modifier.fillMaxWidth()){Text("Restaurer une sauvegarde")}}}
        item{Panel {Text("GitHub · jouer ensemble",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("La synchronisation publie uniquement ton pseudo, XP, série de jours et nombre de leçons dans un Gist secret. Toute personne qui possède le lien peut lire ce résumé.",fontSize=13.sp,lineHeight=21.sp,color=Muted)
            if(connected) {
                Text("Compte connecté",fontWeight=FontWeight.Bold,color=Teal)
                var auto by remember{mutableStateOf(p.prefs.getBoolean("autoSync",true))}
                Row(verticalAlignment=Alignment.CenterVertically){Text("Sync à l'ouverture et après une séance",modifier=Modifier.weight(1f),fontSize=13.sp);Switch(auto,{auto=it;p.prefs.edit().putBoolean("autoSync",it).apply()})}
                model.sync.savedGistUrl?.let{url->OutlinedButton({NativeShare.text(context,url,"Mon profil Hamigo")},Modifier.fillMaxWidth()){Text("Partager mon lien de progression")}}
                Action("Synchroniser maintenant",enabled=!model.busy){model.task{model.sync.push(p.snapshot());model.message="Progression synchronisée."}}
                TextButton({model.sync.disconnect();model.refresh()}){Text("Déconnecter GitHub")}
            } else {
                OutlinedTextField(token,{token=it},label={Text("Jeton personnel GitHub · scope gist")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
                Action(if(model.busy)"Connexion…" else "Connecter et synchroniser",enabled=token.isNotBlank()&&!model.busy){val value=token;token="";model.connect(value)}
                TextButton({openLink(context,"https://github.com/settings/tokens/new?scopes=gist&description=Hamigo")}){Text("Créer un jeton limité aux Gists")}
                TextButton({advanced=!advanced}){Text("Connexion OAuth avec mon application GitHub")}
                if(advanced) {
                    Text("Crée une application OAuth GitHub avec Device Flow activé, puis colle son Client ID. Aucun secret client n'est nécessaire.",fontSize=12.sp,color=Muted)
                    OutlinedTextField(clientId,{clientId=it},label={Text("Client ID OAuth")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    OutlinedButton({model.task {try {p.prefs.edit().putString("oauthClient",clientId).apply();val s=DeviceOAuth.start(clientId);oauth=s;val value=DeviceOAuth.awaitToken(clientId,s);model.sync.connect(value);model.sync.push(p.snapshot());model.message="Compte GitHub connecté."} finally {oauth=null}}},Modifier.fillMaxWidth(),enabled=!model.busy&&clientId.isNotBlank()){Text("Lancer la connexion OAuth")}
                }
            }
        }}
        item{Panel {Text("Sources & version",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Hamigo ${BuildConfig.VERSION_NAME}\n56 leçons originales · banque Exam1 REF hors ligne\nVérification pédagogique : 3 octobre 2026",fontSize=13.sp,lineHeight=21.sp,color=Muted)
            TextButton({openLink(context,"http://f6kgl.free.fr/COURS.html")}){Text("Cours F6KGL · CC BY-NC-SA 4.0")}
            TextButton({openLink(context,"https://exam1.r-e-f.org/")}){Text("Questions communautaires Exam1 · REF")}
            TextButton({openLink(context,"https://www.anfr.fr/gerer/radioamateurs/les-certificats")}){Text("Certificat · informations ANFR")}
            Text("Entraînement indépendant de l'ANFR. Certaines questions communautaires peuvent conserver des formulations anciennes ; leur source est consultable pendant les révisions.",fontSize=11.sp,color=Muted,lineHeight=17.sp)
        }}
    }
    oauth?.let{s->AlertDialog(onDismissRequest={model.cancelTask();oauth=null},title={Text("Connexion GitHub")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Entre ce code sur github.com/login/device :");Text(s.userCode,fontSize=30.sp,fontWeight=FontWeight.ExtraBold,color=Teal);Text("Cette fenêtre attend ta validation sur GitHub.");LinearProgressIndicator(Modifier.fillMaxWidth())}},confirmButton={TextButton({openLink(context,s.verificationUri)}){Text("Ouvrir GitHub")}},dismissButton={TextButton({model.cancelTask();oauth=null}){Text("Annuler")}})}
}
