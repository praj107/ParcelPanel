package com.parcelpanel.tracking

import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StructuredRefreshSnapshot(
    val result: SyncResult,
    val status: NormalizedStatus,
    val message: String,
    val events: List<ParsedRefreshEvent>,
    val deliveredAt: Long?,
    val etaEnd: Long?,
    val serviceName: String?,
    val rawSummaryJson: String,
)

object AramexAuTrackingParser {
    fun parse(
        trackingNumber: String,
        rawResponseBody: String,
    ): StructuredRefreshSnapshot? {
        val jsonBody = extractJsonObject(rawResponseBody) ?: return null
        val root = runCatching {
            carrierJson.parseToJsonElement(jsonBody).jsonObject
        }.getOrNull() ?: return null

        val errorMessage = root.stringOrNull("error")
        if (!errorMessage.isNullOrBlank()) {
            return when {
                errorMessage.contains("does not have any scans", ignoreCase = true) ->
                    StructuredRefreshSnapshot(
                        result = SyncResult.SUCCESS,
                        status = NormalizedStatus.LABEL_CREATED,
                        message = "Tracking number allocated. Waiting for the first Aramex scan.",
                        events = emptyList(),
                        deliveredAt = null,
                        etaEnd = null,
                        serviceName = null,
                        rawSummaryJson = root.toString(),
                    )

                errorMessage.contains("invalid label number", ignoreCase = true) ->
                    StructuredRefreshSnapshot(
                        result = SyncResult.ERROR,
                        status = NormalizedStatus.UNKNOWN,
                        message = "Aramex did not recognize tracking number $trackingNumber.",
                        events = emptyList(),
                        deliveredAt = null,
                        etaEnd = null,
                        serviceName = null,
                        rawSummaryJson = root.toString(),
                    )

                else ->
                    StructuredRefreshSnapshot(
                        result = SyncResult.ERROR,
                        status = NormalizedStatus.UNKNOWN,
                        message = errorMessage,
                        events = emptyList(),
                        deliveredAt = null,
                        etaEnd = null,
                        serviceName = null,
                        rawSummaryJson = root.toString(),
                    )
            }
        }

        val result = root.objectOrNull("result") ?: root
        val scans = result.arrayOrNull("Scans").orEmpty()
            .mapNotNull { parseAramexScan(it) }

        if (scans.isEmpty()) {
            val summary = listOfNotNull(
                result.stringOrNull("StatusDescription"),
                result.stringOrNull("LatestStatus"),
            ).firstOrNull()
                ?: "Official Aramex tracker responded, but no scan timeline was exposed."
            val summaryStatus = mapStructuredStatus(
                primaryCode = result.stringOrNull("Status"),
                message = summary,
                acceptedCodes = ARAMEX_ACCEPTED_CODES,
                inTransitCodes = ARAMEX_IN_TRANSIT_CODES,
                exceptionCodes = ARAMEX_EXCEPTION_CODES,
                returnedCodes = ARAMEX_RETURNED_CODES,
                deliveredCodes = ARAMEX_DELIVERED_CODES,
                outForDeliveryCodes = ARAMEX_OUT_FOR_DELIVERY_CODES,
                deliveryAttemptCodes = ARAMEX_DELIVERY_ATTEMPT_CODES,
                collectionCodes = ARAMEX_COLLECTION_CODES,
                labelCreatedCodes = ARAMEX_LABEL_CREATED_CODES,
            )
            return StructuredRefreshSnapshot(
                result = SyncResult.SUCCESS,
                status = summaryStatus,
                message = summary,
                events = emptyList(),
                deliveredAt = null,
                etaEnd = parseAramexDate(result.stringOrNull("DeliveryETADate"), endOfDayIfDateOnly = true),
                serviceName = resolveAramexServiceName(result),
                rawSummaryJson = result.toString(),
            )
        }

        val events = scans.take(MAX_STRUCTURED_EVENTS).map { scan ->
            val status = mapStructuredStatus(
                primaryCode = scan.statusCode,
                message = scan.message,
                acceptedCodes = ARAMEX_ACCEPTED_CODES,
                inTransitCodes = ARAMEX_IN_TRANSIT_CODES,
                exceptionCodes = ARAMEX_EXCEPTION_CODES,
                returnedCodes = ARAMEX_RETURNED_CODES,
                deliveredCodes = ARAMEX_DELIVERED_CODES,
                outForDeliveryCodes = ARAMEX_OUT_FOR_DELIVERY_CODES,
                deliveryAttemptCodes = ARAMEX_DELIVERY_ATTEMPT_CODES,
                collectionCodes = ARAMEX_COLLECTION_CODES,
                labelCreatedCodes = ARAMEX_LABEL_CREATED_CODES,
            )

            ParsedRefreshEvent(
                title = structuredTitle(
                    explicitTitle = scan.eventTitle,
                    fallbackStatus = status,
                ),
                description = scan.message,
                status = status,
                occurredAt = scan.occurredAt,
            )
        }

        val latestEvent = events.first()
        val etaEnd = parseAramexDate(result.stringOrNull("DeliveryETADate"), endOfDayIfDateOnly = true)
        return StructuredRefreshSnapshot(
            result = SyncResult.SUCCESS,
            status = latestEvent.status,
            message = latestEvent.description ?: latestEvent.title,
            events = events,
            deliveredAt = if (latestEvent.status == NormalizedStatus.DELIVERED) latestEvent.occurredAt else null,
            etaEnd = etaEnd,
            serviceName = resolveAramexServiceName(result),
            rawSummaryJson = result.toString(),
        )
    }
}

