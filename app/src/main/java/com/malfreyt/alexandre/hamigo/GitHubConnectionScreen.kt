package com.malfreyt.alexandre.hamigo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

/** Device OAuth stays inside Hamigo and disappears as soon as polling obtains the token. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun GitHubConnectionScreen(model:AppModel) {
    val device=model.oauthSession ?: return
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var browserOpen by remember(device) {mutableStateOf(false)}
    var loading by remember(device) {mutableStateOf(true)}
    var canFill by remember(device) {mutableStateOf(false)}
    val web=remember(device) {WebView(context)}
    fun fillCode() {
        if(!canFill)return
        web.evaluateJavascript(deviceCodeScript(device.userCode)) { result ->
            if(result=="0") model.message="Tu peux aussi utiliser Copier le code pour renseigner la page GitHub."
        }
    }
    DisposableEffect(web) {
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.setSupportMultipleWindows(false)
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return false
                val uri=request.url
                if(uri.scheme=="https" && uri.host=="github.com")return false
                openLink(context,uri.toString());return true
            }
            override fun onPageStarted(view:WebView,url:String?,favicon:Bitmap?) {loading=true;canFill=false}
            override fun onPageFinished(view:WebView,url:String?) {
                loading=false
                val uri=Uri.parse(url.orEmpty())
                canFill=uri.scheme=="https" && uri.host=="github.com" && uri.path?.trimEnd('/')=="/login/device"
                // Only device-code inputs on this exact GitHub page are changed. No form is submitted.
                if(canFill)view.evaluateJavascript(deviceCodeScript(device.userCode),null)
            }
        }
        onDispose {web.stopLoading();web.webViewClient=WebViewClient();web.destroy()}
    }
    Dialog(onDismissRequest={model.cancelTask()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Cream) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton({model.cancelTask()}) {Icon(Icons.Rounded.Close,"Annuler la connexion")}
                    Column(Modifier.weight(1f)) {Text("Connexion GitHub",fontWeight=FontWeight.Bold);Text("github.com · connexion HTTPS",fontSize=11.sp,color=Muted)}
                    IconButton({openLink(context,device.verificationUri)}) {Icon(Icons.Rounded.OpenInBrowser,"Ouvrir GitHub dans le navigateur")}
                }
                Row(Modifier.fillMaxWidth().background(Mist).padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(device.userCode,fontSize=21.sp,fontWeight=FontWeight.ExtraBold,color=Teal,modifier=Modifier.weight(1f))
                    IconButton({clipboard.setText(AnnotatedString(device.userCode));model.message="Code copié."}) {Icon(Icons.Rounded.ContentCopy,"Copier le code GitHub")}
                    if(browserOpen)TextButton({fillCode()},enabled=canFill) {Text("Renseigner")}
                }
                model.oauthStatus?.let {Text(it,Modifier.padding(horizontal=16.dp,vertical=6.dp),fontSize=12.sp,color=Muted)}
                if(browserOpen) {
                    if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                    AndroidView(factory={web},modifier=Modifier.fillMaxWidth().weight(1f))
                } else {
                    Column(Modifier.weight(1f).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Pico(Modifier.size(100.dp),mood=MascotMood.DETERMINED)
                        Text("Autorise Hamigo à utiliser les Gists de ton compte. Le code sera renseigné dans la page quand GitHub le permet.",fontSize=15.sp)
                        Text("Cette fenêtre se ferme automatiquement après l’autorisation. Tes révisions restent sur cet appareil même sans connexion.",fontSize=13.sp,color=Muted)
                        Action("Ouvrir GitHub ici") {browserOpen=true;web.loadUrl(device.verificationUri)}
                    }
                }
            }
        }
        BackHandler {if(browserOpen && web.canGoBack())web.goBack() else model.cancelTask()}
    }
}

/** No bridge, password access or authorization click. Merely sets the displayed one-time code. */
internal fun deviceCodeScript(code:String):String {
    val safe=JSONObject.quote(code.replace("-",""))
    return """(function(){
      if(location.origin!=='https://github.com'||location.pathname.replace(/\/$/,'')!=='/login/device')return 0;
      var code=$safe;
      var cells=Array.from(document.querySelectorAll('input[maxlength="1"]')).filter(x=>x.type!=='hidden');
      var set=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
      function put(x,v){set.call(x,v);x.dispatchEvent(new Event('input',{bubbles:true}));x.dispatchEvent(new Event('change',{bubbles:true}));}
      if(cells.length===code.length){cells.forEach((x,i)=>put(x,code[i]));return cells.length;}
      var field=document.querySelector('input[name="user_code"],input[name="user-code"],input#user-code');
      if(field){put(field,code);return 1;}return 0;
    })()""".trimIndent()
}
