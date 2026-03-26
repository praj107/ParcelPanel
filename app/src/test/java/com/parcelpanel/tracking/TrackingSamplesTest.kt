package com.parcelpanel.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackingSamplesTest {
    @Test
    fun formatSamplesMapToExpectedPrimaryCarrier() {
        TrackingSamples.formatSamples.forEach { sample ->
            val detected = CarrierDetector.detect(sample.trackingNumber).firstOrNull()

            assertThat(detected).isNotNull()
            assertThat(detected?.slug).isEqualTo(sample.carrierSlug)
        }
    }
}