object AustraliaPostStructuredParser {
    fun parse(
        trackingNumber: String,
        rawResponseBody: String,
    ): StructuredRefreshSnapshot? {
        val root = runCatching {
            carrierJson.parseToJsonElement(rawResponseBody)
        }.getOrNull() ?: return null

        val shipments = findShipmentCandidates(root)
        if (shipments.isEmpty()) {
            return StructuredRefreshSnapshot(
                result = SyncResult.ERROR,
                status = NormalizedStatus.UNKNOWN,
                message = "Australia Post did not expose a shipment for $trackingNumber.",
                events = emptyList(),
                deliveredAt = null,
                etaEnd = null,
                serviceName = null,
                rawSummaryJson = rawResponseBody,
            )
        }

        val shipment = shipments.firstOrNull { shipment ->
            shipment.stringOrNull("consignmentId").equals(trackingNumber, ignoreCase = true) ||
                shipment.arrayOrNull("articles").orEmpty().any { article ->
                    (article as? JsonObject)?.stringOrNull("articleId").equals(trackingNumber, ignoreCase = true)
                }
        } ?: shipments.first()

        val article = shipment.arrayOrNull("articles").orEmpty()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.stringOrNull("articleId").equals(trackingNumber, ignoreCase = true) }
            ?: shipment.arrayOrNull("articles").orEmpty().mapNotNull { it as? JsonObject }.firstOrNull()

        if (article == null) {
            return StructuredRefreshSnapshot(
                result = SyncResult.ERROR,
                status = NormalizedStatus.UNKNOWN,
                message = "Australia Post returned a shipment container without article details.",
                events = emptyList(),
                deliveredAt = null,
                etaEnd = null,
                serviceName = null,
                rawSummaryJson = shipment.toString(),
            )
        }

        val events = article.arrayOrNull("events").orEmpty()
            .mapNotNull { parseAustraliaPostEvent(it as? JsonObject) }
            .take(MAX_STRUCTURED_EVENTS)

        val latestMilestone = listOfNotNull(
            shipment.objectOrNull("milestone")?.stringOrNull("name"),
            events.firstOrNull()?.milestone,
            article.arrayOrNull("milestones").orEmpty().firstNotNullOfOrNull { milestone ->
                (milestone as? JsonObject)?.stringOrNull("name")
            },
            article.stringOrNull("trackStatusOfArticle"),
        ).firstOrNull() ?: "Pending"

