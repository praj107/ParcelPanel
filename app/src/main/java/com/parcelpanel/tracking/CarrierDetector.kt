package com.parcelpanel.tracking

import com.parcelpanel.model.CarrierMatch

object CarrierDetector {
    fun detect(raw: String): List<CarrierMatch> {
        val candidate = raw.trim().uppercase()
        if (candidate.isBlank()) return emptyList()

        val matches = buildList {
            when {
                Regex("^1Z[0-9A-Z]{16}$").matches(candidate) -> {
                    add(match("ups", 98, "Classic UPS 1Z format"))
                }
                Regex("^[A-Z]{2}[0-9]{9}AU$").matches(candidate) -> {
                    add(match("auspost", 95, "Australia Post article ID format"))
                    add(match("startrack", 78, "StarTrack often shares AusPost article patterns"))
                }
                Regex("^CP[0-9A-Z]{8,}$").matches(candidate) -> {
                    add(match("couriersplease", 96, "CouriersPlease public tracking prefix"))
                }
                Regex("^(JJD|JD)[0-9A-Z]{10,}$").matches(candidate) -> {
                    add(match("dhl", 94, "DHL parcel prefix"))
                }
                Regex("^[0-9]{10}$").matches(candidate) -> {
                    add(match("dhl", 72, "10-digit format is common for DHL tracking"))
                    add(match("aramex", 55, "Also plausible for Aramex consignments"))
                }
                Regex("^[0-9]{11}$").matches(candidate) -> {
                    add(match("aramex", 82, "11-digit format is common on Aramex public tracking examples"))
                    add(match("dhl", 58, "DHL can also present shorter numeric identifiers"))
                }
                Regex("^[0-9]{12}$").matches(candidate) -> {
                    add(match("fedex", 82, "12-digit format is common for FedEx/TNT"))
                    add(match("aramex", 60, "Aramex can also use 12-digit consignments"))
                }
                Regex("^[0-9]{15}$").matches(candidate) -> {
                    add(match("fedex", 90, "15-digit format is strongly associated with FedEx"))
                }
                Regex("^[0-9]{16,18}$").matches(candidate) -> {
                    add(match("aramex", 70, "Long numeric consignment fits Aramex"))
                    add(match("fedex", 62, "FedEx also uses long numeric identifiers"))
                    add(match("teamge", 48, "Domestic freight carriers sometimes use long numeric IDs"))
                }
                Regex("^TGE[0-9]{8,}$").matches(candidate) -> {
                    add(match("teamge", 92, "Explicit Team Global Express prefix"))
                }
                Regex("^[A-Z]{3}[0-9]{8,}$").matches(candidate) -> {
                    add(match("directfreight", 64, "Consignment code resembles domestic road-freight formatting"))
                    add(match("teamge", 50, "Alphanumeric freight format"))
                }
            }

            if (matchesNoneFor("auspost") && candidate.endsWith("AU") && candidate.length in 11..16) {
                add(match("auspost", 62, "Australian postal suffix"))
            }
        }

        return (matches + fallbackMatches(candidate))
            .distinctBy { it.slug }
            .sortedByDescending { it.confidence }
    }

    private fun MutableList<CarrierMatch>.matchesNoneFor(slug: String): Boolean =
        none { it.slug == slug }

    private fun match(slug: String, confidence: Int, reason: String): CarrierMatch {
        val definition = CarrierCatalog.bySlug(slug) ?: error("Unknown carrier slug: $slug")
        return CarrierMatch(
            slug = slug,
            displayName = definition.displayName,
            confidence = confidence,
            reason = reason
        )
    }

    private fun fallbackMatches(candidate: String): List<CarrierMatch> {
        if (candidate.length < 8) return emptyList()
        return listOf(
            match("auspost", 34, "Australian carrier fallback"),
            match("couriersplease", 32, "Australian parcel network fallback"),
            match("directfreight", 30, "Domestic road-express fallback"),
        )
    }
}
