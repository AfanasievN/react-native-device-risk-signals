package io.github.afanasievn.devicerisksignals

import org.junit.Assert.*
import org.junit.Test

class GeolocationSignalsTest {
  @Test fun unavailableFixDoesNotBecomeNonMockFix() {
    val raw = GeolocationSignals(hasCoarsePermission = false, authorizationStatus = "denied", gnssSupported = false).toRawMap()
    assertEquals(mapOf("hasCoarsePermission" to false, "authorizationStatus" to "denied", "gnssSupported" to false), raw)
    assertFalse(raw.containsKey("isFromMockProvider"))
    assertTrue(GeolocationSignals().toRawMap().isEmpty())
  }

  @Test fun completeFixPreservesAllFieldsAndIntegerAge() {
    assertEquals(mapOf(
      "hasCoarsePermission" to true, "authorizationStatus" to "granted", "locationServicesEnabled" to false,
      "gnssSupported" to true, "latitude" to 0.0, "longitude" to -2.5, "accuracyMeters" to 0.0,
      "altitudeMeters" to -10.0, "provider" to "gps", "isFromMockProvider" to false, "locationAgeMs" to -1,
    ), GeolocationSignals(true, "granted", false, true, 0.0, -2.5, 0.0, -10.0, "gps", false, -1).toRawMap())
  }
}