        val latestMessage = listOfNotNull(
            events.firstOrNull()?.description,
            latestMilestone,
        ).first()

        val latestStatus = mapStructuredStatus(
            primaryCode = null,
            message = listOfNotNull(latestMilestone, latestMessage).joinToString(" "),
            acceptedCodes = emptySet(),
            inTransitCodes = emptySet(),
            exceptionCodes = emptySet(),
            returnedCodes = emptySet(),
            deliveredCodes = emptySet(),
            outForDeliveryCodes = emptySet(),
            deliveryAttemptCodes = emptySet(),
            collectionCodes = emptySet(),
            labelCreatedCodes = emptySet(),
        )

        val parsedEvents = if (events.isNotEmpty()) {
            events.map { event ->
                val status = mapStructuredStatus(
                    primaryCode = null,
                    message = listOfNotNull(event.milestone, event.description, event.eventCode).joinToString(" "),
                    acceptedCodes = emptySet(),
                    inTransitCodes = emptySet(),
                    exceptionCodes = emptySet(),
                    returnedCodes = emptySet(),
                    deliveredCodes = emptySet(),
                    outForDeliveryCodes = emptySet(),
                    deliveryAttemptCodes = emptySet(),
                    collectionCodes = emptySet(),
                    labelCreatedCodes = emptySet(),
                )
                ParsedRefreshEvent(
                    title = structuredTitle(event.milestone, status),
                    description = buildList {
                        if (!event.description.isNullOrBlank()) add(event.description)
                        if (!event.location.isNullOrBlank()) add(event.location)
                    }.joinToString(" • ").ifBlank { null },
                    status = status,
                    occurredAt = normalizeEpoch(event.occurredAt),
                )
            }
        } else {
            listOf(
                ParsedRefreshEvent(
                    title = structuredTitle(latestMilestone, latestStatus),
                    description = latestMessage,
                    status = latestStatus,
                    occurredAt = null,
                )
            )
        }

        val etaEnd = article.objectOrNull("estimatedDeliveryDateRange")?.stringOrNull("toDate")
            ?.let { parseIsoDate(it, endOfDayIfDateOnly = true) }
            ?: article.stringOrNull("deliveredByDateISO")?.let { parseIsoDate(it, endOfDayIfDateOnly = true) }

        val deliveredAt = when {
            latestStatus == NormalizedStatus.DELIVERED -> parsedEvents.firstOrNull()?.occurredAt
            else -> null
        }

        return StructuredRefreshSnapshot(
            result = SyncResult.SUCCESS,
            status = latestStatus,
            message = parsedEvents.firstOrNull()?.description ?: parsedEvents.firstOrNull()?.title ?: latestMilestone,
            events = parsedEvents,
            deliveredAt = deliveredAt,
            etaEnd = etaEnd,
            serviceName = if ((shipment.longOrNull("articleCount") ?: 1L) > 1L) "Australia Post Consignment" else null,
            rawSummaryJson = shipment.toString(),
        )
    }
}

private val carrierJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private data class AramexScan(
    val statusCode: String?,
    val message: String?,
    val eventTitle: String?,
    val occurredAt: Long?,
)

private data class AustraliaPostEvent(
    val occurredAt: Long?,
    val description: String?,
    val location: String?,
    val eventCode: String?,
    val milestone: String?,
)

