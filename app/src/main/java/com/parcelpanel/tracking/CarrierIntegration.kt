package com.parcelpanel.tracking

import com.parcelpanel.model.CarrierAuthMode
import com.parcelpanel.model.CarrierDefinition
import com.parcelpanel.model.ConnectorMode
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult
import com.parcelpanel.model.SyncTrigger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

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
            notes = "ParcelPanel bootstraps a browser session against the official tracker, then attempts a structured shipment fetch before falling back to page extraction."
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
            notes = "StarTrack rides the same official tracker surface as Australia Post, including the same session-backed structured fetch path."
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
            connectorMode = ConnectorMode.API_READY,
            supportTier = 1,
            activeInAustralia = true,
            supportsPod = true,
            supportsMultiPiece = true,
            trackerUrlTemplate = "https://www.aramex.com.au/tools/track/?l={trackingNumber}",
            notes = "ParcelPanel now uses Aramex Australia's public structured tracking endpoint first, then falls back to the public tracker page if needed."
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
    val trigger: SyncTrigger,
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

data class ConnectorCapabilities(
    val backgroundSafe: Boolean,
    val requiresInteractiveSession: Boolean,
    val structuredPayloadAvailable: Boolean,
)

interface CarrierConnector {
    val definition: CarrierDefinition
    val capabilities: ConnectorCapabilities

    suspend fun refresh(request: RefreshRequest): RefreshEnvelope
}

