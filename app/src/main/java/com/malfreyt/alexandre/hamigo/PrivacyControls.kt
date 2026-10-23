package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.*

@Composable internal fun SocialDataDisclosure(modifier:Modifier=Modifier) {
    val context=LocalContext.current
    var expanded by rememberSaveable {mutableStateOf(false)}
    Column(modifier.fillMaxWidth()) {
        TextButton(feedbackClick {expanded=!expanded},Modifier.align(Alignment.CenterHorizontally).testTag("social-data-disclosure"),contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) {
            Text("Données et confidentialité",fontSize=12.sp)
            Icon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null,Modifier.size(18.dp))
        }
        if(expanded) {
            Text("GitHub reçoit ta sauvegarde (progression, réponses, XP et préférences) et un résumé social (pseudo et statistiques). Les échanges sont automatiques, y compris en arrière-plan. Les deux Gists sont non répertoriés, non chiffrés et lisibles avec leur lien. L’invitation partage seulement le résumé social.",color=Muted,fontSize=12.sp,lineHeight=18.sp)
            Text("GitHub autorise les Gists et le profil pour lire les comptes bloqués. Hamigo utilise ses propres Gists et ne modifie pas ton profil. Les blocages GitHub retirent les équipiers concernés et filtrent leurs demandes à la synchronisation. Tu peux arrêter la synchro, te déconnecter ou supprimer les Gists Hamigo dans les paramètres. Ton apprentissage fonctionne aussi sans compte.",color=Muted,fontSize=12.sp,lineHeight=18.sp)
            ActionLinks {
                ActionLink("Politique de confidentialité",icon=Icons.Rounded.PrivacyTip){openLink(context,HamigoLinks.PRIVACY)}
                ActionLink("Règles de l’équipe",icon=Icons.Rounded.Groups){openLink(context,HamigoLinks.COMMUNITY_RULES)}
            }
        }
    }
}

@Composable internal fun SocialRulesDialog(model:AppModel) {
    val context=LocalContext.current
    if(model.socialConsentPending)HamigoSheet(title="Avant de rejoindre l’équipe",onDismiss=model::dismissSocialRules,
        icon={Icon(Icons.Rounded.Groups,null,Modifier.size(36.dp),tint=Teal)},
        actions={
            TextButton(feedbackClick(model::dismissSocialRules)){Text("Pas maintenant")}
            Button(feedbackClick(model::acceptSocialRules),Modifier.testTag("accept-social-rules")){Text("Accepter et continuer")}
        }) {
            Text("Utilise un pseudo et un avatar respectueux. Les contenus illégaux, haineux, sexuels, le harcèlement, les menaces, l’usurpation et le spam sont interdits. Les menus de l’équipe permettent d’ouvrir les outils de blocage et de signalement GitHub.")
            ActionLinks {
                ActionLink("Lire les règles de l’équipe",icon=Icons.Rounded.Groups){openLink(context,HamigoLinks.COMMUNITY_RULES)}
                ActionLink("Politique de confidentialité",icon=Icons.Rounded.PrivacyTip){openLink(context,HamigoLinks.PRIVACY)}
            }
        }
    if(model.socialDataConsentPending)HamigoSheet(title="Tes données sur GitHub",onDismiss=model::dismissSocialRules,
        icon={Icon(Icons.Rounded.PrivacyTip,null,Modifier.size(36.dp),tint=Teal)},
        actions={
            TextButton(feedbackClick(model::dismissSocialRules)){Text("Sans connexion")}
            Button(feedbackClick(model::acceptSocialData),Modifier.testTag("accept-social-data")){Text("Autoriser et continuer")}
        }) {
            Text("La connexion GitHub est facultative. Elle demande deux permissions :")
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                GitHubPermissionItem(Icons.Rounded.CloudSync,"Gists (gist)",
                    "Sauvegarder ta progression, tes réponses, tes résultats, tes XP et tes préférences dans un Gist, et publier ton pseudo et tes statistiques dans un résumé social séparé. Les demandes d’amis utilisent les commentaires de ces Gists.")
                GitHubPermissionItem(Icons.Rounded.Block,"Profil (user)",
                    "Lire les comptes que tu as bloqués sur GitHub pour retirer les équipiers concernés et filtrer leurs demandes. Cette liste reste en cache sur l’appareil, sans être ajoutée aux Gists.")
            }
            Text("GitHub donne accès à tous tes Gists et inclut la modification du profil dans ces permissions. Hamigo ne gère que ses propres Gists et ne modifie ni ton profil ni tes blocages.")
            Text("Ces lectures et écritures sont automatiques pendant l’utilisation et en arrière-plan, même quand l’app est fermée, pour sauvegarder tes révisions et actualiser ton équipe. Tu peux désactiver la synchro dans les paramètres.")
            Text("Les deux Gists sont non répertoriés, non chiffrés et lisibles par toute personne qui connaît leur lien. L’éditeur de Hamigo ne reçoit pas ton jeton sur un serveur à lui.")
            ActionLink("Politique de confidentialité",icon=Icons.Rounded.PrivacyTip){openLink(context,HamigoLinks.PRIVACY)}
            Text("La connexion est facultative. Tu peux apprendre sans compte et supprimer les Gists Hamigo depuis Mes données.",fontSize=13.sp,color=Muted)
        }
}

