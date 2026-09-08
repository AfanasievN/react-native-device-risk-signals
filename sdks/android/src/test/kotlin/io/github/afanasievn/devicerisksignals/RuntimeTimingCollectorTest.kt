package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RuntimeTimingCollectorTest {
  @Test
  fun `collects positive clock intervals using the shared field names`() {
    var time = 0L
    val raw = RuntimeTimingCollector { time.also { time += 10L } }.collect().toRawMap()
    assertEquals("elapsed_realtime_nanos", raw["nativeClockSource"])
    assertEquals(256, raw["nativeSampleCount"])
    assertEquals(10.0, raw["nativeTimerResolutionNs"])
    assertEquals(10.0, raw["nativeIntervalMedianNs"])
    assertEquals(10.0, raw["nativeIntervalP95Ns"])
    assertEquals(0.0, raw["nativeIntervalMadNs"])
  }

  @Test
  fun `unavailable measurements are omitted when the clock does not advance`() {
    val raw = RuntimeTimingCollector { 42L }.collect().toRawMap()
    assertEquals(0, raw["nativeSampleCount"])
    assertFalse(raw.containsKey("nativeTimerResolutionNs"))
    assertFalse(raw.containsKey("nativeIntervalMedianNs"))
    assertFalse(raw.containsKey("nativeIntervalP95Ns"))
    assertFalse(raw.containsKey("nativeIntervalMadNs"))
  }

  @Test(expected = IllegalStateException::class)
  fun `clock failures propagate to the caller without fabricated data`() {
    RuntimeTimingCollector { throw IllegalStateException("unavailable") }.collect()
  }
}