private fun parseAramexScan(element: JsonElement): AramexScan? {
    val scan = element as? JsonObject ?: return null
    val statusCode = scan.stringOrNull("Status") ?: scan.stringOrNull("StatusCode")
    val locationName = listOfNotNull(
        scan.stringOrNull("Location"),
        scan.stringOrNull("Name"),
        scan.stringOrNull("Franchise"),
    ).firstOrNull()
    val companyName = scan.objectOrNull("CompanyInfo")?.stringOrNull("contactName")
    val description = listOfNotNull(
        scan.stringOrNull("Description"),
        scan.stringOrNull("StatusDescription"),
        companyName,
        locationName,
    ).firstOrNull()

    if (statusCode == null && description == null) return null

    return AramexScan(
        statusCode = statusCode,
        message = buildList {
            if (!description.isNullOrBlank()) add(description)
            if (!locationName.isNullOrBlank() && locationName != description) add(locationName)
        }.distinct().joinToString(" • ").ifBlank { null },
        eventTitle = locationName,
        occurredAt = parseAramexDate(scan.stringOrNull("Date")),
    )
}

private fun parseAustraliaPostEvent(event: JsonObject?): AustraliaPostEvent? {
    if (event == null) return null
    return AustraliaPostEvent(
        occurredAt = event.longOrNull("dateTime"),
        description = event.stringOrNull("description"),
        location = event.stringOrNull("location"),
        eventCode = event.stringOrNull("eventCode"),
        milestone = event.stringOrNull("milestone"),
    )
}

private fun structuredTitle(
    explicitTitle: String?,
    fallbackStatus: NormalizedStatus,
): String {
    val trimmed = explicitTitle?.trim().orEmpty()
    return if (trimmed.isNotBlank()) trimmed else fallbackStatus.label
}

private fun mapStructuredStatus(
    primaryCode: String?,
    message: String?,
    acceptedCodes: Set<String>,
    inTransitCodes: Set<String>,
    exceptionCodes: Set<String>,
    returnedCodes: Set<String>,
    deliveredCodes: Set<String>,
    outForDeliveryCodes: Set<String>,
    deliveryAttemptCodes: Set<String>,
    collectionCodes: Set<String>,
    labelCreatedCodes: Set<String>,
): NormalizedStatus {
    val code = primaryCode?.trim()?.uppercase(Locale.ROOT)
    when {
        code in deliveredCodes -> return NormalizedStatus.DELIVERED
        code in outForDeliveryCodes -> return NormalizedStatus.OUT_FOR_DELIVERY
        code in deliveryAttemptCodes -> return NormalizedStatus.DELIVERY_ATTEMPTED
        code in collectionCodes -> return NormalizedStatus.AVAILABLE_FOR_COLLECTION
        code in returnedCodes -> return NormalizedStatus.RETURNED
        code in exceptionCodes -> return NormalizedStatus.EXCEPTION
        code in inTransitCodes -> return NormalizedStatus.IN_TRANSIT
        code in acceptedCodes -> return NormalizedStatus.ACCEPTED
        code in labelCreatedCodes -> return NormalizedStatus.LABEL_CREATED
    }

    val haystack = message.orEmpty().lowercase(Locale.ROOT)
    return when {
        listOf("delivered", "received by addressee", "successfully delivered").any { haystack.contains(it) } ->
            NormalizedStatus.DELIVERED
        listOf("coming today", "out for delivery", "with driver", "with courier for delivery").any { haystack.contains(it) } ->
            NormalizedStatus.OUT_FOR_DELIVERY
        listOf("attempted delivery", "delivery attempted", "unable to deliver").any { haystack.contains(it) } ->
            NormalizedStatus.DELIVERY_ATTEMPTED
        listOf("awaiting collection", "available for collection", "ready for collection", "collect").any { haystack.contains(it) } ->
            NormalizedStatus.AVAILABLE_FOR_COLLECTION
        listOf("customs", "clearance").any { haystack.contains(it) } ->
            NormalizedStatus.CUSTOMS_OR_CLEARANCE
        listOf("returned to sender", "returned", "return to sender").any { haystack.contains(it) } ->
            NormalizedStatus.RETURNED
        listOf("contact sender", "delay", "delayed", "exception", "problem", "hold").any { haystack.contains(it) } ->
            NormalizedStatus.EXCEPTION
        listOf("on its way", "in transit", "processed", "arrived at facility").any { haystack.contains(it) } ->
            NormalizedStatus.IN_TRANSIT
        listOf("we've got it", "we have got it", "accepted", "collected", "picked up").any { haystack.contains(it) } ->
            NormalizedStatus.ACCEPTED
        listOf("label created", "created by sender", "shipment created").any { haystack.contains(it) } ->
            NormalizedStatus.LABEL_CREATED
        else -> NormalizedStatus.UNKNOWN
    }
}

