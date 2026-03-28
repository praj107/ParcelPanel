package com.parcelpanel.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CarrierDetectorTest {
    @Test
    fun detectsUpsOneZFormat() {
        val match = CarrierDetector.detect("1Z999AA10123456784").first()

        assertThat(match.slug).isEqualTo("ups")
        assertThat(match.confidence).isAtLeast(90)
    }

    @Test
    fun detectsAustraliaPostArticleId() {
        val matches = CarrierDetector.detect("AB123456789AU")

        assertThat(matches.first().slug).isEqualTo("auspost")
        assertThat(matches.map { it.slug }).contains("startrack")
    }

    @Test
    fun detectsCouriersPleasePrefix() {
        val match = CarrierDetector.detect("CPWBUK00006643702").first()

        assertThat(match.slug).isEqualTo("couriersplease")
    }

    @Test
    fun explicitTeamGlobalExpressPrefixBeatsGenericFreightPattern() {
        val match = CarrierDetector.detect("TGE123456789012").first()

        assertThat(match.slug).isEqualTo("teamge")
        assertThat(match.confidence).isAtLeast(90)
    }

    @Test
    fun elevenDigitAramexConsignmentBeatsGenericFallbacks() {
        val match = CarrierDetector.detect("35672988035").first()

        assertThat(match.slug).isEqualTo("aramex")
        assertThat(match.confidence).isAtLeast(80)
    }

    @Test
    fun detectsAramexMpLabelFormat() {
        val match = CarrierDetector.detect("MP0083215634").first()

        assertThat(match.slug).isEqualTo("aramex")
        assertThat(match.confidence).isAtLeast(95)
    }

    @Test
    fun detectsAustraliaPostConsumerTrackingFormat() {
        val matches = CarrierDetector.detect("34CD91013562")

        assertThat(matches.first().slug).isEqualTo("auspost")
        assertThat(matches.map { it.slug }).contains("startrack")
    }
}
