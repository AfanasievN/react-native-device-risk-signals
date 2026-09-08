package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioLatencySignalsTest {
  @Test fun validPropertiesExposeNativeValuesAndEstimate() {
    assertEquals(
      mapOf("framesPerBuffer" to 240, "nativeSampleRate" to 48000,
        "outputLatencyMs" to 5.0, "measured" to true),
      AudioLatencyCollector.fromProperties("240", "48000").toRawMap(),
    )
  }

  @Test fun unavailableAndMalformedPropertiesAreOmitted() {
    assertEquals(mapOf("measured" to false), AudioLatencyCollector.fromProperties(null, null).toRawMap())
    assertEquals(mapOf("measured" to false), AudioLatencyCollector.fromProperties("oops", "999999999999").toRawMap())
  }

  @Test fun partialReadingStillCountsAsMeasuredWithoutEstimate() {
    assertEquals(mapOf("framesPerBuffer" to 240, "measured" to true),
      AudioLatencyCollector.fromProperties("240", null).toRawMap())
    assertEquals(mapOf("nativeSampleRate" to 48000, "measured" to true),
      AudioLatencyCollector.fromProperties(null, "48000").toRawMap())
  }

  @Test fun nonPositiveRateOmitsEstimateAndRawValuesArePreserved() {
    for (rate in listOf("0", "-1")) {
      assertEquals(mapOf("framesPerBuffer" to -10, "nativeSampleRate" to rate.toInt(), "measured" to true),
        AudioLatencyCollector.fromProperties("-10", rate).toRawMap())
    }
  }
}
