package com.parcelpanel.tracking

import com.parcelpanel.model.CarrierAuthMode
import com.parcelpanel.model.CarrierDefinition
import com.parcelpanel.model.ConnectorMode
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult

object CarrierCatalog {
    val all: List<CarrierDefinition> = listOf(
        CarrierDefinition(
            slug = "auspost",
            displayName = "Australia Post",
            initials = "AP",
            authMode = CarrierAuthMode.EXTERNAL_TRACKER_ONLY,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://auspost.com.au/mypost/track/#/details/{trackingNumber}",
            notes = "Public consumer tracking is available; authenticated business APIs are contract-oriented."
        ),
        CarrierDefinition(
            slug = "startrack",
            displayName = "StarTrack",
            initials = "ST",
            authMode = CarrierAuthMode.EXTERNAL_TRACKER_ONLY,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://auspost.com.au/mypost/track/#/details/{trackingNumber}",
            notes = "Handled alongside Australia Post in v1."
        ),
        CarrierDefinition(
            slug = "dhl",
            displayName = "DHL",
            initials = "DH",
            authMode = CarrierAuthMode.USER_SUPPLIED_KEY,
            connectorMode = ConnectorMode.API_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.dhl.com/au-en/home/tracking/tracking-express.html?submit=1&tracking-id={trackingNumber}",
            notes = "Official tracking APIs exist, but mobile-safe live polling should use a user-supplied key."
        ),
        CarrierDefinition(
            slug = "fedex",
            displayName = "FedEx / TNT",
            initials = "FX",
            authMode = CarrierAuthMode.USER_SUPPLIED_ACCOUNT,
            connectorMode = ConnectorMode.ACCOUNT_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.fedex.com/fedextrack/?trknbr={trackingNumber}",
            notes = "FedEx Track API is available; live mobile polling is deferred until account-backed configuration exists."
        ),
        CarrierDefinition(
            slug = "ups",
            displayName = "UPS",
            initials = "UP",
            authMode = CarrierAuthMode.USER_SUPPLIED_ACCOUNT,
            connectorMode = ConnectorMode.ACCOUNT_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.ups.com/track?tracknum={trackingNumber}",
            notes = "UPS developer APIs exist, but safe mobile use still needs user account credentials."
        ),
        CarrierDefinition(
            slug = "aramex",
            displayName = "Aramex Australia",
            initials = "AR",
            authMode = CarrierAuthMode.USER_SUPPLIED_ACCOUNT,
            connectorMode = ConnectorMode.ACCOUNT_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.aramex.com/au/en/track/shipments/{trackingNumber}",
            notes = "Good Australian coverage, with public tracking and account-driven integrations."
        ),
        CarrierDefinition(
            slug = "couriersplease",
            displayName = "CouriersPlease",
            initials = "CP",
            authMode = CarrierAuthMode.EXTERNAL_TRACKER_ONLY,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 2,
            activeInAustralia = true,
            supportsPod = false,
            supportsMultiPiece = false,
            trackerUrlTemplate = "https://dev.couriersplease.com.au/Tools/Track?no={trackingNumber}",
            notes = "Consumer tracking is available; public integration docs are limited."
        ),
        CarrierDefinition(
            slug = "directfreight",
            displayName = "Direct Freight Express",
            initials = "DF",
            authMode = CarrierAuthMode.EXTERNAL_TRACKER_ONLY,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 2,
            activeInAustralia = true,
            supportsPod = false,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.directfreight.com.au/TrackTrace.aspx",
            notes = "Important domestic carrier, but v1 uses external tracking hand-off."
        ),
        CarrierDefinition(
            slug = "teamge",
            displayName = "Team Global Express",
            initials = "TG",
            authMode = CarrierAuthMode.EXTERNAL_TRACKER_ONLY,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 2,
            activeInAustralia = true,
            supportsPod = false,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://teamglobalexp.com/en",
            notes = "MyTeamGE is relevant, but live public API coverage is not strong enough for direct polling in this build."
        ),
        CarrierDefinition(
            slug = "toll",
            displayName = "Toll",
            initials = "TL",
            authMode = CarrierAuthMode.ENTERPRISE_ONLY,
            connectorMode = ConnectorMode.ENTERPRISE_CONTACT,
            supportTier = 3,
            activeInAustralia = true,
            supportsPod = false,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.tollgroup.com/",
            notes = "Included in the catalog, but intentionally deferred from active v1 tracking."
        ),
    )

    fun bySlug(slug: String?): CarrierDefinition? = all.firstOrNull { it.slug == slug }
}

data class RefreshRequest(
    val trackingNumber: String,
    val currentStatus: NormalizedStatus,
)

data class RefreshEnvelope(
    val result: SyncResult,
    val message: String,
    val trackerUrl: String?,
)

interface CarrierConnector {
    val definition: CarrierDefinition

    suspend fun refresh(request: RefreshRequest): RefreshEnvelope
}

private class ExternalTrackerConnector(
    override val definition: CarrierDefinition,
) : CarrierConnector {
    override suspend fun refresh(request: RefreshRequest): RefreshEnvelope {
        val message = when (definition.connectorMode) {
            ConnectorMode.API_READY ->
                "Live polling is reserved for a future key-backed integration. Open the carrier tracker for the latest checkpoint."
            ConnectorMode.ACCOUNT_READY ->
                "This carrier typically requires account-backed auth. ParcelPanel stores your item locally and can hand off to the official tracker."
            ConnectorMode.ENTERPRISE_CONTACT ->
                "This carrier is catalogued, but enterprise integration remains outside the current solo-dev scope."
            ConnectorMode.EXTERNAL_TRACKER ->
                "ParcelPanel saved the shipment locally. Open the official tracker to fetch the latest status."
        }
        return RefreshEnvelope(
            result = SyncResult.SEE_EXTERNAL_TRACKER,
            message = message,
            trackerUrl = definition.trackingUrl(request.trackingNumber)
        )
    }
}

class ConnectorRegistry {
    private val connectors = CarrierCatalog.all.associate { definition ->
        definition.slug to ExternalTrackerConnector(definition)
    }

    fun connectorFor(slug: String?): CarrierConnector? = slug?.let { connectors[it] }
}

