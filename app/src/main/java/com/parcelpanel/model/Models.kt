package com.parcelpanel.model

enum class NormalizedStatus(val label: String, val isTerminal: Boolean) {
    UNKNOWN("Unknown", false),
    LABEL_CREATED("Label created", false),
    ACCEPTED("Accepted", false),
    IN_TRANSIT("In transit", false),
    CUSTOMS_OR_CLEARANCE("Customs / clearance", false),
    OUT_FOR_DELIVERY("Out for delivery", false),
    AVAILABLE_FOR_COLLECTION("Collect from depot", false),
    DELIVERY_ATTEMPTED("Delivery attempted", false),
    DELIVERED("Delivered", true),
    EXCEPTION("Exception", false),
    RETURNED("Returned", true),
    CANCELLED("Cancelled", true),
}

enum class CarrierAuthMode {
    PUBLIC,
    USER_SUPPLIED_KEY,
    USER_SUPPLIED_ACCOUNT,
    EXTERNAL_TRACKER_ONLY,
    ENTERPRISE_ONLY,
}

enum class ConnectorMode {
    EXTERNAL_TRACKER,
    API_READY,
    ACCOUNT_READY,
    ENTERPRISE_CONTACT,
}

enum class IdentifierType {
    TRACKING_NUMBER,
    CONSIGNMENT_NUMBER,
    REFERENCE,
}

enum class SyncTrigger {
    USER_ADD,
    MANUAL_REFRESH,
    PERIODIC_REFRESH,
}

enum class SyncResult {
    SUCCESS,
    SEE_EXTERNAL_TRACKER,
    SKIPPED,
    ERROR,
}

data class CarrierDefinition(
    val slug: String,
    val displayName: String,
    val initials: String,
    val authMode: CarrierAuthMode,
    val connectorMode: ConnectorMode,
    val supportTier: Int,
    val activeInAustralia: Boolean,
    val supportsPod: Boolean,
    val supportsMultiPiece: Boolean,
    val trackerUrlTemplate: String?,
    val notes: String,
) {
    fun trackingUrl(trackingNumber: String): String? =
        trackerUrlTemplate?.replace("{trackingNumber}", trackingNumber.trim())
}

data class CarrierMatch(
    val slug: String,
    val displayName: String,
    val confidence: Int,
    val reason: String,
)

data class TimelineEvent(
    val id: String,
    val title: String,
    val description: String?,
    val status: NormalizedStatus,
    val occurredAt: Long?,
    val location: String?,
)

data class SyncEntry(
    val startedAt: Long,
    val trigger: SyncTrigger,
    val result: SyncResult,
    val message: String?,
    val trackerUrl: String?,
)

data class ParcelSummary(
    val id: String,
    val label: String,
    val trackingNumber: String,
    val carrierDisplayName: String,
    val carrierInitials: String,
    val status: NormalizedStatus,
    val updatedAt: Long,
    val deliveredAt: Long?,
    val archived: Boolean,
    val pinned: Boolean,
)

data class ParcelDetail(
    val id: String,
    val label: String,
    val notes: String?,
    val trackingNumber: String,
    val carrierDisplayName: String,
    val carrierInitials: String,
    val carrierSlug: String,
    val status: NormalizedStatus,
    val archived: Boolean,
    val serviceName: String?,
    val trackerUrl: String?,
    val updatedAt: Long,
    val deliveredAt: Long?,
    val etaEnd: Long?,
    val timeline: List<TimelineEvent>,
    val syncEntries: List<SyncEntry>,
)

data class AppPreferencesModel(
    val syncIntervalHours: Int = 4,
    val apiKeys: Map<String, String> = emptyMap(),
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheckAt: Long? = null,
)
