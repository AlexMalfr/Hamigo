package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun LicenseNoticesAction() {
    val context=LocalContext.current
    var open by rememberSaveable {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    val documents=linkedMapOf("Périmètre des licences et attribution du cours" to "PERIMETERS.md",
        "Code · GNU GPL version 3 ou ultérieure" to "LICENSE",
        "Contenus adaptés · CC BY-NC-SA 4.0" to "CC-BY-NC-SA-4.0.txt",
        "Bibliothèques · Apache License 2.0" to "Apache-2.0.txt",
        "Markdown · BSD 2-Clause" to "BSD-2-Clause-commonmark.txt",
        "Attributions et ressources tierces" to "THIRD_PARTY_NOTICES.md")
    val text=remember(selected){selected?.let {context.assets.open("licenses/$it").bufferedReader().use {it.readText()}}}
    ActionLink("Licences et attributions",modifier=Modifier.testTag("license-notices"),icon=Icons.Rounded.Description){open=true}
    if(open)HamigoSheet(title=if(selected==null)"Licences et attributions" else documents.entries.first {it.value==selected}.key,
        onDismiss={open=false;selected=null},contentKey=selected,titleIcon=Icons.Rounded.Description,actions={
            if(selected!=null)TextButton({selected=null}){Text("Retour")}
            Button(feedbackClick {open=false;selected=null}){Text("Fermer")}
        }) {
            if(text==null) {
                Text("Code libre sous GPL-3.0-or-later. Les contenus adaptés du cours de Jean-Luc Fortin F6GPX et du radio-club F6KGL/F5KFF sont sous CC BY-NC-SA 4.0 : attribution, usage non commercial et partage sous la même licence. Exam’1 et les autres ressources conservent leurs droits propres.",fontSize=13.sp,color=Muted)
                ActionLinks {
                    documents.forEach {(title,file)->
                        val icon=when(file) {
                            "LICENSE"->Icons.Rounded.Code
                            "CC-BY-NC-SA-4.0.txt"->Icons.Rounded.MenuBook
                            "Apache-2.0.txt"->Icons.Rounded.Extension
                            "THIRD_PARTY_NOTICES.md"->Icons.Rounded.Copyright
                            else->Icons.Rounded.Balance
                        }
                        ActionLink(title,icon=icon){selected=file}
                    }
                    ActionLink("Sources de la version ${BuildConfig.VERSION_NAME}",icon=Icons.Rounded.Code){openLink(context,"${HamigoLinks.REPOSITORY}/tree/${BuildConfig.SOURCE_REVISION}")}
                }
            } else SelectionContainer {
                if(selected?.endsWith(".md")==true)LicenseMarkdown(text,Modifier.testTag("license-markdown")){href->
                    val target=licenseLinkTarget(selected!!,href)
                    if(target in documents.values)selected=target else openLink(context,target)
                }
                else Text(text,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.testTag("license-text"))
            }
        }
}
