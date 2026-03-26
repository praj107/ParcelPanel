package com.parcelpanel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParcelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackedItem(item: TrackedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackingIdentifier(identifier: TrackingIdentifierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCarrierProfiles(profiles: List<CarrierProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShipmentSnapshot(snapshot: ShipmentSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackingEvents(events: List<TrackingEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncSession(session: SyncSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawPayload(rawPayload: RawPayloadEntity)

    @Query("UPDATE shipment_snapshot SET is_current = 0 WHERE tracked_item_id = :trackedItemId")
    suspend fun markSnapshotsNotCurrent(trackedItemId: String)

    @Query(
        """
        SELECT
            t.id,
            t.user_label,
            ti.value AS primary_tracking_number,
            COALESCE(t.current_carrier_slug, t.preferred_carrier_slug) AS carrier_slug,
            cp.display_name AS carrier_display_name,
            cp.initials AS carrier_initials,
            t.latest_normalized_status,
            t.updated_at,
            t.delivered_at,
            t.pinned,
            t.archived
        FROM tracked_item t
        LEFT JOIN tracking_identifier ti
            ON ti.tracked_item_id = t.id AND ti.is_primary = 1
        LEFT JOIN carrier_profile cp
            ON cp.slug = COALESCE(t.current_carrier_slug, t.preferred_carrier_slug)
        WHERE t.archived = 0
          AND t.latest_normalized_status NOT IN ('DELIVERED', 'RETURNED', 'CANCELLED')
        ORDER BY t.pinned DESC, t.updated_at DESC
        """
    )
    fun observeInboxRows(): Flow<List<TrackedItemListRow>>

    @Query(
        """
        SELECT
            t.id,
            t.user_label,
            ti.value AS primary_tracking_number,
            COALESCE(t.current_carrier_slug, t.preferred_carrier_slug) AS carrier_slug,
            cp.display_name AS carrier_display_name,
            cp.initials AS carrier_initials,
            t.latest_normalized_status,
            t.updated_at,
            t.delivered_at,
            t.pinned,
            t.archived
        FROM tracked_item t
        LEFT JOIN tracking_identifier ti
            ON ti.tracked_item_id = t.id AND ti.is_primary = 1
        LEFT JOIN carrier_profile cp
            ON cp.slug = COALESCE(t.current_carrier_slug, t.preferred_carrier_slug)
        WHERE t.archived = 1
           OR t.latest_normalized_status IN ('DELIVERED', 'RETURNED', 'CANCELLED')
        ORDER BY COALESCE(t.delivered_at, t.updated_at) DESC
        """
    )
    fun observeHistoryRows(): Flow<List<TrackedItemListRow>>

    @Query("SELECT * FROM tracked_item WHERE id = :itemId LIMIT 1")
    fun observeTrackedItem(itemId: String): Flow<TrackedItemEntity?>

    @Query("SELECT * FROM tracking_identifier WHERE tracked_item_id = :itemId AND is_primary = 1 LIMIT 1")
    fun observePrimaryIdentifier(itemId: String): Flow<TrackingIdentifierEntity?>

    @Query("SELECT * FROM shipment_snapshot WHERE tracked_item_id = :itemId AND is_current = 1 LIMIT 1")
    fun observeCurrentSnapshot(itemId: String): Flow<ShipmentSnapshotEntity?>

    @Query("SELECT * FROM tracking_event WHERE snapshot_id = :snapshotId ORDER BY sequence_no DESC")
    fun observeEvents(snapshotId: String): Flow<List<TrackingEventEntity>>

    @Query("SELECT * FROM sync_session WHERE tracked_item_id = :itemId ORDER BY started_at DESC LIMIT 12")
    fun observeSyncSessions(itemId: String): Flow<List<SyncSessionEntity>>

    @Query("SELECT * FROM carrier_profile ORDER BY support_tier, display_name")
    fun observeCarrierProfiles(): Flow<List<CarrierProfileEntity>>

    @Query("SELECT * FROM tracked_item WHERE id = :itemId LIMIT 1")
    suspend fun getTrackedItem(itemId: String): TrackedItemEntity?

    @Query("SELECT * FROM tracking_identifier WHERE tracked_item_id = :itemId AND is_primary = 1 LIMIT 1")
    suspend fun getPrimaryIdentifier(itemId: String): TrackingIdentifierEntity?

    @Query("SELECT COUNT(*) FROM carrier_profile")
    suspend fun carrierProfileCount(): Int
}

