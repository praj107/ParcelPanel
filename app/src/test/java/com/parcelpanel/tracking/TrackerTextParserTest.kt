package com.parcelpanel.tracking

import com.google.common.truth.Truth.assertThat
import com.parcelpanel.model.NormalizedStatus
import org.junit.Test

class TrackerTextParserTest {
    @Test
    fun parse_aramexLatestUpdate_extractsStatusMessageAndTimestamp() {
        val snapshot = TrackerTextParser.parse(
            carrierSlug = "aramex",
            trackingNumber = "ARAMEX",
            document = TrackerPageDocument(
                pageTitle = "Track Results",
                finalUrl = "https://www.aramex.com/au/en/track/results?ShipmentNumber=ARAMEX&source=aramex",
                bodyText = """
                    Track Shipments
                    Local Shipment
                    Shipment Number
                    ARAMEX
                    Latest Update
                    An Aramex Delivery Champion has the shipment and is expected to reach the customer's doorstep shortly
                    12 Sep 25 05:56
                    Origin
                    Destination
                    Created
                    Collected
                    Departed
                    In transit
                    Arrived at destination
                    Out for delivery
                    Destination
                    Delivered
                """.trimIndent(),
            ),
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.status).isEqualTo(NormalizedStatus.OUT_FOR_DELIVERY)
        assertThat(snapshot?.message).contains("Aramex Delivery Champion")
        assertThat(snapshot?.events).hasSize(1)
        assertThat(snapshot?.events?.first()?.occurredAt).isNotNull()
    }

    @Test
    fun parse_notFoundText_returnsUnknownMessage() {
        val snapshot = TrackerTextParser.parse(
            carrierSlug = "aramex",
            trackingNumber = "123456",
            document = TrackerPageDocument(
                pageTitle = "Track Results",
                finalUrl = "https://www.aramex.com/au/en/track/results?ShipmentNumber=123456&source=aramex",
                bodyText = """
                    Track Shipments
                    No results found with current selection, please enter a different tracking number.
                """.trimIndent(),
            ),
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.status).isEqualTo(NormalizedStatus.UNKNOWN)
        assertThat(snapshot?.message).contains("did not find")
    }

    @Test
    fun parse_genericDeliveredMessage_detectsDelivered() {
        val snapshot = TrackerTextParser.parse(
            carrierSlug = "ups",
            trackingNumber = "1Z999AA10123456784",
            document = TrackerPageDocument(
                pageTitle = "UPS Tracking",
                finalUrl = "https://www.ups.com/track?tracknum=1Z999AA10123456784",
                bodyText = """
                    Tracking details for 1Z999AA10123456784
                    Delivered
                    Package was delivered to the front door
                    10 Mar 2026 14:20
                """.trimIndent(),
            ),
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.status).isEqualTo(NormalizedStatus.DELIVERED)
        assertThat(snapshot?.deliveredAt).isNotNull()
    }
}
