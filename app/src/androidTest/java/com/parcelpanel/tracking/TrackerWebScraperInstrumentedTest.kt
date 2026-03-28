package com.parcelpanel.tracking
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.parcelpanel.model.NormalizedStatus
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerWebScraperInstrumentedTest {
    @Test
    fun fetch_aramexPublicTracker_extractsLiveStatus() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val scraper = TrackerWebScraper(context)
        val trackingNumber = "35672988035"
        val trackerUrl = CarrierCatalog.bySlug("aramex")!!.trackingUrl(trackingNumber)!!

        val document = scraper.fetch(trackerUrl, trackingNumber)
        val snapshot = TrackerTextParser.parse(
            carrierSlug = "aramex",
            trackingNumber = trackingNumber,
            document = document,
        )

        assertThat(document.finalUrl).contains("aramex.com")
        assertThat(document.bodyText.lowercase()).contains("aramex")
        assertThat(snapshot).isNotNull()
        assertThat(snapshot!!.status).isNotEqualTo(NormalizedStatus.UNKNOWN)
        assertThat(snapshot.message).isNotEmpty()
        assertThat(snapshot.events).isNotEmpty()
    }
}
