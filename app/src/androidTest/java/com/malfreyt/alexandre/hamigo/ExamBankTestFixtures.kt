package com.malfreyt.alexandre.hamigo

import android.content.Context

/** The full official fixture is downloaded locally by tools/prepare_exam1_tests.ps1, never packaged. */
internal object ExamBankTestFixtures {
    fun open(context:Context,id:String)=ExamBankStore.forContext(context).openImage(
        checkNotNull(ExamBankStore.forContext(context).snapshot()) {"Run tools/prepare_exam1_tests.ps1 on the emulator first."}
            .questions.first {it.id==id}.image!!)
}
