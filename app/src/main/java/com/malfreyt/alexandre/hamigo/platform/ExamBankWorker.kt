package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import androidx.work.*
import com.malfreyt.alexandre.hamigo.DiagnosticAccess
import com.malfreyt.alexandre.hamigo.DiagnosticContext
import com.malfreyt.alexandre.hamigo.ExamBankRepository
import java.util.concurrent.TimeUnit

class ExamBankWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result {
        if(DiagnosticAccess.syncPaused(applicationContext))return Result.success()
        return if(ExamBankRepository.forContext(applicationContext).refresh())Result.success()
            else if(runAttemptCount<3)Result.retry() else Result.failure()
    }
}

internal object ExamBankScheduler {
    private const val PENDING="hamigo-exam1-download"
    private const val DAILY="hamigo-exam1-daily"
    fun schedule(context:Context) {
        if(context.applicationContext is DiagnosticContext||DiagnosticAccess.syncPaused(context))return
        val manager=WorkManager.getInstance(context)
        val connected=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        manager.enqueueUniqueWork(PENDING,ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<ExamBankWorker>()
            .setConstraints(connected).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
        manager.enqueueUniquePeriodicWork(DAILY,ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ExamBankWorker>(1,TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setInitialDelay(1,TimeUnit.DAYS).build())
    }
}
