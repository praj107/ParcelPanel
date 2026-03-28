package com.parcelpanel

import android.app.Application
import com.parcelpanel.data.AppDatabase
import com.parcelpanel.data.ParcelRepository
import com.parcelpanel.data.SettingsRepository
import com.parcelpanel.sync.RefreshScheduler
import com.parcelpanel.tracking.ConnectorRegistry
import com.parcelpanel.update.OtaUpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ParcelPanelApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val connectorRegistry: ConnectorRegistry by lazy { ConnectorRegistry() }
    val otaUpdateRepository: OtaUpdateRepository by lazy {
        OtaUpdateRepository(
            context = this,
            settingsRepository = settingsRepository,
        )
    }
    val repository: ParcelRepository by lazy {
        ParcelRepository(
            database = database,
            dao = database.parcelDao(),
            settingsRepository = settingsRepository,
            connectorRegistry = connectorRegistry,
        )
    }
    val refreshScheduler: RefreshScheduler by lazy { RefreshScheduler(this) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.seedCarrierProfiles()
            val prefs = settingsRepository.preferences.first()
            refreshScheduler.ensureScheduled(prefs.syncIntervalHours)
        }
    }
}
