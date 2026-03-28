package com.parcelpanel.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleaseVersionComparatorTest {
    @Test
    fun compare_ordersSemanticVersions() {
        assertThat(ReleaseVersionComparator.compare("1.0.1", "1.0.0")).isGreaterThan(0)
        assertThat(ReleaseVersionComparator.compare("v2.0.0", "1.9.9")).isGreaterThan(0)
        assertThat(ReleaseVersionComparator.compare("1.0.0-beta1", "1.0.0")).isEqualTo(0)
        assertThat(ReleaseVersionComparator.compare("1.0", "1.0.0")).isEqualTo(0)
        assertThat(ReleaseVersionComparator.compare("1.2.0", "1.10.0")).isLessThan(0)
    }

    @Test
    fun isNewer_ignoresPrefixAndBuildMetadata() {
        assertThat(ReleaseVersionComparator.isNewer("v1.0.1+12", "1.0.0")).isTrue()
        assertThat(ReleaseVersionComparator.isNewer("1.0.0", "1.0.0")).isFalse()
    }
}
