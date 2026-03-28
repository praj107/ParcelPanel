package com.parcelpanel.tracking

import com.parcelpanel.model.CarrierAuthMode
import com.parcelpanel.model.CarrierDefinition
import com.parcelpanel.model.ConnectorMode
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CarrierCatalog {
    val all: List<CarrierDefinition> = listOf(
        CarrierDefinition(
            slug = "auspost",
            displayName = "Australia Post",
            initials = "AP",
            authMode = CarrierAuthMode.PUBLIC,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://auspost.com.au/mypost/track/#/details/{trackingNumber}",
            notes = "ParcelPanel now attempts to scrape the official consumer tracker first, then falls back to hand-off if the page blocks extraction."
        ),
        CarrierDefinition(
            slug = "startrack",
            displayName = "StarTrack",
            initials = "ST",
            authMode = CarrierAuthMode.PUBLIC,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://auspost.com.au/mypost/track/#/details/{trackingNumber}",
            notes = "ParcelPanel now attempts to scrape the official StarTrack/AusPost tracker first."
        ),
        CarrierDefinition(
            slug = "dhl",
            displayName = "DHL",
            initials = "DH",
            authMode = CarrierAuthMode.PUBLIC,
            connectorMode = ConnectorMode.API_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.dhl.com/au-en/home/tracking/tracking-express.html?submit=1&tracking-id={trackingNumber}",
            notes = "ParcelPanel tries the public DHL tracker page first and can still move to account-backed APIs later."
        ),
        CarrierDefinition(
            slug = "fedex",
            displayName = "FedEx / TNT",
            initials = "FX",
            authMode = CarrierAuthMode.PUBLIC,
            connectorMode = ConnectorMode.ACCOUNT_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.fedex.com/fedextrack/?trknbr={trackingNumber}",
            notes = "ParcelPanel tries the public FedEx tracker page first and can still move to account-backed APIs later."
        ),
        CarrierDefinition(
            slug = "ups",
            displayName = "UPS",
            initials = "UP",
            authMode = CarrierAuthMode.PUBLIC,
            connectorMode = ConnectorMode.ACCOUNT_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.ups.com/track?tracknum={trackingNumber}",
            notes = "ParcelPanel tries the public UPS tracker page first and can still move to account-backed APIs later."
        ),
        CarrierDefinition(
            slug = "aramex",
            displayName = "Aramex Australia",
            initials = "AR",
            authMode = CarrierAuthMode.PUBLIC,
            connectorMode = ConnectorMode.ACCOUNT_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.aramex.com/au/en/track/results?ShipmentNumber={trackingNumber}&source=aramex",
            notes = "ParcelPanel uses the public Aramex tracker results page first; official SOAP APIs can still be added later for credentialed users."
        ),
        CarrierDefinition(
            slug = "couriersplease",
            displayName = "CouriersPlease",
            initials = "CP",
            authMode = CarrierAuthMode.PUBLIC,
            connectorMode = ConnectorMode.EXTERNAL_TRACKER,
            supportTier = 2,
            activeInAustralia = true,
            supportsPod = false,
            supportsMultiPiece = false,
            trackerUrlTemplate = "https://dev.couriersplease.com.au/Tools/Track?no={trackingNumber}",
            notes = "ParcelPanel attempts to scrape the public CouriersPlease tracker first."
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
    val normalizedStatus: NormalizedStatus = NormalizedStatus.UNKNOWN,
    val events: List<ParsedRefreshEvent> = emptyList(),
    val serviceName: String? = null,
    val deliveredAt: Long? = null,
    val etaEnd: Long? = null,
    val rawSummaryJson: String? = null,
)

interface CarrierConnector {
    val definition: CarrierDefinition

    suspend fun refresh(request: RefreshRequest): RefreshEnvelope
}

private class ScrapingTrackerConnector(
    override val definition: CarrierDefinition,
    private val scraper: TrackerWebScraper,
) : CarrierConnector {
    override suspend fun refresh(request: RefreshRequest): RefreshEnvelope {
        val trackerUrl = definition.trackingUrl(request.trackingNumber)
            ?: return fallbackEnvelope(request, "This carrier does not expose a direct tracking page URL in the current catalog.")

        return runCatching {
            val page = scraper.fetch(trackerUrl, request.trackingNumber)
            val parsed = TrackerTextParser.parse(definition.slug, request.trackingNumber, page)
                ?: return@runCatching fallbackEnvelope(
                    request = request,
                    message = "Official tracker page loaded, but ParcelPanel could not confidently extract shipment status yet.",
                    trackerUrl = page.finalUrl,
                    rawSummaryText = page.bodyText,
                )
            val result = if (parsed.message.contains("did not find an active shipment", ignoreCase = true)) {
                SyncResult.ERROR
            } else {
                SyncResult.SUCCESS
            }
            RefreshEnvelope(
                result = result,
                message = parsed.message,
                trackerUrl = page.finalUrl,
                normalizedStatus = if (result == SyncResult.SUCCESS) parsed.status else request.currentStatus,
                events = parsed.events,
                serviceName = definition.displayName,
                deliveredAt = parsed.deliveredAt,
                etaEnd = parsed.etaEnd,
                rawSummaryJson = buildJsonObject {
                    put("carrierSlug", definition.slug)
                    put("pageTitle", page.pageTitle ?: "")
                    put("finalUrl", page.finalUrl)
                    put("summaryText", parsed.rawSummaryText.take(1200))
                }.toString(),
            )
        }.getOrElse { throwable ->
            fallbackEnvelope(
                request = request,
                message = throwable.message ?: "Official tracker lookup failed. Open the carrier tracker for the latest checkpoint.",
                trackerUrl = trackerUrl,
            )
        }
    }

    private fun fallbackEnvelope(
        request: RefreshRequest,
        message: String,
        trackerUrl: String? = definition.trackingUrl(request.trackingNumber),
        rawSummaryText: String? = null,
    ): RefreshEnvelope {
        return RefreshEnvelope(
            result = SyncResult.SEE_EXTERNAL_TRACKER,
            message = message,
            trackerUrl = trackerUrl,
            normalizedStatus = request.currentStatus,
            rawSummaryJson = rawSummaryText?.let { summaryText ->
                buildJsonObject {
                    put("carrierSlug", definition.slug)
                    put("summaryText", summaryText.take(1200))
                }.toString()
            },
        )
    }
}

class ConnectorRegistry(
    context: android.content.Context,
) {
    private val scraper = TrackerWebScraper(context)
    private val connectors = CarrierCatalog.all.associate { definition ->
        definition.slug to ScrapingTrackerConnector(definition, scraper)
    }

    fun connectorFor(slug: String?): CarrierConnector? = slug?.let { connectors[it] }
}
