package com.parcelpanel.data

import androidx.room.TypeConverter
import com.parcelpanel.model.CarrierAuthMode
import com.parcelpanel.model.ConnectorMode
import com.parcelpanel.model.IdentifierType
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult
import com.parcelpanel.model.SyncTrigger

class Converters {
    @TypeConverter
    fun normalizedStatusToString(value: NormalizedStatus?): String? = value?.name

    @TypeConverter
    fun stringToNormalizedStatus(value: String?): NormalizedStatus? = value?.let(NormalizedStatus::valueOf)

    @TypeConverter
    fun identifierTypeToString(value: IdentifierType?): String? = value?.name

    @TypeConverter
    fun stringToIdentifierType(value: String?): IdentifierType? = value?.let(IdentifierType::valueOf)

    @TypeConverter
    fun authModeToString(value: CarrierAuthMode?): String? = value?.name

    @TypeConverter
    fun stringToAuthMode(value: String?): CarrierAuthMode? = value?.let(CarrierAuthMode::valueOf)

    @TypeConverter
    fun connectorModeToString(value: ConnectorMode?): String? = value?.name

    @TypeConverter
    fun stringToConnectorMode(value: String?): ConnectorMode? = value?.let(ConnectorMode::valueOf)

    @TypeConverter
    fun syncTriggerToString(value: SyncTrigger?): String? = value?.name

    @TypeConverter
    fun stringToSyncTrigger(value: String?): SyncTrigger? = value?.let(SyncTrigger::valueOf)

    @TypeConverter
    fun syncResultToString(value: SyncResult?): String? = value?.name

    @TypeConverter
    fun stringToSyncResult(value: String?): SyncResult? = value?.let(SyncResult::valueOf)
}

