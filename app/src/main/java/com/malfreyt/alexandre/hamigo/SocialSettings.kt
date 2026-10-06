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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
@Composable private fun SyncPanel(modifier: Modifier = Modifier, controlsOnly: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if(controlsOnly)Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp),content=content)
    else Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=Mist) {
        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)
    }
}

@Composable
fun GitHubConnection(model: AppModel, modifier: Modifier = Modifier, controlsOnly: Boolean = false, showGistLinks: Boolean = false) {
    val context = LocalContext.current
    val connected = remember(model.revision) { runCatching { model.sync.tokens.get() != null }.getOrDefault(false) }
    val login = remember(model.revision) { model.sync.accountLogin }
    val identity = remember(model.revision) { model.sync.accountIdentity }
    val lastSync = remember(model.revision) { model.sync.lastSyncedAt }
    val lastError = remember(model.revision) { model.sync.lastSyncError }
    val automatic = remember(model.revision) { model.progress.prefs.getBoolean("autoSync", true) }
    val backupGist = remember(model.revision) { model.sync.savedBackupUrl }
    val socialGist = remember(model.revision) { model.sync.savedGistUrl }
    var details by remember { mutableStateOf(false) }
    SyncPanel(modifier, controlsOnly) {
        if (!controlsOnly || !connected) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if(connected) GitHubAvatar(identity,login ?: model.progress.name,Modifier.size(36.dp))
            else Icon(Icons.Rounded.CloudSync, null, tint = Teal, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("SYNCHRONISATION GITHUB")
                Text(if (connected) "Connecté · ${login ?: "GitHub"}" else "Retrouve ton voyage partout",
                    fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink)
            }
        }
        model.oauthStatus?.let {Text(it,color=Muted,fontSize=12.sp,lineHeight=17.sp)}
        if (connected) {
            if (lastError != null) {
                Surface(color = Color(0xFFFFE8E0), shape = RoundedCornerShape(12.dp)) {
                    Text(lastError, Modifier.padding(10.dp), color = Ink, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Synchronisation automatique", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Dernière synchro : ${syncDate(lastSync)}", color = Muted, fontSize = 12.sp)
                }
                Switch(automatic, { model.setAutoSync(it) }, modifier=Modifier.height(40.dp), enabled = !model.busy)
            }
            if (!controlsOnly) Action(if (model.busy) "Synchronisation…" else "Synchroniser maintenant", enabled = !model.busy) {
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
        TextButton({ details = !details }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(0.dp)) {
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
            if (connected && showGistLinks && (backupGist != null || socialGist != null)) {
                Text("Mes Gists sur GitHub", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (backupGist != null) TextButton({ openLink(context, backupGist) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Gist de sauvegarde", fontSize = 12.sp)
                    }
                    if (socialGist != null) TextButton({ openLink(context, socialGist) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Gist social", fontSize = 12.sp)
                    }
                }
            }
            if (connected) TextButton({ model.disconnectGitHub() }, enabled = !model.busy, contentPadding = PaddingValues(0.dp)) {
                Text("Déconnecter GitHub")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(model: AppModel) {
    val context = LocalContext.current
    val own = remember(model.revision) { model.progress.snapshot() }
    val invitation = remember(model.revision) { model.sync.savedGistUrl?.let { FriendInvite.link(it) } }
    val connected = remember(model.revision) { model.sync.tokens.hasToken() }
    val accountLogin = remember(model.revision) { model.sync.accountLogin }
    val lastSyncError = remember(model.revision) { model.sync.lastSyncError }
    var syncExpanded by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var receivedLink by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var scanOpen by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        cameraDenied=!granted
        if(granted)scanOpen=true
        else inviteError="Autorise la caméra pour scanner un QR, ou utilise le lien d’invitation."
    }
    val listState = rememberLazyListState()
    val pullState=rememberPullToRefreshState()
    val ranking = (listOf(own) + model.friends.map { it.progress }).sortedByDescending { it.weeklyXp }
    PullToRefreshBox(isRefreshing=model.socialRefreshing,onRefresh={model.refreshSocial(manual=true)},
        state=pullState,modifier=Modifier.fillMaxSize().testTag("friends-refresh"),indicator={
            PullToRefreshDefaults.Indicator(state=pullState,isRefreshing=model.socialRefreshing,
                modifier=Modifier.align(Alignment.TopCenter),containerColor=Mist,color=Teal)
        }) {
    LazyColumn(Modifier.fillMaxSize().testTag("friends-list"), state = listState, contentPadding = PaddingValues(start=16.dp,top=16.dp,end=16.dp,bottom=56.dp+LocalNavigationContentOverlap.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stickyHeader(key = "friends-header") {
            Column(Modifier.fillMaxWidth().testTag("friends-header").stickyHeaderShadow(listState).background(Cream).padding(vertical = 8.dp)) {
                BigTitle("Sur la même fréquence", "En équipe, on garde le signal.")
            }
        }
        item {
            if (!connected) GitHubConnection(model)
            else SyncPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GitHubAvatar(model.sync.accountIdentity, accountLogin ?: own.name, Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Connecté · ${accountLogin ?: "GitHub"}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton({ syncExpanded = !syncExpanded },Modifier.size(36.dp)) {
                        Icon(if (syncExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (syncExpanded) "Masquer les réglages de synchronisation" else "Afficher les réglages de synchronisation", tint = Teal)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (lastSyncError != null) "Synchronisation à vérifier" else "Sauvegarde et équipe GitHub",
                        color = if (lastSyncError != null) MaterialTheme.colorScheme.error else Muted,
                        fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton({ model.refreshSocial(manual = true) }, enabled = !model.busy,modifier=Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (model.busy) "Actualisation…" else "Actualiser l’équipe", fontSize = 12.sp)
                    }
                }
                if (syncExpanded) GitHubConnection(model, controlsOnly = true)
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pico(Modifier.size(44.dp), mood = MascotMood.HAPPY)
                    Column(Modifier.weight(1f)) {
                        Text("Invitations", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Retrouvez-vous par lien ou QR code.", color = Muted, fontSize = 12.sp)
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
            }
        }
        if(model.friendRequests.isNotEmpty() || model.outgoingRequests.isNotEmpty()) item { FriendRequestsPanel(model) }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Classement hebdomadaire", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Leaderboard, null, tint = Teal)
                }
                ranking.forEachIndexed { index, profile ->
                    val isOwn = profile === own
                    Row(Modifier.fillMaxWidth().background(if (isOwn) Mist else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${index + 1}", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (index == 0) Coral else Teal, modifier = Modifier.width(22.dp))
                        GitHubAvatar(if(isOwn) model.sync.accountIdentity else model.friends.firstOrNull { it.progress === profile }?.githubIdentity,
                            profile.name,Modifier.size(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name + if (isOwn) " · toi" else "", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${profile.streak} jours de série · ${profile.lessons} leçons", fontSize = 11.sp, color = Muted)
                        }
                        Text("${profile.weeklyXp} XP", fontWeight = FontWeight.ExtraBold, color = Teal, fontSize = 15.sp)
                    }
                }
                if (model.friends.isEmpty()) Text("Invite un équipier pour suivre vos progrès et vous encourager.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                OutlinedButton({ NativeShare.teamImage(context, own, model.friends.map { it.progress }) },
                    Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Partager le classement")
                }
            }
        }
        if (model.friends.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tes équipiers", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(model.friends.size.toString(), color = Teal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(model.friends, key = { it.gist.ifBlank { it.progress.name } }) { friend ->
            var menuOpen by remember { mutableStateOf(false) }
            var confirmRemoval by remember { mutableStateOf(false) }
            var confirmRequest by remember { mutableStateOf(false) }
            val socialGist = remember(friend.gist) { runCatching { GitHubSync.gistPageUrl(friend.gist) }.getOrNull() }
            val githubProfile = remember(friend.githubIdentity) { friend.githubIdentity?.let { runCatching { it.profileUrl }.getOrNull() } }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GitHubAvatar(friend.githubIdentity,friend.progress.name,Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.progress.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${friend.progress.xp} XP · ${friend.progress.lessons} leçons", color = Muted, fontSize = 12.sp)
                    }
                    Box {
                        IconButton({ menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "Options de ${friend.progress.name}") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if(model.sync.tokens.hasToken() && model.sync.savedGistUrl!=null) {
                                val sent=model.outgoingRequests.firstOrNull { it.request.recipientGistId==runCatching { GitHubSync.gistId(friend.gist) }.getOrNull() }
                                DropdownMenuItem(
                                    text={Text(when(sent?.status) {"sent"->"Demande déjà envoyée";"accepted"->"Ajout réciproque confirmé";else->"Demander l’ajout en retour"})},
                                    leadingIcon={Icon(Icons.Rounded.PersonAdd,null)},
                                    enabled=!model.busy && sent?.status !in setOf("sent","accepted"),
                                    onClick={menuOpen=false;confirmRequest=true})
                            }
                            if (githubProfile != null) DropdownMenuItem(
                                text = { Text("Ouvrir le profil GitHub") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                onClick = { menuOpen = false; openLink(context, githubProfile) }
                            )
                            if (socialGist != null) DropdownMenuItem(
                                text = { Text("Ouvrir le Gist social") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                onClick = { menuOpen = false; openLink(context, socialGist) }
                            )
                            DropdownMenuItem(
                                text = { Text("Retirer cet équipier",color=MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.PersonRemove, null,tint=MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmRemoval = true }
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
            if(confirmRemoval) AlertDialog(onDismissRequest={confirmRemoval=false},
                title={Text("Retirer ${friend.progress.name} ?")},
                text={Text("Sa progression n’apparaîtra plus dans ton équipe. Tu pourras l’ajouter à nouveau avec son lien d’invitation.")},
                confirmButton={TextButton({confirmRemoval=false;model.removeFriend(friend)},
                    colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text("Retirer")}},
                dismissButton={TextButton({confirmRemoval=false}){Text("Annuler")}})
            if(confirmRequest) AlertDialog(onDismissRequest={confirmRequest=false},
                title={Text("Demander l’ajout en retour ?")},
                text={Text("${friend.progress.name} recevra une demande à accepter dans Hamigo. Elle sera publiée sous ton compte GitHub dans les commentaires de son Gist social, lisibles avec son lien.")},
                confirmButton={TextButton({confirmRequest=false;model.sendFriendRequest(friend)},enabled=!model.busy) {Text("Envoyer")}},
                dismissButton={TextButton({confirmRequest=false}){Text("Annuler")}})
        }
        if (!connected) item {
            OutlinedButton({ model.refreshSocial(manual = true) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !model.busy) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (model.busy) "Actualisation…" else "Actualiser l’équipe")
            }
        }
    }
    }
    if (scanOpen) FriendQrScanner(onDismiss={scanOpen=false},onInvite={id ->
        scanOpen=false;addOpen=false;receivedLink="";inviteError=null;model.pendingInvite=id
    })
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
                    Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Partager le QR")
                }
            }
        }, confirmButton = { TextButton({ showQr = false }) { Text("Fermer") } })
    }
    if (addOpen && !scanOpen) {
        AlertDialog(onDismissRequest = { addOpen = false }, title = { Text("Ajouter un équipier") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button({
                    inviteError=null;cameraDenied=false
                    if(context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)scanOpen=true
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                },Modifier.fillMaxWidth(),enabled=context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                    Icon(Icons.Rounded.QrCodeScanner,null);Spacer(Modifier.width(8.dp));Text("Scanner un QR code")
                }
                Text("Ou colle le lien d’invitation reçu.", fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedTextField(receivedLink, { receivedLink = it.take(512); inviteError = null;cameraDenied=false },
                    label = { Text("Lien d’invitation Hamigo") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = inviteError != null && !cameraDenied, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                inviteError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if(cameraDenied)TextButton({
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
                },contentPadding=PaddingValues(0.dp)) {Text("Autoriser la caméra dans les réglages",fontSize=12.sp)}
            }
        }, confirmButton = {
            TextButton({
                cameraDenied=false
                val parsed = FriendInvite.parse(receivedLink.trim())
                if (parsed == null) inviteError = "Ce lien n’est pas une invitation Hamigo valide."
                else { model.pendingInvite = parsed; addOpen = false; receivedLink = "" }
            }, enabled = receivedLink.isNotBlank() && !model.busy) { Text("Continuer") }
        }, dismissButton = { TextButton({ addOpen = false }) { Text("Annuler") } })
    }
}

@Composable
private fun FriendRequestsPanel(model: AppModel) {
    var allIncoming by remember { mutableStateOf(false) }
    var outgoingOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(model.friendRequests.isNotEmpty()) Panel(color=Color(0xFFFFF4D6)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.NotificationsActive,null,tint=Teal,modifier=Modifier.size(22.dp))
                Text("Demandes reçues",fontSize=18.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.weight(1f))
                Badge { Text(model.friendRequests.size.toString()) }
            }
            val requests=if(allIncoming) model.friendRequests else model.friendRequests.take(5)
            requests.forEach { request -> key(request.decisionKey) {
                Surface(color = Color.White.copy(alpha = .6f), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            GitHubAvatar(request.author,request.profile.name,Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(request.profile.name,fontWeight=FontWeight.Bold,fontSize=15.sp)
                                Text("@${request.author.login}",fontSize=12.sp,color=Muted)
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button({model.acceptFriendRequest(request)},Modifier.weight(1f),enabled=!model.busy,
                                contentPadding=PaddingValues(horizontal=14.dp,vertical=6.dp)) { Text("Accepter") }
                            TextButton({model.ignoreFriendRequest(request)},Modifier.weight(1f),enabled=!model.busy) { Text("Ignorer") }
                        }
                    }
                }
            } }
            if(!allIncoming && model.friendRequests.size>5) TextButton({allIncoming=true}) { Text("Voir les autres demandes") }
        }
        if(model.outgoingRequests.isNotEmpty()) Panel {
            TextButton({outgoingOpen=!outgoingOpen},Modifier.fillMaxWidth(),contentPadding=PaddingValues(0.dp)) {
                Icon(Icons.Rounded.Send,null,modifier=Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Demandes envoyées · ${model.outgoingRequests.size}",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=16.sp)
                Icon(if(outgoingOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)
            }
            if(outgoingOpen) model.outgoingRequests.sortedByDescending { it.updatedAt }.take(10).forEach { outgoing ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(outgoing.recipientName,fontWeight=FontWeight.Bold,fontSize=14.sp)
                        Text(when(outgoing.status) {
                            "accepted" -> "Acceptée ✓"
                            "sent" -> "En attente de sa réponse"
                            "failed" -> "Envoi à réessayer"
                            else -> "Envoi non confirmé"
                        },fontSize=12.sp,color=Muted)
                    }
                    if(outgoing.status in setOf("pending","failed")) TextButton({model.retryFriendRequest(outgoing)},enabled=!model.busy) {Text("Réessayer")}
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(model: AppModel) {
    val context = LocalContext.current
    val animatedBack = LocalAnimatedBack.current
    val p = model.displayedProgress ?: model.progress
    var name by remember { mutableStateOf(p.name) }
    var hour by remember { mutableStateOf(p.prefs.getInt("reminderHour", 20).toString()) }
    var minute by remember { mutableStateOf(p.prefs.getInt("reminderMinute", 0).toString().padStart(2, '0')) }
    var reminderEnabled by remember { mutableStateOf(p.prefs.getBoolean("reminderEnabled", false)) }
    var permissionForTest by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
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
        item { PageHeader("Paramètres", "Tes préférences et ton compte.") { if(animatedBack!=null)animatedBack() else model.route = "profile" } }
        item { SettingsCategory("Profil & objectif", Icons.Rounded.Person) }
        item {
            Panel {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    GitHubAvatar(model.sync.accountIdentity,p.name,Modifier.size(44.dp))
                    Text("Ton profil", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
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
        item { SettingsCategory("Compte & synchronisation", Icons.Rounded.CloudSync) }
        item { GitHubConnection(model, showGistLinks = true) }
        item { SettingsCategory("Gameplay", Icons.Rounded.SportsEsports) }
        item { GameplaySettings(model, p) }
        item { SettingsCategory("Rappels", Icons.Rounded.Notifications) }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Rappel quotidien", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
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
        item { SettingsCategory("Sauvegarde & application", Icons.Rounded.Settings) }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sauvegarde manuelle", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Importer ou exporter une copie locale.", fontSize = 12.sp, color = Muted)
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
                    TextButton({ openLink(context, "https://github.com/AlexMalfr/Hamigo") },Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)) {Box(Modifier.fillMaxWidth()){Text("Code source de Hamigo · GitHub",fontSize=12.sp)}}
                }
                Text("Entraînement indépendant de l’ANFR. Certaines formulations communautaires peuvent être anciennes ; leur source est consultable pendant les révisions.", fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            }
        }
        item {
            OutlinedButton(
                { openLink(context, "https://alexandre-malfreyt.notion.site/3f1dbe8ec53680e18e5bd7682e0661c0") },
                Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.MailOutline, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Contacter / faire un retour", fontSize = 14.sp)
            }
        }
    }
}

@Composable private fun SettingsCategory(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = Teal)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Teal)
    }
}

@Composable private fun GameplaySettings(model: AppModel, progress: Progress) {
    val settings = remember(model.revision) { GameplayPreferences.read(progress.prefs) }
    var threshold by remember(settings.thresholdMs) { mutableFloatStateOf(settings.thresholdMs.toFloat()) }
    fun save(value: MorseInputSettings) {
        GameplayPreferences.save(progress.prefs, value)
        model.refresh(); model.refreshSocial()
    }
    Panel {
        Text("Saisie du Morse", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Dans les questions et le traducteur.", fontSize = 12.sp, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!settings.singleKey, { save(settings.copy(singleKey = false)) },
                label = { Text("Deux boutons") }, modifier = Modifier.weight(1f))
            FilterChip(settings.singleKey, { save(settings.copy(singleKey = true)) },
                label = { Text("Un bouton") }, modifier = Modifier.weight(1f))
        }
        if (settings.singleKey) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Durée minimale d’un trait", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${threshold.toInt()} ms", fontWeight = FontWeight.Bold, color = Teal, fontSize = 13.sp)
            }
            Slider(threshold, { threshold = it }, valueRange = 150f..600f, steps = 8,
                onValueChangeFinished = { save(settings.copy(thresholdMs = (kotlin.math.round(threshold / 50f) * 50).toInt())) },
                modifier = Modifier.testTag("morse-timing"))
            Text("Plus court : un point. L’essai reconnaît aussi les pauses entre lettres et mots.", fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
        }
        Surface(color = Mist, shape = RoundedCornerShape(14.dp)) {
            MorseSettingsPreview(settings)
        }
    }
}
