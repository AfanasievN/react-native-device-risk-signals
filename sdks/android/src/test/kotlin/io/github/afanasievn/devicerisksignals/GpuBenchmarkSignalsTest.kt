package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GpuBenchmarkSignalsTest {
  @Test fun unavailableFieldsAreOmitted() {
    assertEquals(emptyMap<String, Any>(), GpuBenchmarkSignals().toRawMap())
    assertEquals(mapOf("benchmarkPerformed" to false, "skippedReason" to "emulator"),
      GpuBenchmarkSignals(benchmarkPerformed = false, skippedReason = "emulator").toRawMap())
  }

  @Test fun everyRawFieldPreservesItsValueAndType() {
    assertEquals(mapOf(
      "rendererName" to "renderer", "vendorName" to "vendor", "apiVersion" to "ES 2",
      "shadingLanguageVersion" to "GLSL", "maxTextureSize" to 4096,
      "benchmarkPerformed" to true, "drawCallsCompleted" to 0, "durationMs" to 50,
      "operationTimeP50Ms" to 1.0, "operationTimeP95Ms" to 2.0,
      "operationTimeMadMs" to 0.0, "operationTimeCoefficientOfVariation" to 0.5,
      "warmupSlope" to -0.2, "skippedReason" to "error",
    ), GpuBenchmarkSignals(
      rendererName = "renderer", vendorName = "vendor", apiVersion = "ES 2",
      shadingLanguageVersion = "GLSL", maxTextureSize = 4096, benchmarkPerformed = true,
      drawCallsCompleted = 0, durationMs = 50, operationTimeP50Ms = 1.0,
      operationTimeP95Ms = 2.0, operationTimeMadMs = 0.0,
      operationTimeCoefficientOfVariation = 0.5, warmupSlope = -0.2, skippedReason = "error",
    ).toRawMap())
  }

  @Test fun unsupportedAfterIdentityRetainsIdentityWithoutInventingTiming() {
    val map = GpuBenchmarkSignals(rendererName = "renderer")
      .copy(benchmarkPerformed = false, skippedReason = "unsupported").toRawMap()
    assertEquals("renderer", map["rendererName"])
    assertEquals(false, map["benchmarkPerformed"])
    assertFalse(map.containsKey("durationMs"))
    assertFalse(map.containsKey("operationTimeP50Ms"))
  }
}
