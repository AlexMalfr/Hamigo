package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malfreyt.alexandre.hamigo.platform.copyGitHubCode

/** Available on returning from the browser, without a separate introduction screen. */
@Composable fun GitHubDeviceCode(model: AppModel) {
    val session = model.oauthSession ?: return
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(session.userCode, Modifier.weight(1f), fontSize = 22.sp,
            color = Teal, fontWeight = FontWeight.ExtraBold)
        IconButton({ copyGitHubCode(context, session.userCode) }) {
            Icon(Icons.Rounded.ContentCopy, "Copier le code GitHub")
        }
        IconButton({ model.openGitHubBrowser() }) {
            Icon(Icons.Rounded.OpenInBrowser, "Rouvrir l’onglet GitHub")
        }
    }
    Text("Dans la première case GitHub : appui long → Coller. Le presse-papiers du clavier peut ne pas remplir les autres cases.",
        color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    TextButton({ model.cancelTask() }, contentPadding = PaddingValues(0.dp)) {
        Text("Annuler la connexion")
    }
}