@Composable private fun GitHubPermissionItem(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,description:String) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
        Icon(icon,null,Modifier.padding(top=2.dp).size(22.dp),tint=Teal)
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,color=Teal,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,fontSize=16.sp)
            Text(description,fontSize=14.sp,lineHeight=20.sp)
        }
    }
}

@Composable internal fun PlayStoreButtons() {
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        ActionLink("Hamigo sur Google Play",Modifier.weight(1f).testTag("open-play-store"),icon=Icons.Rounded.Shop){HamigoLinks.openPlayStore(context)}
        VerticalDivider(Modifier.height(26.dp))
        IconButton(feedbackClick {HamigoLinks.openPlayStore(context,review=true)},Modifier.width(56.dp).heightIn(min=48.dp).testTag("review-play-store")) {
            Icon(Icons.Rounded.Star,"Donner un avis sur Google Play",tint=Teal)
        }
    }
}

@Composable internal fun RemoteDataDeletionAction(model:AppModel) {
    val context=LocalContext.current
    val window=LocalWindowInfo.current.containerSize
    val wideConfirmation=window.width>window.height&&with(LocalDensity.current){window.height.toDp()}<400.dp
    var confirmation by rememberSaveable {mutableStateOf(false)}
    val connected=remember(model.revision){model.sync.tokens.hasToken()}
    val pending=remember(model.revision){SocialRules.deletionPending(model.storageContext)}
    if(pending)Text("La suppression n’est pas terminée. Les publications GitHub restent suspendues jusqu’à sa réussite.",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.error)
    ActionLink(if(pending)"Réessayer la suppression des Gists" else "Supprimer mes données sur GitHub",icon=Icons.Rounded.DeleteOutline,
        enabled=connected&&!model.busy,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("delete-remote-data")){confirmation=true}
    if(!connected)Text("Connecte GitHub pour supprimer depuis l’app, ou utilise la procédure en ligne.",fontSize=12.sp,lineHeight=18.sp,color=Muted)
    ActionLink("Supprimer depuis le site GitHub",icon=Icons.Rounded.OpenInNew){openLink(context,HamigoLinks.DATA_DELETION)}
    if(confirmation)AlertDialog(onDismissRequest={confirmation=false},
        modifier=if(wideConfirmation)Modifier.widthIn(max=560.dp).fillMaxWidth().padding(horizontal=24.dp) else Modifier,
        properties=DialogProperties(usePlatformDefaultWidth=!wideConfirmation),
        icon={Icon(Icons.Rounded.WarningAmber,null,Modifier.size(if(wideConfirmation)24.dp else 36.dp),tint=MaterialTheme.colorScheme.error)},
        title={Text("Supprimer les Gists Hamigo ?",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,
            style=if(wideConfirmation)LocalTextStyle.current.copy(fontSize=20.sp,lineHeight=24.sp) else LocalTextStyle.current)},
        text={Text("Suppression définitive des Gists, de leur historique et de leurs commentaires. Ton apprentissage reste sur ce téléphone ; Hamigo sera déconnecté.",Modifier.verticalScroll(rememberScrollState()),
            style=if(wideConfirmation)MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp,lineHeight=18.sp) else LocalTextStyle.current)},
        confirmButton={DelayedDestructiveButton("Supprimer",Modifier.testTag("confirm-delete-remote-data")){confirmation=false;model.deleteGitHubData()}},
        dismissButton={TextButton(feedbackClick {confirmation=false}){Text("Annuler")}})
}

