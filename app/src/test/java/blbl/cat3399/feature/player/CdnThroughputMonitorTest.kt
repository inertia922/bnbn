package blbl.cat3399.feature.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CdnThroughputMonitorTest {
    private fun monitor() =
        CdnThroughputMonitor(
            thresholdBytesPerSecond = 1_000L,
            windowMs = 1_000L,
            idleResetMs = 2_000L,
            slowWindowsToSwitch = 2,
        )

    @Test
    fun fastCdnNeverSwitches() {
        val monitor = monitor()
        assertFalse(monitor.record(100_000, 1_000L))
        assertFalse(monitor.record(100_000, 2_000L))
        assertFalse(monitor.record(100_000, 3_000L))
    }

    @Test
    fun twoSlowWindowsTriggerSwitch() {
        val monitor = monitor()
        assertFalse(monitor.record(100, 1_000L))
        assertFalse(monitor.record(100, 2_000L))
        assertTrue(monitor.record(100, 3_000L))
    }

    @Test
    fun idleGapDoesNotCountAsSlow() {
        val monitor = monitor()
        assertFalse(monitor.record(100, 1_000L))
        assertFalse(monitor.record(100, 2_000L))
        // No reads for a long time, then a normal read: the idle gap is ignored.
        assertFalse(monitor.record(100, 20_000L))
        assertFalse(monitor.record(100_000, 21_000L))
    }
}
