package com.parcelpanel.tracking

import com.parcelpanel.model.NormalizedStatus
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedRefreshEvent(
    val title: String,
    val description: String?,
    val status: NormalizedStatus,
    val occurredAt: Long?,
)

data class ParsedTrackingSnapshot(
    val status: NormalizedStatus,
    val message: String,
    val events: List<ParsedRefreshEvent>,
    val deliveredAt: Long?,
    val etaEnd: Long?,
    val rawSummaryText: String,
)

object TrackerTextParser {
    fun parse(
        carrierSlug: String,
        trackingNumber: String,
        document: TrackerPageDocument,
    ): ParsedTrackingSnapshot? {
        val bodyText = normalizeVisibleText(document.bodyText)
        if (bodyText.isBlank()) return null
        if (looksBlocked(bodyText)) return null

        val relevantText = relevantText(bodyText, trackingNumber)
        if (looksNotFound(relevantText)) {
            return ParsedTrackingSnapshot(
                status = NormalizedStatus.UNKNOWN,
                message = "Carrier tracker did not find an active shipment for $trackingNumber.",
                events = emptyList(),
                deliveredAt = null,
                etaEnd = null,
                rawSummaryText = relevantText,
            )
        }

        val extractedMessage = when (carrierSlug) {
            "aramex" -> extractAramexLatestUpdate(relevantText)
            else -> null
        } ?: extractGenericUpdateMessage(relevantText)

        val detectedStatus = detectStatus(relevantText, extractedMessage)
        val occurredAt = extractTimestamp(relevantText)
        val eventTitle = statusTitle(detectedStatus)
        if (detectedStatus == NormalizedStatus.UNKNOWN && extractedMessage == null) {
            return null
        }

        val message = extractedMessage ?: eventTitle
        return ParsedTrackingSnapshot(
            status = detectedStatus,
            message = message,
            events = listOf(
                ParsedRefreshEvent(
                    title = eventTitle,
                    description = message,
                    status = detectedStatus,
                    occurredAt = occurredAt,
                )
            ),
            deliveredAt = if (detectedStatus == NormalizedStatus.DELIVERED) occurredAt else null,
            etaEnd = null,
            rawSummaryText = relevantText,
        )
    }

    private fun normalizeVisibleText(raw: String): String =
        raw.replace(Regex("[\\u0000-\\u001F]+"), "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")

    private fun relevantText(bodyText: String, trackingNumber: String): String {
        val canonical = bodyText.replace(Regex("\\s+"), " ").trim()
        val trackingIndex = canonical.indexOf(trackingNumber, ignoreCase = true)
        if (trackingIndex < 0) return canonical.take(MAX_CONTEXT_LENGTH)

        val start = (trackingIndex - CONTEXT_PADDING).coerceAtLeast(0)
        val end = (trackingIndex + CONTEXT_PADDING).coerceAtMost(canonical.length)
        return canonical.substring(start, end)
    }

    private fun looksBlocked(text: String): Boolean {
        val blockedMarkers = listOf(
            "access denied",
            "you don't have permission",
            "captcha",
            "verify you are human",
            "bot",
        )
        return blockedMarkers.any { marker -> text.contains(marker, ignoreCase = true) }
    }

    private fun looksNotFound(text: String): Boolean {
        val notFoundMarkers = listOf(
            "no results found",
            "could not find",
            "unable to locate",
            "enter a different tracking number",
        )
        return notFoundMarkers.any { marker -> text.contains(marker, ignoreCase = true) }
    }

    private fun extractAramexLatestUpdate(text: String): String? {
        val fullMatch = Regex(
            "(?i)latest update\\s+(.+?)\\s+([0-9]{1,2}\\s+[A-Za-z]{3}\\s+[0-9]{2,4}\\s+[0-9]{2}:[0-9]{2})"
        ).find(text)
        if (fullMatch != null) {
            return fullMatch.groupValues[1].trim()
        }

        val partialMatch = Regex(
            "(?i)latest update\\s+(.+?)(?=\\s+(origin|destination|created|collected|departed|in transit|arrived|out for delivery|delivered)\\b)"
        ).find(text)
        return partialMatch?.groupValues?.get(1)?.trim()
    }