@Composable internal fun PersonalDataAction(model:AppModel) {
    var open by rememberSaveable {mutableStateOf(false)}
    ActionLink("Mes données",icon=Icons.Rounded.ManageAccounts,modifier=Modifier.testTag("personal-data")){open=true}
    if(open)HamigoSheet(title="Mes données",onDismiss={open=false},actions={TextButton(feedbackClick {open=false}){Text("Fermer")}}) {
            Text("Ta progression locale reste indépendante de tes sauvegardes GitHub.",fontSize=13.sp,color=Muted)
            Text("Supprimer les Gists Hamigo efface aussi leurs anciennes versions et leurs commentaires. Ton apprentissage et tes réglages sur ce téléphone sont conservés, ainsi que ton compte GitHub ; Hamigo sera déconnecté.",fontSize=13.sp,color=Muted)
            Text("Déconnecte aussi tes autres appareils pour éviter qu’ils recréent les Gists. Les commentaires déjà publiés chez d’autres personnes peuvent être supprimés séparément sur GitHub.",fontSize=13.sp,color=Muted)
            RemoteDataDeletionAction(model)
        }
}

internal fun githubReportUrl(identity:GitHubIdentity)="https://github.com/contact/report-abuse?report="+android.net.Uri.encode("${identity.login} (user)")
internal fun githubBlockUrl(identity:GitHubIdentity)="https://github.com/settings/blocked_users?block_user="+android.net.Uri.encode(identity.login)

@Composable internal fun IncomingAccountSafety(request:FriendRequest) {
    val context=LocalContext.current
    var menu by remember {mutableStateOf(false)}
    var report by rememberSaveable {mutableStateOf(false)}
    Box {
        IconButton(feedbackClick {menu=true}){Icon(Icons.Rounded.MoreVert,"Actions pour ${request.profile.name}")}
        DropdownMenu(menu,{menu=false}) {
            DropdownMenuItem(text={Text("Bloquer sur GitHub")},leadingIcon={Icon(Icons.Rounded.Block,null)},onClick=feedbackClick {menu=false;openLink(context,githubBlockUrl(request.author))})
            DropdownMenuItem(text={Text("Signaler à GitHub")},leadingIcon={Icon(Icons.Rounded.Flag,null)},onClick=feedbackClick {menu=false;report=true})
        }
    }
    if(report)GitHubReportSheet(request.author,GitHubSync.gistPageUrl(request.senderGistId),context){report=false}
}

@Composable internal fun GitHubReportSheet(identity:GitHubIdentity,gist:String,context:android.content.Context,onDismiss:()->Unit) {
    val template=remember(identity,gist){"""
        Account concerned: @${identity.login}
        GitHub profile: ${identity.profileUrl}
        Social Gist used in Hamigo: $gist

        What happened: [describe the behaviour or content]
        When: [date and time]
        Evidence: [URLs of the relevant comments or content]
    """.trimIndent()}
    var copied by remember {mutableStateOf(false)}
    HamigoSheet(title="Signaler à GitHub",onDismiss=onDismiss,
        icon={Icon(Icons.Rounded.WarningAmber,null,Modifier.size(36.dp),tint=Color(0xFFC16A18))},actions={
        TextButton(feedbackClick(onDismiss)){Text("Fermer")}
        Button(feedbackClick {onDismiss();openLink(context,githubReportUrl(identity))}){
            Icon(Icons.Rounded.OpenInNew,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Ouvrir GitHub")
        }
    }) {
        Text("Le signalement est envoyé directement à GitHub depuis son formulaire. Tu peux copier ce modèle, compléter les passages entre crochets et le coller après connexion. Ne joins pas ta sauvegarde complète.",fontSize=13.sp,color=Muted)
        androidx.compose.foundation.text.selection.SelectionContainer {Text(template,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.testTag("github-report-template"))}
        ActionLink(if(copied)"Modèle copié" else "Copier le modèle",icon=Icons.Rounded.ContentCopy) {
            if(context.applicationContext !is DiagnosticContext&&!DiagnosticAccess.networkPaused) {
                val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Signalement GitHub",template));copied=true
            }
        }
    }
}
