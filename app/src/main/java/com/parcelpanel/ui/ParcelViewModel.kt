package com.parcelpanel.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.parcelpanel.data.CarrierProfileEntity
import com.parcelpanel.data.ParcelRepository
import com.parcelpanel.model.AppPreferencesModel
import com.parcelpanel.model.CarrierMatch
import com.parcelpanel.model.ParcelDetail
import com.parcelpanel.model.ParcelSummary
import com.parcelpanel.model.SyncEntry
import com.parcelpanel.model.SyncTrigger
import com.parcelpanel.sync.RefreshScheduler
import com.parcelpanel.tracking.CarrierDetector
import com.parcelpanel.update.AppUpdateState
import com.parcelpanel.update.OtaUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ParcelViewModel(
    private val repository: ParcelRepository,
    private val scheduler: RefreshScheduler,
    private val otaUpdateRepository: OtaUpdateRepository,
) : ViewModel() {
    val inbox: StateFlow<List<ParcelSummary>> = repository.observeInbox().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val history: StateFlow<List<ParcelSummary>> = repository.observeHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val carriers: StateFlow<List<CarrierProfileEntity>> = repository.carrierProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val settings: StateFlow<AppPreferencesModel> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferencesModel(),
    )
    val appUpdateState: StateFlow<AppUpdateState> = otaUpdateRepository.state

    private val _pendingSharedText = MutableStateFlow<String?>(null)
    val pendingSharedText: StateFlow<String?> = _pendingSharedText.asStateFlow()

    fun detail(itemId: String) = repository.observeDetail(itemId)

    fun detectCarriers(trackingNumber: String): List<CarrierMatch> =
        CarrierDetector.detect(trackingNumber)

    fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (sharedText.isNotBlank()) {
            _pendingSharedText.value = sharedText
        }
    }

    fun clearPendingSharedText() {
        _pendingSharedText.value = null
    }

    fun addTrackedItem(
        trackingNumber: String,
        label: String,
        notes: String,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val itemId = repository.addTrackedItem(trackingNumber, label, notes)
            onCreated(itemId)
        }
    }

    fun refresh(itemId: String, onComplete: (SyncEntry) -> Unit) {
        viewModelScope.launch {
            onComplete(repository.refreshTrackedItem(itemId, SyncTrigger.MANUAL_REFRESH))
        }
    }

    fun setArchived(itemId: String, archived: Boolean) {
        viewModelScope.launch {
            repository.setArchived(itemId, archived)
        }
    }

    fun setArchived(itemIds: Collection<String>, archived: Boolean, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            onComplete(repository.setArchived(itemIds, archived))
        }
    }

    fun deleteTrackedItem(itemId: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onComplete(repository.deleteTrackedItem(itemId))
        }
    }

    fun deleteTrackedItems(itemIds: Collection<String>, onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            onComplete(repository.deleteTrackedItems(itemIds))
        }
    }

    fun updateSyncInterval(hours: Int) {
        viewModelScope.launch {
            repository.setSyncIntervalHours(hours)
            scheduler.ensureScheduled(hours)
        }
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            otaUpdateRepository.checkForUpdates(manual = manual)
        }
    }

    fun updateAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            otaUpdateRepository.setAutoCheckUpdates(enabled)
        }
    }

    fun downloadLatestUpdate() {
        viewModelScope.launch {
            otaUpdateRepository.downloadLatestUpdate()
        }
    }

    class Factory(
        private val repository: ParcelRepository,
        private val scheduler: RefreshScheduler,
        private val otaUpdateRepository: OtaUpdateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ParcelViewModel(repository, scheduler, otaUpdateRepository) as T
        }
    }
}
