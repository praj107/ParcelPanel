package com.parcelpanel.tracking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.SyncResult
import com.parcelpanel.model.SyncTrigger
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerWebScraperInstrumentedTest {
    @Ignore("Manual smoke test against live carrier tracker surfaces.")
    @Test
    fun manualSmoke_aramexLiveNoScan_mapsStructuredLabelCreated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val connector = ConnectorRegistry(context).connectorFor("aramex")!!

        val envelope = connector.refresh(
            RefreshRequest(
                trackingNumber = "MP0083215634",
                currentStatus = NormalizedStatus.UNKNOWN,
                trigger = SyncTrigger.MANUAL_REFRESH,
            )
        )

        assertThat(envelope.result).isEqualTo(SyncResult.SUCCESS)
        assertThat(envelope.normalizedStatus).isEqualTo(NormalizedStatus.LABEL_CREATED)
        assertThat(envelope.message).contains("Waiting for the first Aramex scan")
    }

    @Ignore("Manual smoke test against live carrier tracker surfaces.")
    @Test
    fun manualSmoke_auspostLiveDelivery_returnsStructuredOrManualSessionOutcome() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val connector = ConnectorRegistry(context).connectorFor("auspost")!!

        val envelope = connector.refresh(
            RefreshRequest(
                trackingNumber = "34CD91013562",
                currentStatus = NormalizedStatus.UNKNOWN,
                trigger = SyncTrigger.MANUAL_REFRESH,
            )
        )

        assertThat(envelope.message).isNotEmpty()
        assertThat(envelope.result).isAnyOf(
            SyncResult.SUCCESS,
            SyncResult.SKIPPED,
            SyncResult.SEE_EXTERNAL_TRACKER,
        )
    }
}
