package com.parcelpanel.tracking

import com.google.common.truth.Truth.assertThat
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult
import org.junit.Test

class StructuredCarrierParsersTest {
    @Test
    fun aramexNoScans_isTreatedAsLabelCreated() {
        val snapshot = AramexAuTrackingParser.parse(
            trackingNumber = "MP0083215634",
            rawResponseBody = """({"error":"Label 'MP0083215634' does not have any scans.","generated_in":"0ms"});""",
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.result).isEqualTo(SyncResult.SUCCESS)
        assertThat(snapshot?.status).isEqualTo(NormalizedStatus.LABEL_CREATED)
        assertThat(snapshot?.message).contains("Waiting for the first Aramex scan")
    }

    @Test
    fun aramexStructuredPayload_mapsDeliveredStatusAndService() {
        val snapshot = AramexAuTrackingParser.parse(
            trackingNumber = "MP0099999999",
            rawResponseBody = """
                ({
                  "result": {
                    "Scans": [
                      {
                        "Status": "SIG",
                        "Date": "28/03/2026 14:20",
                        "Description": "Delivered to front door",
                        "Name": "Perth"
                      },
                      {
                        "Status": "ONB",
                        "Date": "28/03/2026 08:15",
                        "Description": "On board for delivery",
                        "Name": "Perth"
                      }
                    ],
                    "DeliveryETADate": "28/03/2026",
                    "DeliveryServiceType": {
                      "ServiceCode": "SGR"
                    }
                  }
                });
            """.trimIndent(),
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.result).isEqualTo(SyncResult.SUCCESS)
        assertThat(snapshot?.status).isEqualTo(NormalizedStatus.DELIVERED)
        assertThat(snapshot?.serviceName).isEqualTo("Signature Service")
        assertThat(snapshot?.events).hasSize(2)
        assertThat(snapshot?.events?.first()?.title).isEqualTo("Delivered to front door")
        assertThat(snapshot?.events?.first()?.location).isEqualTo("Perth")
        assertThat(snapshot?.deliveredAt).isNotNull()
        assertThat(snapshot?.etaEnd).isNotNull()
    }

    @Test
    fun aramexStructuredPayload_keepsFullTimelineBeyondSixEvents() {
        val scans = (1..8).joinToString(separator = ",") { index ->
            """
                {
                  "Status": "CBR",
                  "Date": "28/03/2026 ${"%02d".format(index)}:15",
                  "Description": "Checkpoint $index",
                  "Name": "Depot $index"
                }
            """.trimIndent()
        }

        val snapshot = AramexAuTrackingParser.parse(
            trackingNumber = "MP0011111111",
            rawResponseBody = """
                ({
                  "result": {
                    "Scans": [
                      $scans
                    ]
                  }
                });
            """.trimIndent(),
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.events).hasSize(8)
        assertThat(snapshot?.events?.last()?.title).isEqualTo("Checkpoint 8")
    }

    @Test
    fun australiaPostStructuredPayload_mapsDeliveredShipment() {
        val snapshot = AustraliaPostStructuredParser.parse(
            trackingNumber = "34CD91013562",
            rawResponseBody = """
                {
                  "shipments": [
                    {
                      "consignmentId": "34CD91013562",
                      "articleCount": 1,
                      "milestone": {
                        "name": "Delivered"
                      },
                      "articles": [
                        {
                          "articleId": "34CD91013562",
                          "trackStatusOfArticle": "Delivered",
                          "estimatedDeliveryDateRange": {
                            "fromDate": "2026-03-26",
                            "toDate": "2026-03-27"
                          },
                          "deliveredByDateISO": "2026-03-27",
                          "events": [
                            {
                              "dateTime": 1774610400000,
                              "description": "Delivered",
                              "location": "PERTH WA",
                              "eventCode": "DEL",
                              "milestone": "Delivered"
                            },
                            {
                              "dateTime": 1774581600000,
                              "description": "On board for delivery",
                              "location": "PERTH WA",
                              "eventCode": "OFD",
                              "milestone": "Coming today"
                            }
                          ],
                          "milestones": [
                            {
                              "name": "Delivered"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.result).isEqualTo(SyncResult.SUCCESS)
        assertThat(snapshot?.status).isEqualTo(NormalizedStatus.DELIVERED)
        assertThat(snapshot?.message).contains("Delivered")
        assertThat(snapshot?.events).hasSize(2)
        assertThat(snapshot?.events?.first()?.location).isEqualTo("PERTH WA")
        assertThat(snapshot?.deliveredAt).isEqualTo(1774610400000)
        assertThat(snapshot?.etaEnd).isNotNull()
    }

    @Test
    fun australiaPostStructuredPayload_keepsFullTimelineBeyondSixEvents() {
        val events = (1..7).joinToString(separator = ",") { index ->
            """
                {
                  "dateTime": ${1774500000000L + (index * 60_000L)},
                  "description": "Checkpoint $index",
                  "location": "Location $index",
                  "eventCode": "EV$index",
                  "milestone": "Milestone $index"
                }
            """.trimIndent()
        }

        val snapshot = AustraliaPostStructuredParser.parse(
            trackingNumber = "34CD91013562",
            rawResponseBody = """
                {
                  "shipments": [
                    {
                      "consignmentId": "34CD91013562",
                      "articleCount": 1,
                      "articles": [
                        {
                          "articleId": "34CD91013562",
                          "trackStatusOfArticle": "In transit",
                          "events": [
                            $events
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertThat(snapshot).isNotNull()
        assertThat(snapshot?.events).hasSize(7)
        assertThat(snapshot?.events?.last()?.location).isEqualTo("Location 7")
    }
}
