package com.k410sh4.a25lab.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class GenesisStudyWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {
    override fun doWork(): Result = runCatching {
        val engine = GenesisEngine(applicationContext)
        val state = engine.loadState()
        if (state.chunks.isNotEmpty()) {
            engine.study()
        }
        Result.success()
    }.getOrElse {
        Result.retry()
    }
}

object GenesisScheduler {
    private const val UNIQUE_WORK = "genesis-safe-study"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequest.Builder(
            GenesisStudyWorker::class.java,
            6,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
