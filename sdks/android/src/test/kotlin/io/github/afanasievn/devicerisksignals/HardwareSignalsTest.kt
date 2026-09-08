package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareSignalsTest {
  @Test
  fun `unavailable hardware and fonts produce empty observations`() {
    assertTrue(HardwareSignals().toRawMap().isEmpty())
    assertTrue(FontsSignals().toRawMap().isEmpty())
  }

  @Test
  fun `observed false zero and numeric types survive serialization`() {
    val signals = HardwareSignals(
      screenWidthPx = 1080, screenDensity = 2.5, batteryLevel = 0.0,
      batteryPresent = false, lowPowerModeEnabled = false,
      processResidentMemoryBytes = 4096.0, batteryCycleCount = 0.0,
      cpuArchitecture = "aarch64", powerSource = "battery",
    )
    assertEquals(mapOf<String, Any>(
      "screenWidthPx" to 1080, "screenDensity" to 2.5, "batteryLevel" to 0.0,
      "batteryPresent" to false, "lowPowerModeEnabled" to false,
      "processResidentMemoryBytes" to 4096.0, "batteryCycleCount" to 0.0,
      "cpuArchitecture" to "aarch64", "powerSource" to "battery",
    ), signals.toRawMap())
  }

  @Test
  fun `font fingerprint remains a separate raw field`() {
    assertEquals(mapOf("fontsDigest" to "abc123"), FontsSignals("abc123").toRawMap())
  }

  @Test
  fun `every builder observation retains its name and value`() {
    val builder = HardwareSignals.Builder()
    val expected = linkedMapOf<String, Any>()
    val fields = builder.javaClass.declaredFields.filter { !it.isSynthetic }
    assertEquals(35, fields.size)
    fields.forEachIndexed { index, field ->
      val value: Any = when (field.type) {
        java.lang.Integer::class.java -> index + 100
        java.lang.Double::class.java -> index + 0.25
        java.lang.Boolean::class.java -> index % 2 == 0
        String::class.java -> "observed-${field.name}"
        else -> error("Unexpected hardware observation type: ${field.type}")
      }
      field.isAccessible = true
      field.set(builder, value)
      expected[field.name] = value
    }
    assertEquals(expected, builder.build().toRawMap())
  }
}
