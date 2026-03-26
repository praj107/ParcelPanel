package com.parcelpanel.tracking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CarrierCatalogTest {
    @Test
    fun allLaunchCarriersHaveTrackerLinks() {
        val tierOne = CarrierCatalog.all.filter { it.supportTier == 1 }

        assertThat(tierOne).isNotEmpty()
        assertThat(tierOne.all { !it.trackerUrlTemplate.isNullOrBlank() }).isTrue()
    }

    @Test
    fun trackingUrlInterpolatesNumber() {
        val url = CarrierCatalog.bySlug("ups")?.trackingUrl("1Z999AA10123456784")

        assertThat(url).contains("1Z999AA10123456784")
    }
}

