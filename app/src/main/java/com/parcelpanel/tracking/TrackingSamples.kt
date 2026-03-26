package com.parcelpanel.tracking

data class TrackingSample(
    val carrierSlug: String,
    val trackingNumber: String,
    val label: String,
)

object TrackingSamples {
    val formatSamples: List<TrackingSample> = listOf(
        TrackingSample(
            carrierSlug = "auspost",
            trackingNumber = "AB123456789AU",
            label = "Australia Post",
        ),
        TrackingSample(
            carrierSlug = "ups",
            trackingNumber = "1Z999AA10123456784",
            label = "UPS",
        ),
        TrackingSample(
            carrierSlug = "couriersplease",
            trackingNumber = "CPWBUK00006643702",
            label = "CouriersPlease",
        ),
        TrackingSample(
            carrierSlug = "dhl",
            trackingNumber = "JJD1234567890123456",
            label = "DHL",
        ),
        TrackingSample(
            carrierSlug = "fedex",
            trackingNumber = "123456789012345",
            label = "FedEx / TNT",
        ),
        TrackingSample(
            carrierSlug = "directfreight",
            trackingNumber = "ABC12345678",
            label = "Direct Freight Express",
        ),
        TrackingSample(
            carrierSlug = "teamge",
            trackingNumber = "TGE123456789012",
            label = "Team Global Express",
        ),
        TrackingSample(
            carrierSlug = "aramex",
            trackingNumber = "3412345678901234",
            label = "Aramex Australia",
        ),
    )
}