private fun findShipmentCandidates(root: JsonElement): List<JsonObject> {
    val candidates = mutableListOf<JsonObject>()

    fun visit(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                if (element.arrayOrNull("articles") != null) {
                    candidates += element
                }
                element.values.forEach(::visit)
            }

            is JsonArray -> element.forEach(::visit)
            else -> Unit
        }
    }

    visit(root)
    return candidates
}

private fun resolveAramexServiceName(result: JsonObject): String? {
    val serviceCode = result.objectOrNull("DeliveryServiceType")?.stringOrNull("ServiceCode")
    val baseName = when (serviceCode) {
        "STN" -> "Standard Service"
        "ATL" -> "Authority to Leave Service"
        "SGR" -> "Signature Service"
        "PIN" -> "Secure PIN Service"
        "CNC" -> "Click & Collect Service"
        else -> null
    }
    return when {
        result.booleanOrNull("IsPriorityParcel") == true && baseName != null -> "Priority $baseName"
        result.booleanOrNull("IsPriorityParcel") == true -> "Priority Delivery"
        else -> baseName
    }
}

private fun extractJsonObject(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
    val firstBrace = trimmed.indexOf('{')
    val lastBrace = trimmed.lastIndexOf('}')
    if (firstBrace < 0 || lastBrace <= firstBrace) return null
    return trimmed.substring(firstBrace, lastBrace + 1)
}

