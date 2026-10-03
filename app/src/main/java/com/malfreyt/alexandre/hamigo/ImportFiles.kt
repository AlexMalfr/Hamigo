package com.malfreyt.alexandre.hamigo

import android.content.ContentResolver
import android.net.Uri

fun readImport(resolver:ContentResolver,uri:Uri):String? = resolver.openInputStream(uri)?.bufferedReader()?.use {reader->
    val result=StringBuilder();val buffer=CharArray(4096)
    while(true) {
        val n=reader.read(buffer)
        if(n<0)break
        require(result.length+n<=2_000_000){"Fichier trop volumineux."}
        result.append(buffer,0,n)
    }
    result.toString()
}
class ShareFileProvider : androidx.core.content.FileProvider()
