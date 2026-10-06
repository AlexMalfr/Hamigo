package com.malfreyt.alexandre.hamigo

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.malfreyt.alexandre.hamigo.platform.FriendInvite

/** The camera only decodes locally; an invitation still needs the ordinary confirmation. */
@Composable internal fun FriendQrScanner(onDismiss: () -> Unit,onInvite: (String) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var cameraError by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val hasFlash=remember {context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)}
    var accepted by remember {mutableStateOf(false)}
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().testTag("friend-qr-scanner"),color=Cream) {
            Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Scanner une invitation",Modifier.weight(1f),fontSize=23.sp,fontWeight=FontWeight.ExtraBold,color=Ink)
                    IconButton(onDismiss) {Icon(Icons.Rounded.Close,"Fermer le lecteur QR")}
                }
                Text("Place le QR de ton ami dans le cadre.",color=Muted,fontSize=14.sp)
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black),contentAlignment=Alignment.Center) {
                    QrCameraPreview(torch,onCode={code ->
                        if(!accepted) {
                            val id=FriendInvite.parse(code.trim())
                            if(id==null)error="Ce QR n’est pas une invitation Hamigo. Essaie celui de ton ami."
                            else {accepted=true;onInvite(id)}
                        }
                    },onError={cameraError=true;error="La caméra est indisponible. Tu peux ajouter ton ami avec son lien."})
                    if(!cameraError)Box(Modifier.sizeIn(maxWidth=240.dp,maxHeight=240.dp).fillMaxSize(.7f)
                        .aspectRatio(1f).border(3.dp,Color.White,RoundedCornerShape(20.dp)))
                }
                error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    if(hasFlash)OutlinedButton({torch=!torch},enabled=!cameraError) {
                        Icon(if(torch)Icons.Rounded.FlashlightOff else Icons.Rounded.FlashlightOn,null)
                        Spacer(Modifier.width(6.dp));Text(if(torch)"Éteindre la lampe" else "Lampe")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onDismiss) {Text("Revenir au lien")}
                }
            }
        }
    }
}

@Composable private fun QrCameraPreview(torch: Boolean,onCode: (String) -> Unit,onError: () -> Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val currentCode by rememberUpdatedState(onCode)
    val currentError by rememberUpdatedState(onError)
    val view=remember(context) {
        BarcodeView(context).apply {
            decoderFactory=DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeContinuous(object:BarcodeCallback {
                override fun barcodeResult(result:BarcodeResult) {currentCode(result.text)}
            })
            addStateListener(object:CameraPreview.StateListener {
                override fun previewSized()=Unit
                override fun previewStarted()=Unit
                override fun previewStopped()=Unit
                override fun cameraClosed()=Unit
                override fun cameraError(error:Exception) {currentError()}
            })
        }
    }
    AndroidView(factory={view},modifier=Modifier.fillMaxSize(),update={it.setTorch(torch)})
    DisposableEffect(view,lifecycle) {
        val observer=LifecycleEventObserver {_,event ->
            when(event) {
                Lifecycle.Event.ON_RESUME -> view.resume()
                Lifecycle.Event.ON_PAUSE -> view.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.resume()
        onDispose {lifecycle.removeObserver(observer);view.stopDecoding();view.pause()}
    }
}