private fun parseAramexDate(
    rawValue: String?,
    endOfDayIfDateOnly: Boolean = false,
): Long? {
    val value = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val dateTimeFormatters = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", Locale.ENGLISH),
    )
    val dateOnlyFormatters = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
    )

    val dateTimeMatch = dateTimeFormatters.firstNotNullOfOrNull { formatter ->
        runCatching {
            LocalDateTime.parse(value, formatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
    if (dateTimeMatch != null) return dateTimeMatch

    return dateOnlyFormatters.firstNotNullOfOrNull { formatter ->
        runCatching {
            val date = LocalDate.parse(value, formatter)
            val zoned = if (endOfDayIfDateOnly) {
                date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1)
            } else {
                date.atStartOfDay(ZoneId.systemDefault())
            }
            zoned.toInstant().toEpochMilli()
        }.getOrNull()
    }
}

private fun parseIsoDate(
    rawValue: String,
    endOfDayIfDateOnly: Boolean = false,
): Long? {
    return runCatching { Instant.parse(rawValue).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(rawValue).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(rawValue)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
        ?: runCatching {
            val date = LocalDate.parse(rawValue)
            val zoned = if (endOfDayIfDateOnly) {
                date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1)
            } else {
                date.atStartOfDay(ZoneId.systemDefault())
            }
            zoned.toInstant().toEpochMilli()
        }.getOrNull()
}

private fun normalizeEpoch(value: Long?): Long? {
    val epoch = value ?: return null
    return if (epoch in 1..99_999_999_999L) epoch * 1000 else epoch
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull
        ?: stringOrNull(name)?.toLongOrNull()

private fun JsonObject.booleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull
        ?: stringOrNull(name)?.toBooleanStrictOrNull()

private fun JsonObject.objectOrNull(name: String): JsonObject? =
    this[name] as? JsonObject

private fun JsonObject.arrayOrNull(name: String): JsonArray? =
    this[name] as? JsonArray

private const val MAX_STRUCTURED_EVENTS = 6

private val ARAMEX_LABEL_CREATED_CODES = setOf(
    "ARP",
    "PP0",
    "PP1",
    "PP2",
    "PP3",
    "PP4",
    "PP5",
    "PP7",
    "PP8",
    "FUT",
    "EBA",
    "RL1",
    "OWD",
    "OWP",
    "PC1",
    "NOC",
    "NOH",
)

private val ARAMEX_ACCEPTED_CODES = setOf(
    "PPP",
    "WIN",
    "PIC",
    "AM1",
    "Y21",
)

private val ARAMEX_IN_TRANSIT_CODES = setOf(
    "CBR",
    "TVL",
    "BLT",
    "TMW",
    "TOO",
    "DPO",
    "GOU",
    "SYD",
    "MKY",
    "DUB",
    "UNW",
    "BRI",
    "MEL",
    "BDB",
    "NTH",
    "GLS",
    "PQQ",
    "OAG",
    "GFT",
    "CCT",
    "WGG",
    "LAU",
    "DAR",
    "GFF",
    "MYB",
    "DDY",
    "NWN",
    "SUN",
    "KLM",
    "PER",
    "YNG",
    "ADL",
    "WOL",
    "CAP",
    "GEE",
    "CFS",
    "NEW",
    "TAS",
    "GLD",
    "CNS",
    "ALB",
    "BAT",
    "BEN",
    "PRP",
    "TUL",
    "KNY",
    "RID",
    "RIA",
    "ONF",
    "MRP",
)

private val ARAMEX_DELIVERED_CODES = setOf(
    "YES",
    "PCY",
    "R34",
    "R35",
    "R36",
    "ATL",
    "LAI",
    "PRS",
    "DRU",
    "YEA",
    "ALL",
    "CLS",
    "SIG",
)

private val ARAMEX_OUT_FOR_DELIVERY_CODES = setOf(
    "ONB",
    "RUO",
)

private val ARAMEX_DELIVERY_ATTEMPT_CODES = setOf(
    "CAR",
    "CC1",
    "CC2",
    "CC3",
    "CC4",
    "CC5",
    "CC6",
    "CCL",
    "CCR",
    "CCQ",
)

private val ARAMEX_COLLECTION_CODES = setOf(
    "CCP",
    "CKN",
    "MDC",
)

private val ARAMEX_RETURNED_CODES = setOf(
    "RTS",
    "OW5",
    "PCR",
    "RSS",
)

private val ARAMEX_EXCEPTION_CODES = setOf(
    "OOT",
    "U19",
    "DLY",
    "PAC",
    "G13",
    "U01",
    "U02",
    "U03",
    "U04",
    "U05",
    "U06",
    "U07",
    "U08",
    "U09",
    "U10",
    "U11",
    "U12",
    "U13",
    "U14",
    "U15",
    "U16",
    "U38",
    "U42",
    "UA1",
    "UA2",
    "UA3",
    "UA4",
    "UAR",
    "MIS",
    "UNA",
    "UND",
    "DAM",
    "HAD",
    "HAM",
    "HAP",
    "B71",
    "B72",
    "B73",
    "B76",
    "B77",
    "B78",
    "DDF",
    "BBN",
    "B68",
    "BBS",
    "BDS",
    "C08",
    "KAN",
    "LCL",
    "LUN",
    "MSS",
    "MYL",
    "NBD",
    "O80",
    "O81",
    "O82",
    "O83",
    "OFO",
    "ON1",
    "ON2",
    "OO1",
    "OO2",
    "OO3",
    "OO4",
    "OO5",
    "OO6",
    "OO7",
    "OOA",
    "OOO",
    "OW4",
    "OWC",
    "OWF",
    "OWO",
    "OWR",
    "OWS",
    "OWT",
    "OWU",
    "OZS",
    "PC2",
    "PCK",
    "RL2",
    "SID",
    "SMR",
    "SMS",
    "TEM",
    "V41",
    "FD5",
)
