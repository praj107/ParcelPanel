package com.parcelpanel.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackedItemEntity::class,
        TrackingIdentifierEntity::class,
        CarrierProfileEntity::class,
        ShipmentSnapshotEntity::class,
        ShipmentPieceEntity::class,
        TrackingEventEntity::class,
        PlaceEntity::class,
        DeliveryArtifactEntity::class,
        SyncSessionEntity::class,
        RawPayloadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun parcelDao(): ParcelDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "parcelpanel.db"
            ).build()
    }
}