private class TextScrapingTrackerConnector(
    override val definition: CarrierDefinition,
    private val scraper: TrackerWebScraper,
) : CarrierConnector {
    override val capabilities = ConnectorCapabilities(
        backgroundSafe = true,
        requiresInteractiveSession = false,
        structuredPayloadAvailable = false,
    )

    override suspend fun refresh(request: RefreshRequest): RefreshEnvelope {
        val trackerUrl = definition.trackingUrl(request.trackingNumber)
            ?: return fallbackEnvelope(
                request = request,
                message = "This carrier does not expose a direct tracking page URL in the current catalog.",
            )

        return runCatching {
            fromDocument(
                request = request,
                document = scraper.fetch(trackerUrl, request.trackingNumber),
            )
        }.getOrElse { throwable ->
            fallbackEnvelope(
                request = request,
                message = throwable.message ?: "Official tracker lookup failed. Open the carrier tracker for the latest checkpoint.",
                trackerUrl = trackerUrl,
            )
        }
    }

    fun fromDocument(
        request: RefreshRequest,
        document: TrackerPageDocument,
    ): RefreshEnvelope {
        val parsed = TrackerTextParser.parse(definition.slug, request.trackingNumber, document)
            ?: return fallbackEnvelope(
                request = request,
                message = "Official tracker page loaded, but ParcelPanel could not confidently extract shipment status yet.",
                trackerUrl = document.finalUrl,
                rawSummaryText = document.bodyText,
            )

        val result = if (parsed.message.contains("did not find an active shipment", ignoreCase = true)) {
            SyncResult.ERROR
        } else {
            SyncResult.SUCCESS
        }

        return RefreshEnvelope(
            result = result,
            message = parsed.message,
            trackerUrl = document.finalUrl,
            normalizedStatus = if (result == SyncResult.SUCCESS) parsed.status else request.currentStatus,
            events = parsed.events,
            serviceName = definition.displayName,
            deliveredAt = parsed.deliveredAt,
            etaEnd = parsed.etaEnd,
            rawSummaryJson = buildJsonObject {
                put("carrierSlug", definition.slug)
                put("pageTitle", document.pageTitle ?: "")
                put("finalUrl", document.finalUrl)
                put("summaryText", parsed.rawSummaryText.take(1200))
            }.toString(),
        )
    }

    fun fallbackEnvelope(
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

private class AramexAustraliaConnector(
    override val definition: CarrierDefinition,
    private val httpClient: TrackerHttpClient,
    private val fallbackConnector: TextScrapingTrackerConnector,
) : CarrierConnector {
    override val capabilities = ConnectorCapabilities(
        backgroundSafe = true,
        requiresInteractiveSession = false,
        structuredPayloadAvailable = true,
    )

    override suspend fun refresh(request: RefreshRequest): RefreshEnvelope {
        val trackerUrl = definition.trackingUrl(request.trackingNumber)
            ?: return fallbackConnector.fallbackEnvelope(
                request = request,
                message = "Aramex tracker URL is not configured in the carrier catalog.",
            )

        val endpointUrl =
            "https://www.aramex.com.au/umbraco/api/TrackingApi/GetTrackingData?LabelNo=${urlEncode(request.trackingNumber)}"

        val structuredSnapshot = runCatching {
            val response = httpClient.get(endpointUrl)
            if (response.body.isBlank()) {
                null
            } else {
                AramexAuTrackingParser.parse(request.trackingNumber, response.body)
            }
        }.getOrNull()

        return structuredSnapshot?.toEnvelope(
            trackerUrl = trackerUrl,
            fallbackService = definition.displayName,
        ) ?: fallbackConnector.refresh(request)
    }
}

private class AustraliaPostTrackerConnector(
    override val definition: CarrierDefinition,
    private val scraper: TrackerWebScraper,
    private val fallbackConnector: TextScrapingTrackerConnector,
) : CarrierConnector {
    override val capabilities = ConnectorCapabilities(
        backgroundSafe = false,
        requiresInteractiveSession = true,
        structuredPayloadAvailable = true,
    )

    override suspend fun refresh(request: RefreshRequest): RefreshEnvelope {
        val trackerUrl = definition.trackingUrl(request.trackingNumber)
            ?: return fallbackConnector.fallbackEnvelope(
                request = request,
                message = "Australia Post tracker URL is not configured in the carrier catalog.",
            )

        if (request.trigger == SyncTrigger.PERIODIC_REFRESH) {
            return manualSessionRequiredEnvelope(
                request = request,
                trackerUrl = trackerUrl,
                detail = "Background refresh skipped because Australia Post requires a warm browser session.",
            )
        }

        val sessionDocument = runCatching {
            scraper.fetchWithSessionScript(
                url = trackerUrl,
                trackingNumber = request.trackingNumber,
                script = buildAustraliaPostSessionScript(request.trackingNumber),
            )
        }.getOrElse { throwable ->
            return fallbackConnector.fallbackEnvelope(
                request = request,
                message = throwable.message ?: "Australia Post session bootstrap failed. Refresh manually in ParcelPanel to renew tracker access.",
                trackerUrl = trackerUrl,
            )
        }

        val structuredSnapshot = sessionDocument.scriptPayload?.let { payload ->
            AustraliaPostStructuredParser.parse(request.trackingNumber, payload)
        }
        if (structuredSnapshot != null) {
            return structuredSnapshot.toEnvelope(
                trackerUrl = sessionDocument.responseUrl ?: sessionDocument.finalUrl,
                fallbackService = definition.displayName,
            )
        }

        val blockedBySessionGate = sessionDocument.httpStatus == 403 ||
            sessionDocument.scriptStatus == "http_error" ||
            sessionDocument.bodyText.contains("enable js and disable any ad blocker", ignoreCase = true) ||
            sessionDocument.bodyText.contains("datadome", ignoreCase = true)

        if (blockedBySessionGate) {
            return manualSessionRequiredEnvelope(
                request = request,
                trackerUrl = sessionDocument.finalUrl,
                detail = "Australia Post blocked the structured fetch and needs a renewed in-app browser session.",
                sessionDocument = sessionDocument,
            )
        }

        return fallbackConnector.fromDocument(request, sessionDocument.asPageDocument())
    }

    private fun manualSessionRequiredEnvelope(
        request: RefreshRequest,
        trackerUrl: String?,
        detail: String,
        sessionDocument: TrackerSessionDocument? = null,
    ): RefreshEnvelope {
        return RefreshEnvelope(
            result = SyncResult.SKIPPED,
            message = "Australia Post requires a warm browser session. Refresh this shipment in ParcelPanel to renew tracker access.",
            trackerUrl = trackerUrl,
            normalizedStatus = request.currentStatus,
            rawSummaryJson = buildJsonObject {
                put("carrierSlug", definition.slug)
                put("detail", detail)
                put("sessionStatus", sessionDocument?.scriptStatus ?: "not_started")
                sessionDocument?.httpStatus?.let { put("httpStatus", it) }
                sessionDocument?.errorMessage?.let { put("errorMessage", it) }
                sessionDocument?.bodyText?.takeIf { it.isNotBlank() }?.let { put("bodyText", it.take(600)) }
            }.toString(),
        )
    }
}

private fun StructuredRefreshSnapshot.toEnvelope(
    trackerUrl: String?,
    fallbackService: String,
): RefreshEnvelope = RefreshEnvelope(
    result = result,
    message = message,
    trackerUrl = trackerUrl,
    normalizedStatus = status,
    events = events,
    serviceName = serviceName ?: fallbackService,
    deliveredAt = deliveredAt,
    etaEnd = etaEnd,
    rawSummaryJson = rawSummaryJson,
)

private fun buildAustraliaPostSessionScript(trackingNumber: String): String {
    val trackingLiteral = jsQuoted(trackingNumber)
    return """
        (function() {
          const stateKey = "__parcelpanelAusPostState";
          const trackingId = $trackingLiteral;
          const endpoint = "https://digitalapi.auspost.com.au/shipments-gateway/v1/watchlist/shipments?trackingIds=" + encodeURIComponent(trackingId);
          if (!window[stateKey]) {
            window[stateKey] = { status: "starting" };
            (async function() {
              try {
                const response = await fetch(endpoint, {
                  method: "GET",
                  credentials: "include",
                  headers: {
                    "Content-Type": "application/json",
                    "AP_CHANNEL_NAME": "WEB"
                  }
                });
                const payload = await response.text();
                window[stateKey] = {
                  status: response.ok ? "done" : "http_error",
                  httpStatus: response.status,
                  responseUrl: response.url,
                  payload: payload
                };
              } catch (error) {
                window[stateKey] = {
                  status: "error",
                  errorMessage: String(error)
                };
              }
            })();
            return "";
          }
          return JSON.stringify(window[stateKey]);
        })();
    """.trimIndent()
}

private fun jsQuoted(value: String): String =
    buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

private fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

class ConnectorRegistry(
    context: android.content.Context,
) {
    private val scraper = TrackerWebScraper(context)
    private val httpClient = TrackerHttpClient()
    private val textConnectors = CarrierCatalog.all.associate { definition ->
        definition.slug to TextScrapingTrackerConnector(definition, scraper)
    }
    private val connectors: Map<String, CarrierConnector> = CarrierCatalog.all.associate { definition ->
        val connector: CarrierConnector = when (definition.slug) {
            "aramex" -> AramexAustraliaConnector(
                definition = definition,
                httpClient = httpClient,
                fallbackConnector = textConnectors.getValue(definition.slug),
            )

            "auspost", "startrack" -> AustraliaPostTrackerConnector(
                definition = definition,
                scraper = scraper,
                fallbackConnector = textConnectors.getValue(definition.slug),
            )

            else -> textConnectors.getValue(definition.slug)
        }
        definition.slug to connector
    }

    fun connectorFor(slug: String?): CarrierConnector? = slug?.let { connectors[it] }
}
