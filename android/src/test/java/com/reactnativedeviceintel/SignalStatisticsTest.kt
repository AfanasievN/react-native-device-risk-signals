package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalStatisticsTest {
  @Test
  fun `computes explainable robust timing aggregates`() {
    val result = SignalStatistics.summarize(listOf(1.0, 2.0, 3.0, 4.0, 100.0))!!

    assertEquals(3.0, result.median, 0.0001)
    assertEquals(100.0, result.p95, 0.0001)
    assertEquals(1.0, result.mad, 0.0001)
    assertEquals(5, result.sampleCount)
  }

  @Test
  fun `warmup slope compares first and second half without dividing by zero`() {
    assertEquals(-0.5, SignalStatistics.warmupSlope(listOf(4.0, 4.0, 2.0, 2.0))!!, 0.0001)
    assertEquals(null, SignalStatistics.warmupSlope(listOf(0.0, 0.0, 1.0, 1.0)))
  }
}
