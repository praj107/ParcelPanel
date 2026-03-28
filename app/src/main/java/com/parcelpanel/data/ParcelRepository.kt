package com.parcelpanel.data

import androidx.room.withTransaction
import com.parcelpanel.model.AppPreferencesModel
import com.parcelpanel.model.IdentifierType
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.ParcelDetail
import com.parcelpanel.model.ParcelSummary
import com.parcelpanel.model.SyncEntry
import com.parcelpanel.model.SyncResult
import com.parcelpanel.model.SyncTrigger
import com.parcelpanel.model.TimelineEvent
import com.parcelpanel.tracking.CarrierCatalog
import com.parcelpanel.tracking.CarrierDetector
import com.parcelpanel.tracking.ConnectorRegistry
import com.parcelpanel.tracking.ParsedRefreshEvent
import com.parcelpanel.tracking.RefreshRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

class ParcelRepository(
    private val database: AppDatabase,
    private val dao: ParcelDao,
    private val settingsRepository: SettingsRepository,
    private val connectorRegistry: ConnectorRegistry,
) {
    val carrierProfiles: Flow<List<CarrierProfileEntity>> = dao.observeCarrierProfiles()
    val settings: Flow<AppPreferencesModel> = settingsRepository.preferences

    fun observeInbox(): Flow<List<ParcelSummary>> =
        dao.observeInboxRows().combine(carrierProfiles) { rows, profiles ->
            rows.map { row -> row.toSummary(profiles) }
        }

    fun observeHistory(): Flow<List<ParcelSummary>> =
        dao.observeHistoryRows().combine(carrierProfiles) { rows, profiles ->
            rows.map { row -> row.toSummary(profiles) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDetail(itemId: String): Flow<ParcelDetail?> {
        val snapshotFlow = dao.observeCurrentSnapshot(itemId)
        val eventsFlow = snapshotFlow.flatMapLatest { snapshot ->
            snapshot?.let { dao.observeEvents(it.id) } ?: flowOf(emptyList())
        }
        val baseFlow = combine(
            dao.observeTrackedItem(itemId),
            dao.observePrimaryIdentifier(itemId),
            snapshotFlow,
            dao.observeSyncSessions(itemId),
            carrierProfiles,
        ) { item, identifier, snapshot, syncEntries, profiles ->
            DetailBase(
                item = item,
                identifier = identifier,
                snapshot = snapshot,
                syncEntries = syncEntries,
                profiles = profiles,
            )
        }
        return combine(baseFlow, eventsFlow) { base, events ->
            val item = base.item
            val identifier = base.identifier
            val snapshot = base.snapshot
            val syncEntries = base.syncEntries
            val profiles = base.profiles
            if (item == null || identifier == null) {
                null
            } else {
                val profile = profiles.firstOrNull { it.slug == (item.currentCarrierSlug ?: item.preferredCarrierSlug) }
                val carrierDefinition = CarrierCatalog.bySlug(profile?.slug ?: item.preferredCarrierSlug)
                ParcelDetail(
                    id = item.id,
                    label = item.userLabel ?: identifier.value,
                    notes = item.notes,
                    trackingNumber = identifier.value,
                    carrierDisplayName = profile?.displayName ?: carrierDefinition?.displayName ?: "Unknown carrier",
                    carrierInitials = profile?.initials ?: carrierDefinition?.initials ?: "??",
                    carrierSlug = profile?.slug ?: item.preferredCarrierSlug.orEmpty(),
                    status = item.latestNormalizedStatus,
                    archived = item.archived,
                    serviceName = snapshot?.serviceName,
                    trackerUrl = carrierDefinition?.trackingUrl(identifier.value),
                    updatedAt = item.updatedAt,
                    deliveredAt = item.deliveredAt,
                    etaEnd = snapshot?.etaEnd,
                    timeline = events.map { event ->
                        TimelineEvent(
                            id = event.id,
                            title = event.carrierEventLabel ?: event.normalizedEventType.replace('_', ' '),
                            description = event.description,
                            status = event.normalizedStatus,
                            occurredAt = event.occurredAt,
                            location = null,
                        )
                    },
                    syncEntries = syncEntries.map {
                        SyncEntry(
                            startedAt = it.startedAt,
                            trigger = it.trigger,
                            result = it.result,
                            message = it.errorMessage,
                            trackerUrl = it.trackerUrl,
                        )
                    },
                )
            }
        }
    }

    suspend fun seedCarrierProfiles() {
        if (dao.carrierProfileCount() > 0) return
        val profiles = CarrierCatalog.all.map { definition ->
            CarrierProfileEntity(
                slug = definition.slug,
                displayName = definition.displayName,
                initials = definition.initials,
                authMode = definition.authMode,
                connectorMode = definition.connectorMode,
                supportTier = definition.supportTier,
                activeInAustralia = definition.activeInAustralia,
                supportsPod = definition.supportsPod,
                supportsMultiPiece = definition.supportsMultiPiece,
                trackerUrlTemplate = definition.trackerUrlTemplate,
                notes = definition.notes,
            )
        }
        dao.upsertCarrierProfiles(profiles)
    }

    suspend fun addTrackedItem(
        trackingNumber: String,
        label: String?,
        notes: String?,
    ): String {
        val now = System.currentTimeMillis()
        val cleanTrackingNumber = trackingNumber.trim()
        val candidateCarrier = CarrierDetector.detect(cleanTrackingNumber).firstOrNull()?.slug
        val itemId = UUID.randomUUID().toString()
        val snapshotId = UUID.randomUUID().toString()
        val eventId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()
        val chosenCarrier = candidateCarrier ?: "auspost"

        database.withTransaction {
            dao.upsertTrackedItem(
                TrackedItemEntity(
                    id = itemId,
                    userLabel = label?.trim().takeUnless { it.isNullOrBlank() },
                    preferredCarrierSlug = chosenCarrier,
                    currentCarrierSlug = chosenCarrier,
                    pinned = false,
                    archived = false,
                    notes = notes?.trim().takeUnless { it.isNullOrBlank() },
                    createdAt = now,
                    updatedAt = now,
                    latestNormalizedStatus = NormalizedStatus.UNKNOWN,
                    latestStatusAt = now,
                    deliveredAt = null,
                    autoArchiveAt = null,
                )
            )
            dao.upsertTrackingIdentifier(
                TrackingIdentifierEntity(
                    id = UUID.randomUUID().toString(),
                    trackedItemId = itemId,
                    identifierType = IdentifierType.TRACKING_NUMBER,
                    value = cleanTrackingNumber,
                    isPrimary = true,
                    source = "user",
                    patternHint = CarrierDetector.detect(cleanTrackingNumber).firstOrNull()?.reason,
                    createdAt = now,
                )
            )
            dao.upsertShipmentSnapshot(
                ShipmentSnapshotEntity(
                    id = snapshotId,
                    trackedItemId = itemId,
                    carrierSlug = chosenCarrier,
                    externalShipmentId = cleanTrackingNumber,
                    serviceName = CarrierCatalog.bySlug(chosenCarrier)?.displayName,
                    serviceLevel = null,
                    shipmentType = "parcel",
                    pieceCount = 1,
                    originPlaceId = null,
                    destinationPlaceId = null,
                    etaStart = null,
                    etaEnd = null,
                    deliveredAt = null,
                    signedBy = null,
                    fetchedAt = now,
                    isCurrent = true,
                    rawSummaryJson = """{"source":"local-add","trackingNumber":"$cleanTrackingNumber"}""",
                )
            )
            dao.insertTrackingEvents(
                listOf(
                    TrackingEventEntity(
                        id = eventId,
                        snapshotId = snapshotId,
                        pieceId = null,
                        sequenceNo = 0,
                        occurredAt = now,
                        timezone = null,
                        carrierEventCode = null,
                        carrierEventLabel = "Saved locally",
                        normalizedEventType = "saved_to_parcelpanel",
                        normalizedStatus = NormalizedStatus.UNKNOWN,
                        substatus = null,
                        description = "Parcel saved to local history. Suggested carrier: ${CarrierCatalog.bySlug(chosenCarrier)?.displayName.orEmpty()}",
                        placeId = null,
                        actorType = "app",
                        isException = false,
                        rawEventJson = null,
                    )
                )
            )
            dao.insertSyncSession(
                SyncSessionEntity(
                    id = sessionId,
                    trackedItemId = itemId,
                    carrierSlug = chosenCarrier,
                    trigger = SyncTrigger.USER_ADD,
                    startedAt = now,
                    finishedAt = now,
                    result = SyncResult.SUCCESS,
                    httpStatus = null,
                    attemptNo = 1,
                    nextPollAfter = null,
                    errorCode = null,
                    errorMessage = "Parcel added to ParcelPanel and ready for live tracker refresh attempts.",
                    trackerUrl = CarrierCatalog.bySlug(chosenCarrier)?.trackingUrl(cleanTrackingNumber),
                )
            )
        }
        return itemId
    }

    suspend fun refreshTrackedItem(itemId: String, trigger: SyncTrigger = SyncTrigger.MANUAL_REFRESH): SyncEntry {
        val item = dao.getTrackedItem(itemId) ?: error("Tracked item not found: $itemId")
        val identifier = dao.getPrimaryIdentifier(itemId) ?: error("Primary identifier not found: $itemId")
        val connector = connectorRegistry.connectorFor(item.currentCarrierSlug ?: item.preferredCarrierSlug)
            ?: error("No connector for carrier")
        val now = System.currentTimeMillis()
        val envelope = connector.refresh(
            RefreshRequest(
                trackingNumber = identifier.value,
                currentStatus = item.latestNormalizedStatus,
                trigger = trigger,
            )
        )
        val effectiveStatus = if (envelope.result == SyncResult.SUCCESS) {
            envelope.normalizedStatus
        } else {
            item.latestNormalizedStatus
        }
        val latestEventAt = envelope.events.firstOrNull { it.occurredAt != null }?.occurredAt ?: now
        val deliveredAt = when {
            envelope.result == SyncResult.SUCCESS && envelope.normalizedStatus == NormalizedStatus.DELIVERED ->
                envelope.deliveredAt ?: latestEventAt
            else -> item.deliveredAt
        }
        val persistedEvents = envelope.events.ifEmpty {
            if (envelope.result == SyncResult.SUCCESS) {
                listOf(
                    ParsedRefreshEvent(
                        title = effectiveStatus.label,
                        description = envelope.message,
                        status = effectiveStatus,
                        occurredAt = latestEventAt,
                    )
                )
            } else {
                emptyList()
            }
        }
        val snapshotId = UUID.randomUUID().toString()

        database.withTransaction {
            dao.markSnapshotsNotCurrent(itemId)
            dao.upsertShipmentSnapshot(
                ShipmentSnapshotEntity(
                    id = snapshotId,
                    trackedItemId = itemId,
                    carrierSlug = connector.definition.slug,
                    externalShipmentId = identifier.value,
                    serviceName = envelope.serviceName ?: connector.definition.displayName,
                    serviceLevel = null,
                    shipmentType = "parcel",
                    pieceCount = 1,
                    originPlaceId = null,
                    destinationPlaceId = null,
                    etaStart = null,
                    etaEnd = envelope.etaEnd,
                    deliveredAt = deliveredAt,
                    signedBy = null,
                    fetchedAt = now,
                    isCurrent = true,
                    rawSummaryJson = envelope.rawSummaryJson ?: """{"message":"${escapeJson(envelope.message)}"}""",
                )
            )
            if (persistedEvents.isNotEmpty()) {
                dao.insertTrackingEvents(
                    persistedEvents.mapIndexed { index, event ->
                        TrackingEventEntity(
                            id = UUID.randomUUID().toString(),
                            snapshotId = snapshotId,
                            pieceId = null,
                            sequenceNo = index,
                            occurredAt = event.occurredAt,
                            timezone = null,
                            carrierEventCode = null,
                            carrierEventLabel = event.title,
                            normalizedEventType = event.status.name.lowercase(),
                            normalizedStatus = event.status,
                            substatus = null,
                            description = event.description,
                            placeId = null,
                            actorType = "carrier_web",
                            isException = event.status == NormalizedStatus.EXCEPTION,
                            rawEventJson = """{"message":"${escapeJson(event.description ?: event.title)}"}""",
                        )
                    }
                )
            }
            dao.upsertTrackedItem(
                item.copy(
                    updatedAt = now,
                    currentCarrierSlug = connector.definition.slug,
                    latestNormalizedStatus = effectiveStatus,
                    latestStatusAt = if (envelope.result == SyncResult.SUCCESS) latestEventAt else item.latestStatusAt,
                    deliveredAt = deliveredAt,
                )
            )
            dao.insertSyncSession(
                SyncSessionEntity(
                    id = UUID.randomUUID().toString(),
                    trackedItemId = itemId,
                    carrierSlug = connector.definition.slug,
                    trigger = trigger,
                    startedAt = now,
                    finishedAt = now,
                    result = envelope.result,
                    httpStatus = null,
                    attemptNo = 1,
                    nextPollAfter = null,
                    errorCode = null,
                    errorMessage = envelope.message,
                    trackerUrl = envelope.trackerUrl,
                )
            )
        }

        return SyncEntry(
            startedAt = now,
            trigger = trigger,
            result = envelope.result,
            message = envelope.message,
            trackerUrl = envelope.trackerUrl,
        )
    }

    suspend fun refreshActiveTrackedItems(limit: Int = 10) {
        val items = observeInbox().first().take(limit)
        items.forEach { summary ->
            refreshTrackedItem(summary.id, SyncTrigger.PERIODIC_REFRESH)
        }
    }

    suspend fun setArchived(itemId: String, archived: Boolean) {
        val item = dao.getTrackedItem(itemId) ?: return
        dao.upsertTrackedItem(
            item.copy(
                archived = archived,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        settingsRepository.setSyncIntervalHours(hours)
    }

    private fun TrackedItemListRow.toSummary(profiles: List<CarrierProfileEntity>): ParcelSummary {
        val fallback = CarrierCatalog.bySlug(carrierSlug)
        val profile = profiles.firstOrNull { it.slug == carrierSlug }
        val trackingValue = primaryTrackingNumber ?: "Unknown"
        return ParcelSummary(
            id = id,
            label = userLabel ?: trackingValue,
            trackingNumber = trackingValue,
            carrierDisplayName = carrierDisplayName ?: profile?.displayName ?: fallback?.displayName ?: "Unknown carrier",
            carrierInitials = carrierInitials ?: profile?.initials ?: fallback?.initials ?: "??",
            status = latestNormalizedStatus,
            updatedAt = updatedAt,
            deliveredAt = deliveredAt,
            archived = archived,
            pinned = pinned,
        )
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private data class DetailBase(
        val item: TrackedItemEntity?,
        val identifier: TrackingIdentifierEntity?,
        val snapshot: ShipmentSnapshotEntity?,
        val syncEntries: List<SyncSessionEntity>,
        val profiles: List<CarrierProfileEntity>,
    )
}
