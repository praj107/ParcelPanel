package com.parcelpanel.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class RefreshScheduler(
    private val context: Context,
) {
    fun ensureScheduled(syncIntervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            syncIntervalHours.toLong(),
            TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "parcelpanel-periodic-refresh"
    }
}

