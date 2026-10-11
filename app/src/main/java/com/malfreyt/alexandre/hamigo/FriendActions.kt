package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.malfreyt.alexandre.hamigo.platform.GitHubSync

@Stable internal class FriendActionState {
    var removal by mutableStateOf(false)
    var request by mutableStateOf(false)
}
internal enum class FriendActionGroup { ALL, GITHUB, TEAM }

/** Same availability, labels, URLs and confirmations in the menu and full-screen profile. */
@Composable internal fun FriendActionItems(model:AppModel,friend:Friend,state:FriendActionState,asMenu:Boolean=false,group:FriendActionGroup=FriendActionGroup.ALL,onAction:()->Unit={}) {
    val context=LocalContext.current
    val gist=remember(friend.gist){runCatching {GitHubSync.gistPageUrl(friend.gist)}.getOrNull()}
    val rows:@Composable ()->Unit={
    if(group!=FriendActionGroup.GITHUB&&model.sync.tokens.hasToken()&&model.sync.savedGistUrl!=null) {
        val sent=model.outgoingRequests.firstOrNull {it.request.recipientGistId==runCatching {GitHubSync.gistId(friend.gist)}.getOrNull()}
        FriendActionRow(when(sent?.status){"sent"->"Demande déjà envoyée";"accepted"->"Ajout réciproque confirmé";else->"Demander l’ajout en retour"},Icons.Rounded.PersonAdd,asMenu,
            enabled=!model.busy&&sent?.status !in setOf("sent","accepted")){onAction();state.request=true}
    }
    if(group!=FriendActionGroup.TEAM) {
        friend.githubIdentity?.let {identity->FriendActionRow("Ouvrir le profil GitHub",Icons.AutoMirrored.Rounded.OpenInNew,asMenu){onAction();openLink(context,identity.profileUrl)}}
        gist?.let {url->FriendActionRow("Ouvrir le Gist social",Icons.AutoMirrored.Rounded.OpenInNew,asMenu){onAction();openLink(context,url)}}
    }
    if(group!=FriendActionGroup.GITHUB)FriendActionRow("Retirer cet équipier",Icons.Rounded.PersonRemove,asMenu,color=MaterialTheme.colorScheme.error){onAction();state.removal=true}
    }
    if(asMenu)rows() else ActionLinks {rows()}
}

@Composable private fun FriendActionRow(label:String,icon:ImageVector,asMenu:Boolean,enabled:Boolean=true,color:Color=Teal,click:()->Unit) {
    if(asMenu)DropdownMenuItem(text={Text(label,color=if(enabled)color else Muted.copy(alpha=.5f))},leadingIcon={Icon(icon,null,tint=if(enabled)color else Muted.copy(alpha=.5f))},enabled=enabled,onClick=click)
    else ActionLink(label,icon=icon,color=color,enabled=enabled,onClick=click)
}

@Composable internal fun FriendActionDialogs(model:AppModel,friend:Friend,state:FriendActionState,onRemoved:()->Unit={}) {
    if(state.removal)AlertDialog(onDismissRequest={state.removal=false},title={Text("Retirer ${friend.progress.name} ?")},
        text={Text("Sa progression n’apparaîtra plus dans ton équipe. Tu pourras l’ajouter à nouveau avec son lien d’invitation.")},
        confirmButton={TextButton(feedbackClick {state.removal=false;model.removeFriend(friend);onRemoved()},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Retirer")}},
        dismissButton={TextButton(feedbackClick {state.removal=false}){Text("Annuler")}})
    if(state.request)AlertDialog(onDismissRequest={state.request=false},title={Text("Demander l’ajout en retour ?")},
        text={Text("${friend.progress.name} recevra une demande à accepter dans Hamigo. Elle sera publiée sous ton compte GitHub dans les commentaires de son Gist social, lisibles avec son lien.")},
        confirmButton={TextButton(feedbackClick {state.request=false;model.sendFriendRequest(friend)},enabled=!model.busy){Text("Envoyer")}},
        dismissButton={TextButton(feedbackClick {state.request=false}){Text("Annuler")}})
}
