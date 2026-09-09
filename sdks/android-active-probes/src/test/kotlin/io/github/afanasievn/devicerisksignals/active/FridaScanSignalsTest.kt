package io.github.afanasievn.devicerisksignals.active

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FridaScanSignalsTest {
  @Test fun unavailableFieldsAreOmitted() {
    assertEquals(emptyMap<String, Any>(), FridaScanSignals().toRawMap())

    val partial = FridaScanSignals(scanPerformed = true, scannedPort = 27042).toRawMap()
    assertEquals(mapOf("scanPerformed" to true, "scannedPort" to 27042), partial)
    assertFalse(partial.containsKey("defaultPortOpen"))
    assertFalse(partial.containsKey("fridaHandshakeReject"))
  }

  @Test fun everyRawFieldPreservesItsValueAndType() {
    val map = FridaScanSignals(
      scanPerformed = true,
      scannedPort = 27042,
      defaultPortOpen = true,
      fridaHandshakeReject = false,
    ).toRawMap()

    assertEquals(
      mapOf(
        "scanPerformed" to true,
        "scannedPort" to 27042,
        "defaultPortOpen" to true,
        "fridaHandshakeReject" to false,
      ),
      map,
    )
    assertEquals(setOf("scanPerformed", "scannedPort", "defaultPortOpen", "fridaHandshakeReject"), map.keys)
    assertEquals(true, map["scanPerformed"] is Boolean)
    assertEquals(true, map["scannedPort"] is Int)
    assertEquals(true, map["defaultPortOpen"] is Boolean)
    assertEquals(true, map["fridaHandshakeReject"] is Boolean)
  }

  @Test fun falseFlagsAreReportedRatherThanOmitted() {
    assertEquals(
      mapOf("defaultPortOpen" to false, "fridaHandshakeReject" to false),
      FridaScanSignals(defaultPortOpen = false, fridaHandshakeReject = false).toRawMap(),
    )
  }
}
