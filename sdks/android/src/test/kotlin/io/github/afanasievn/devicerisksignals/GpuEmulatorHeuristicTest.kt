package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuEmulatorHeuristicTest {
  @Test fun matchesKnownEmulatorBuildStrings() {
    assertTrue(GpuEmulatorHeuristic.isLikelyEmulator("generic/sdk_gphone64", "Pixel 9", "ranchu"))
    assertTrue(GpuEmulatorHeuristic.isLikelyEmulator("brand/release", "Android SDK built for arm64", "vendor"))
    assertTrue(GpuEmulatorHeuristic.isLikelyEmulator("brand/release", "Model", "goldfish"))
    assertTrue(GpuEmulatorHeuristic.isLikelyEmulator("brand/release", "Model", "vbox86"))
  }

  @Test fun doesNotMatchOrdinaryHardwareOrMissingBuildStrings() {
    assertFalse(GpuEmulatorHeuristic.isLikelyEmulator("vendor/user/release-keys", "SM-A155F", "mt6835"))
    assertFalse(GpuEmulatorHeuristic.isLikelyEmulator(null, null, null))
  }

  @Test fun emulatorSkipReturnsBeforeAnyGpuWork() {
    // A worker-thread call on an emulator must resolve from the heuristic alone; no EGL class is
    // touched, which is why this runs as a JVM test without an Android runtime.
    val signals = GpuBenchmarkCollector(emulatorObserved = true, mainThread = { false }).collect()
    assertEquals(mapOf("benchmarkPerformed" to false, "skippedReason" to "emulator"), signals.toRawMap())
  }

  @Test fun mainThreadCallIsRejectedBeforeTheEmulatorDecision() {
    assertThrows(IllegalStateException::class.java) {
      GpuBenchmarkCollector(emulatorObserved = true, mainThread = { true }).collect()
    }
  }
}
