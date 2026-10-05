package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun syncDate(value: String?): String = value?.let {
    runCatching {
        DateTimeFormatter.ofPattern("dd/MM à HH:mm")
            .withZone(ZoneId.systemDefault()).format(Instant.parse(it))
    }.getOrNull()
} ?: "pas encore effectuée"

/** The same account controls are available from the team and preferences. */
@Composable
fun GitHubConnection(model: AppModel, modifier: Modifier = Modifier) {
    val connected = remember(model.revision) { runCatching { model.sync.tokens.get() != null }.getOrDefault(false) }
    val login = remember(model.revision) { model.sync.accountLogin }
    val lastSync = remember(model.revision) { model.sync.lastSyncedAt }
    val lastError = remember(model.revision) { model.sync.lastSyncError }
    val automatic = remember(model.revision) { model.progress.prefs.getBoolean("autoSync", true) }
    var details by remember { mutableStateOf(false) }
    Panel(modifier, color = Mist) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.CloudSync, null, tint = Teal, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("SYNCHRONISATION GITHUB")
                Text(if (connected) "Connecté · ${login ?: "GitHub"}" else "Retrouve ton voyage partout",
                    fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink)
            }
        }
        model.oauthStatus?.let {Text(it,color=Muted,fontSize=12.sp,lineHeight=17.sp)}
        if (connected) {
            Text("Dernière synchro : ${syncDate(lastSync)}", color = Muted, fontSize = 12.sp)
            if (lastError != null) {
                Surface(color = Color(0xFFFFE8E0), shape = RoundedCornerShape(12.dp)) {
                    Text(lastError, Modifier.padding(10.dp), color = Ink, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Synchronisation automatique", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Tes révisions suivent ton compte.", color = Muted, fontSize = 12.sp)
                }
                Switch(automatic, { model.setAutoSync(it) }, enabled = !model.busy)
            }
            Action(if (model.busy) "Synchronisation…" else "Synchroniser maintenant", enabled = !model.busy) {
                model.refreshSocial(manual = true)
            }
        } else if (model.oauthSession != null) {
            GitHubDeviceCode(model)
        } else if (model.authSession != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ model.openGitHubBrowser() }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Rounded.OpenInBrowser, null); Spacer(Modifier.width(6.dp)); Text("Rouvrir GitHub")
                }
                TextButton({ model.cancelTask() }, contentPadding = PaddingValues(0.dp)) { Text("Annuler") }
            }
        } else {
            Text("Connecte ton compte pour sauvegarder tes leçons, ton XP et tes révisions, et partager ta progression avec ton équipe.",
                color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Action(if (model.busy) "Connexion en cours…" else "Se connecter avec GitHub", enabled = !model.busy) {
                model.startGitHubConnection()
            }
            Text("Le compte GitHub est facultatif. Ton apprentissage reste enregistré sur ce téléphone.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        TextButton({ details = !details }, contentPadding = PaddingValues(0.dp)) {
            Icon(if (details) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            Spacer(Modifier.width(6.dp))
            Text("Données sauvegardées et fréquence", fontSize = 12.sp)
        }
        if (details) {
            Text("La sauvegarde complète conserve tes leçons, tes réponses, ton calendrier de révision, ton XP et tes préférences. Un second Gist contient les statistiques partagées avec ton équipe.",
                fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
            Text("Les deux Gists sont non répertoriés : ils ne figurent pas dans les recherches publiques de GitHub, mais toute personne possédant leur URL peut les lire. Le lien d’invitation donne accès uniquement aux statistiques, jamais au Gist de sauvegarde.",
                fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
            Text("Lecture et écriture au retour dans l’app, en fin de séance et après tes réponses regroupées pendant 8 secondes. En arrière-plan, Android essaie environ une fois par heure lorsque le réseau est disponible. Le bouton ci-dessus permet une mise à jour immédiate.",
                fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
            if (connected) TextButton({ model.disconnectGitHub() }, enabled = !model.busy, contentPadding = PaddingValues(0.dp)) {
                Text("Déconnecter GitHub")
            }
        }
    }
}

@Composable
fun FriendsScreen(model: AppModel) {
    val context = LocalContext.current
    val own = remember(model.revision) { model.progress.snapshot() }
    val invitation = remember(model.revision) { model.sync.savedGistUrl?.let { FriendInvite.link(it) } }
    var showQr by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var receivedLink by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf<String?>(null) }
    val ranking = (listOf(own) + model.friends.map { it.progress }).sortedByDescending { it.weeklyXp }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start=16.dp,top=16.dp,end=16.dp,bottom=16.dp+LocalNavigationContentOverlap.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BigTitle("Sur la même fréquence", "En équipe, on garde le signal.") }
        item { GitHubConnection(model) }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pico(Modifier.size(64.dp), mood = MascotMood.HAPPY)
                    Column(Modifier.weight(1f)) {
                        Text("Ton équipe radio", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Un lien ou un QR suffit pour vous retrouver.", color = Muted, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ showQr = true }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = invitation != null && !model.busy) {
                        Icon(Icons.Rounded.QrCode2, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp)); Text("Inviter")
                    }
                    OutlinedButton({ addOpen = true }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !model.busy) {
                        Icon(Icons.Rounded.PersonAdd, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(6.dp)); Text("Ajouter")
                    }
                }
                if (invitation == null) Text("Connecte GitHub ci-dessus pour créer ton lien d’invitation.", fontSize = 12.sp, color = Muted)
                Action("Partager le classement") {
                    NativeShare.teamImage(context, own, model.friends.map { it.progress })
                }
                Text("Une image de votre équipe, avec vos graphiques de progression.", fontSize = 11.sp, color = Muted)
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Le sprint des 7 jours", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Leaderboard, null, tint = Teal)
                }
                ranking.forEachIndexed { index, profile ->
                    val isOwn = profile === own
                    Row(Modifier.fillMaxWidth().background(if (isOwn) Mist else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${index + 1}", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (index == 0) Coral else Teal, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name + if (isOwn) " · toi" else "", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${profile.streak} jours de série · ${profile.lessons} leçons", fontSize = 11.sp, color = Muted)
                        }
                        Text("${profile.weeklyXp} XP", fontWeight = FontWeight.ExtraBold, color = Teal, fontSize = 15.sp)
                    }
                }
                if (model.friends.isEmpty()) Text("Invite un équipier pour suivre vos progrès et vous encourager.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
            }
        }
        items(model.friends, key = { it.gist.ifBlank { it.progress.name } }) { friend ->
            var menuOpen by remember { mutableStateOf(false) }
            val socialGist = remember(friend.gist) { runCatching { GitHubSync.gistPageUrl(friend.gist) }.getOrNull() }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(friend.progress.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${friend.progress.xp} XP · ${friend.progress.lessons} leçons", color = Muted, fontSize = 12.sp)
                    }
                    Box {
                        IconButton({ menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Options de ${friend.progress.name}") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (socialGist != null) DropdownMenuItem(
                                text = { Text("Ouvrir le Gist social") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                onClick = { menuOpen = false; openLink(context, socialGist) }
                            )
                            DropdownMenuItem(
                                text = { Text("Retirer cet équipier") },
                                leadingIcon = { Icon(Icons.Rounded.PersonRemove, null) },
                                onClick = { menuOpen = false; model.removeFriend(friend) }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mis à jour le ${syncDate(friend.progress.updatedAt)}", fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
                    TextButton({ NativeShare.nudge(context, friend.progress.name) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.WavingHand, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Encourager", fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            OutlinedButton({ model.refreshSocial(manual = true) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !model.busy) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (model.busy) "Actualisation…" else "Actualiser l’équipe")
            }
        }
    }
    if (showQr && invitation != null) {
        val qr = remember(invitation) { FriendInvite.qr(invitation).asImageBitmap() }
        AlertDialog(onDismissRequest = { showQr = false }, title = { Text("Invite ton équipe") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(qr, "QR code d’invitation Hamigo", Modifier.size(224.dp))
                Text("Ton ami ouvre ce lien ou scanne le QR avec l’appareil photo. Hamigo proposera de t’ajouter à son équipe.", fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedButton({ NativeShare.text(context, invitation, "Mon invitation Hamigo") }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(8.dp)); Text("Partager le lien")
                }
                OutlinedButton({ NativeShare.inviteImage(context, invitation) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Partager le QR")
                }
            }
        }, confirmButton = { TextButton({ showQr = false }) { Text("Fermer") } })
    }
    if (addOpen) {
        AlertDialog(onDismissRequest = { addOpen = false }, title = { Text("Ajouter un équipier") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Colle le lien d’invitation Hamigo reçu. Tu peux aussi l’ouvrir directement depuis votre conversation.", fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedTextField(receivedLink, { receivedLink = it.take(512); inviteError = null },
                    label = { Text("Lien d’invitation Hamigo") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = inviteError != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }, confirmButton = {
            TextButton({
                val parsed = FriendInvite.parse(receivedLink.trim())
                if (parsed == null) inviteError = "Ce lien n’est pas une invitation Hamigo valide."
                else { model.pendingInvite = parsed; addOpen = false; receivedLink = "" }
            }, enabled = receivedLink.isNotBlank() && !model.busy) { Text("Continuer") }
        }, dismissButton = { TextButton({ addOpen = false }) { Text("Annuler") } })
    }
}

@Composable
fun SettingsScreen(model: AppModel) {
    val context = LocalContext.current
    val p = model.displayedProgress ?: model.progress
    var name by remember { mutableStateOf(p.name) }
    var hour by remember { mutableStateOf(p.prefs.getInt("reminderHour", 20).toString()) }
    var minute by remember { mutableStateOf(p.prefs.getInt("reminderMinute", 0).toString().padStart(2, '0')) }
    var reminderEnabled by remember { mutableStateOf(p.prefs.getBoolean("reminderEnabled", false)) }
    var permissionForTest by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    val backupGist = remember(model.revision) { model.sync.savedBackupUrl }
    val socialGist = remember(model.revision) { model.sync.savedGistUrl }
    fun configure(on: Boolean) {
        if (!on) {
            reminderEnabled = false
            DailyReminder.configure(context, false, p.prefs.getInt("reminderHour", 20), p.prefs.getInt("reminderMinute", 0))
            p.prefs.edit().putLong("preferencesUpdatedAt", System.currentTimeMillis()).apply()
            model.refresh(); model.refreshSocial(); return
        }
        val h = hour.toIntOrNull(); val m = minute.toIntOrNull()
        if (h !in 0..23 || m !in 0..59) { model.message = "Choisis une heure de 00:00 à 23:59."; return }
        reminderEnabled = true
        DailyReminder.configure(context, true, h!!, m!!)
        p.prefs.edit().putLong("preferencesUpdatedAt", System.currentTimeMillis()).apply()
        model.refresh(); model.refreshSocial()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (permissionForTest) DailyReminder.showTest(context) else configure(true) }
        else { if (!permissionForTest) configure(false); model.message = "L’autorisation de notification n’a pas été accordée." }
        permissionForTest = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.task { model.incoming = withContext(Dispatchers.IO) { readImport(context.contentResolver, uri) } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader("À ta fréquence", "Tes préférences et ton compte.") { model.route = "profile" } }
        item {
            Panel {
                Text("Ton identité radio", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Pseudo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Action("Enregistrer le pseudo", enabled = name.isNotBlank()) {
                    p.name = name.trim(); model.refresh(); model.refreshSocial(); model.message = "Pseudo enregistré."
                }
                Text("Objectif quotidien", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 30, 60, 100).forEach { goal ->
                        FilterChip(p.dailyGoal == goal, { p.setDailyGoal(goal); model.refresh(); model.refreshSocial() }, label = { Text("$goal XP", fontSize = 12.sp) })
                    }
                }
                Text("Une leçon de huit réponses justes rapporte environ 30 XP à sa première validation.", fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            }
        }
        item { GitHubConnection(model) }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Le rendez-vous radio", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Un message de Pico chaque jour, à l’heure locale.", fontSize = 12.sp, color = Muted)
                    }
                    Switch(reminderEnabled, { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionForTest = false; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else configure(on)
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Heure") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Minute") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ configure(reminderEnabled) }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Enregistrer l’heure", fontSize = 12.sp) }
                    OutlinedButton({
                        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionForTest = true; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else DailyReminder.showTest(context)
                    }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Tester le rappel", fontSize = 12.sp) }
                }
                Text("Android peut décaler le rappel pour préserver la batterie.", fontSize = 11.sp, color = Muted)
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sauvegarde manuelle", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Une copie de secours et tes Gists GitHub.", fontSize = 12.sp, color = Muted)
                    }
                    IconButton({ backupExpanded = !backupExpanded }) {
                        Icon(if (backupExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (backupExpanded) "Masquer les options de sauvegarde" else "Afficher les options de sauvegarde")
                    }
                }
                if (backupExpanded) {
                    Text("La copie contient tout ton apprentissage. Tes identifiants GitHub n’y figurent jamais.", fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                    OutlinedButton({ NativeShare.backup(context, p.export()) }, Modifier.fillMaxWidth()) { Text("Exporter une sauvegarde") }
                    TextButton({ picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restaurer une sauvegarde") }
                    if (backupGist != null || socialGist != null) {
                        Text("Consulter sur GitHub", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            if (backupGist != null) TextButton({ openLink(context, backupGist) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Gist de sauvegarde", fontSize = 12.sp)
                            }
                            if (socialGist != null) TextButton({ openLink(context, socialGist) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp)); Text("Gist social", fontSize = 12.sp)
                            }
                        }
                        Text("Non répertoriés, mais lisibles avec leur lien. Évite de partager ta sauvegarde complète.", fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
                    }
                }
            }
        }
        item {
            Panel {
                Text("Sources & version", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val lessonCount = model.content?.lessons?.size ?: 0
                Text("Hamigo ${BuildConfig.VERSION_NAME}\n$lessonCount leçons · banque Exam1 REF hors ligne\nVérification pédagogique : 3 octobre 2026",
                    fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                    TextButton({ openLink(context, "http://f6kgl.free.fr/COURS.html") },Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)) {Box(Modifier.fillMaxWidth()){Text("Cours F6KGL · CC BY-NC-SA 4.0",fontSize=12.sp)}}
                    TextButton({ openLink(context, "https://exam1.r-e-f.org/") },Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)) {Box(Modifier.fillMaxWidth()){Text("Questions communautaires Exam1 · REF",fontSize=12.sp)}}
                    TextButton({ openLink(context, "https://www.anfr.fr/gerer/radioamateurs/les-certificats") },Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)) {Box(Modifier.fillMaxWidth()){Text("Certificat · informations ANFR",fontSize=12.sp)}}
                }
                Text("Entraînement indépendant de l’ANFR. Certaines formulations communautaires peuvent être anciennes ; leur source est consultable pendant les révisions.", fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            }
        }
    }
}
