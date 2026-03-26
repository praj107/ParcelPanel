package com.parcelpanel.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.parcelpanel.model.CarrierAuthMode
import com.parcelpanel.model.ConnectorMode
import com.parcelpanel.model.IdentifierType
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult
import com.parcelpanel.model.SyncTrigger

@Entity(
    tableName = "tracked_item",
    indices = [Index("archived"), Index("current_carrier_slug"), Index("latest_normalized_status")]
)
data class TrackedItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_label") val userLabel: String?,
    @ColumnInfo(name = "preferred_carrier_slug") val preferredCarrierSlug: String?,
    @ColumnInfo(name = "current_carrier_slug") val currentCarrierSlug: String?,
    val pinned: Boolean,
    val archived: Boolean,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "latest_normalized_status") val latestNormalizedStatus: NormalizedStatus,
    @ColumnInfo(name = "latest_status_at") val latestStatusAt: Long?,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long?,
    @ColumnInfo(name = "auto_archive_at") val autoArchiveAt: Long?,
)

@Entity(tableName = "tracking_identifier", indices = [Index("tracked_item_id"), Index("value")])
data class TrackingIdentifierEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tracked_item_id") val trackedItemId: String,
    @ColumnInfo(name = "identifier_type") val identifierType: IdentifierType,
    val value: String,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
    val source: String,
    @ColumnInfo(name = "pattern_hint") val patternHint: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "carrier_profile")
data class CarrierProfileEntity(
    @PrimaryKey val slug: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "initials") val initials: String,
    @ColumnInfo(name = "auth_mode") val authMode: CarrierAuthMode,
    @ColumnInfo(name = "connector_mode") val connectorMode: ConnectorMode,
    @ColumnInfo(name = "support_tier") val supportTier: Int,
    @ColumnInfo(name = "active_in_australia") val activeInAustralia: Boolean,
    @ColumnInfo(name = "supports_pod") val supportsPod: Boolean,
    @ColumnInfo(name = "supports_multi_piece") val supportsMultiPiece: Boolean,
    @ColumnInfo(name = "tracker_url_template") val trackerUrlTemplate: String?,
    val notes: String,
)

@Entity(tableName = "shipment_snapshot", indices = [Index("tracked_item_id"), Index("carrier_slug"), Index("is_current")])
data class ShipmentSnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tracked_item_id") val trackedItemId: String,
    @ColumnInfo(name = "carrier_slug") val carrierSlug: String,
    @ColumnInfo(name = "external_shipment_id") val externalShipmentId: String?,
    @ColumnInfo(name = "service_name") val serviceName: String?,
    @ColumnInfo(name = "service_level") val serviceLevel: String?,
    @ColumnInfo(name = "shipment_type") val shipmentType: String?,
    @ColumnInfo(name = "piece_count") val pieceCount: Int?,
    @ColumnInfo(name = "origin_place_id") val originPlaceId: String?,
    @ColumnInfo(name = "destination_place_id") val destinationPlaceId: String?,
    @ColumnInfo(name = "eta_start") val etaStart: Long?,
    @ColumnInfo(name = "eta_end") val etaEnd: Long?,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long?,
    @ColumnInfo(name = "signed_by") val signedBy: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "is_current") val isCurrent: Boolean,
    @ColumnInfo(name = "raw_summary_json") val rawSummaryJson: String?,
)

@Entity(tableName = "shipment_piece", indices = [Index("snapshot_id")])
data class ShipmentPieceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "snapshot_id") val snapshotId: String,
    @ColumnInfo(name = "piece_ref") val pieceRef: String?,
    @ColumnInfo(name = "piece_index") val pieceIndex: Int?,
    @ColumnInfo(name = "weight_grams") val weightGrams: Int?,
    @ColumnInfo(name = "dimensions_json") val dimensionsJson: String?,
    @ColumnInfo(name = "latest_event_id") val latestEventId: String?,
)

@Entity(tableName = "tracking_event", indices = [Index("snapshot_id"), Index("piece_id")])
data class TrackingEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "snapshot_id") val snapshotId: String,
    @ColumnInfo(name = "piece_id") val pieceId: String?,
    @ColumnInfo(name = "sequence_no") val sequenceNo: Int,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long?,
    val timezone: String?,
    @ColumnInfo(name = "carrier_event_code") val carrierEventCode: String?,
    @ColumnInfo(name = "carrier_event_label") val carrierEventLabel: String?,
    @ColumnInfo(name = "normalized_event_type") val normalizedEventType: String,
    @ColumnInfo(name = "normalized_status") val normalizedStatus: NormalizedStatus,
    val substatus: String?,
    val description: String?,
    @ColumnInfo(name = "place_id") val placeId: String?,
    @ColumnInfo(name = "actor_type") val actorType: String?,
    @ColumnInfo(name = "is_exception") val isException: Boolean,
    @ColumnInfo(name = "raw_event_json") val rawEventJson: String?,
)

@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val city: String?,
    val state: String?,
    val postcode: String?,
    @ColumnInfo(name = "country_code") val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
)

@Entity(tableName = "delivery_artifact", indices = [Index("snapshot_id")])
data class DeliveryArtifactEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "snapshot_id") val snapshotId: String,
    @ColumnInfo(name = "artifact_type") val artifactType: String,
    val label: String?,
    @ColumnInfo(name = "uri_or_blob_ref") val uriOrBlobRef: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val checksum: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "sync_session", indices = [Index("tracked_item_id"), Index("carrier_slug")])
data class SyncSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tracked_item_id") val trackedItemId: String,
    @ColumnInfo(name = "carrier_slug") val carrierSlug: String,
    val trigger: SyncTrigger,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    val result: SyncResult,
    @ColumnInfo(name = "http_status") val httpStatus: Int?,
    @ColumnInfo(name = "attempt_no") val attemptNo: Int,
    @ColumnInfo(name = "next_poll_after") val nextPollAfter: Long?,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "tracker_url") val trackerUrl: String?,
)

@Entity(tableName = "raw_payload", indices = [Index("snapshot_id")])
data class RawPayloadEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "snapshot_id") val snapshotId: String,
    val source: String,
    @ColumnInfo(name = "content_type") val contentType: String?,
    @ColumnInfo(name = "payload_text") val payloadText: String,
    @ColumnInfo(name = "redaction_level") val redactionLevel: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long?,
)

data class TrackedItemListRow(
    val id: String,
    @ColumnInfo(name = "user_label") val userLabel: String?,
    @ColumnInfo(name = "primary_tracking_number") val primaryTrackingNumber: String?,
    @ColumnInfo(name = "carrier_slug") val carrierSlug: String?,
    @ColumnInfo(name = "carrier_display_name") val carrierDisplayName: String?,
    @ColumnInfo(name = "carrier_initials") val carrierInitials: String?,
    @ColumnInfo(name = "latest_normalized_status") val latestNormalizedStatus: NormalizedStatus,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long?,
    val pinned: Boolean,
    val archived: Boolean,
)