    private fun extractGenericUpdateMessage(text: String): String? {
        val phrases = listOf(
            "out for delivery",
            "delivery attempted",
            "available for collection",
            "delivered",
            "in transit",
            "shipment information received",
            "label created",
            "collected",
            "accepted",
            "customs",
            "clearance",
            "delay",
            "exception",
            "returned",
        )
        val lowerText = text.lowercase(Locale.ROOT)
        val matchedPhrase = phrases.firstOrNull { phrase -> lowerText.contains(phrase) } ?: return null
        val startIndex = lowerText.indexOf(matchedPhrase).coerceAtLeast(0)
        return text.substring(startIndex)
            .take(MAX_MESSAGE_LENGTH)
            .trim()
            .trimEnd('.', ',', ';')
    }

    private fun detectStatus(text: String, message: String?): NormalizedStatus {
        val messageStatus = message?.let(::detectStatusFromHaystack)
        if (messageStatus != null && messageStatus != NormalizedStatus.UNKNOWN) {
            return messageStatus
        }
        return detectStatusFromHaystack(text)
    }

    private fun detectStatusFromHaystack(source: String): NormalizedStatus {
        val haystack = source.lowercase(Locale.ROOT)
        return when {
            listOf("delivered", "received by consignee", "successfully delivered").any { haystack.contains(it) } ->
                NormalizedStatus.DELIVERED
            listOf("out for delivery", "delivery champion has the shipment", "with courier for delivery").any { haystack.contains(it) } ->
                NormalizedStatus.OUT_FOR_DELIVERY
            listOf("delivery attempted", "unable to deliver", "attempted delivery").any { haystack.contains(it) } ->
                NormalizedStatus.DELIVERY_ATTEMPTED
            listOf("available for collection", "ready for pickup", "collect from depot").any { haystack.contains(it) } ->
                NormalizedStatus.AVAILABLE_FOR_COLLECTION
            listOf("customs", "clearance").any { haystack.contains(it) } ->
                NormalizedStatus.CUSTOMS_OR_CLEARANCE
            listOf("returned to sender", "shipment returned", "returned").any { haystack.contains(it) } ->
                NormalizedStatus.RETURNED
            listOf("exception", "delay", "on hold", "delivery issue").any { haystack.contains(it) } ->
                NormalizedStatus.EXCEPTION
            listOf("in transit", "departed", "arrived at destination", "arrived at facility").any { haystack.contains(it) } ->
                NormalizedStatus.IN_TRANSIT
            listOf("collected", "picked up", "accepted").any { haystack.contains(it) } ->
                NormalizedStatus.ACCEPTED
            listOf("label created", "shipment information received", "shipment created").any { haystack.contains(it) } ->
                NormalizedStatus.LABEL_CREATED
            else -> NormalizedStatus.UNKNOWN
        }
    }

    private fun statusTitle(status: NormalizedStatus): String = when (status) {
        NormalizedStatus.DELIVERED -> "Delivered"
        NormalizedStatus.OUT_FOR_DELIVERY -> "Out for delivery"
        NormalizedStatus.DELIVERY_ATTEMPTED -> "Delivery attempted"
        NormalizedStatus.AVAILABLE_FOR_COLLECTION -> "Collect from depot"
        NormalizedStatus.CUSTOMS_OR_CLEARANCE -> "Customs / clearance"
        NormalizedStatus.RETURNED -> "Returned"
        NormalizedStatus.EXCEPTION -> "Delivery exception"
        NormalizedStatus.IN_TRANSIT -> "In transit"
        NormalizedStatus.ACCEPTED -> "Accepted"
        NormalizedStatus.LABEL_CREATED -> "Label created"
        NormalizedStatus.UNKNOWN -> "Latest update"
        NormalizedStatus.CANCELLED -> "Cancelled"
    }

    private fun extractTimestamp(text: String): Long? {
        val match = DATE_TIME_PATTERN.find(text) ?: return null
        val timestamp = match.value.replace(Regex("\\s+"), " ").trim()
        val formatters = listOf(
            DateTimeFormatter.ofPattern("d MMM yy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH),
        )
        return formatters.firstNotNullOfOrNull { formatter ->
            runCatching {
                LocalDateTime.parse(timestamp, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
    }

    private val DATE_TIME_PATTERN =
        Regex("\\b[0-9]{1,2}\\s+[A-Za-z]{3}\\s+[0-9]{2,4}\\s+[0-9]{2}:[0-9]{2}\\b")

    private const val CONTEXT_PADDING = 1400
    private const val MAX_CONTEXT_LENGTH = 2400
    private const val MAX_MESSAGE_LENGTH = 220
}
