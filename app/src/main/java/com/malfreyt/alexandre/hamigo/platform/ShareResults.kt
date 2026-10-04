package com.malfreyt.alexandre.hamigo.platform

/** A share template receives an immutable snapshot; its drawing stays separate from session state. */
data class ShareResults(
    val name:String,
    val title:String,
    val correct:Int,
    val total:Int,
    val elapsedMillis:Long,
    val unanswered:Int=0,
    val gainedXp:Int=0,
    val regulationScore:Int?=null,
    val techniqueScore:Int?=null
) {
    val exam get()=regulationScore!=null && techniqueScore!=null
    val successful get()=if(exam) regulationScore!!>=10 && techniqueScore!!>=10 else total>0 && correct*5>=total*4
}
