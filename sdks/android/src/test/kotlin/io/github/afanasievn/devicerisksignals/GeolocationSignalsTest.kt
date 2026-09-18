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

  @Test fun completeFixPreservesAllFieldsAndAge() {
    assertEquals(mapOf(
      "hasCoarsePermission" to true, "authorizationStatus" to "granted", "locationServicesEnabled" to false,
      "gnssSupported" to true, "latitude" to 0.0, "longitude" to -2.5, "accuracyMeters" to 0.0,
      "altitudeMeters" to -10.0, "provider" to "gps", "isFromMockProvider" to false, "locationAgeMs" to -1L,
    ), GeolocationSignals(true, "granted", false, true, 0.0, -2.5, 0.0, -10.0, "gps", false, -1L).toRawMap())
  }

  @Test fun ageOfAFixOlderThanIntRangeStaysTheTrueAge() {
    // 30 days > Int.MAX_VALUE ms (~24.86 days). The emitted age must be the real elapsed time,
    // not a value wrapped by a 32-bit narrowing.
    val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
    val now = 1_800_000_000_000L
    assertEquals(thirtyDaysMs, GeolocationCollector.locationAgeMs(now, now - thirtyDaysMs))
  }

  @Test fun rawMapCarriesAnAgeBeyondIntRangeUnchanged() {
    val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
    val now = 1_800_000_000_000L
    val age = GeolocationCollector.locationAgeMs(now, now - thirtyDaysMs)
    assertEquals(thirtyDaysMs, GeolocationSignals(locationAgeMs = age).toRawMap()["locationAgeMs"])
  }

  @Test fun aFixTimestampedInTheFutureClampsToZero() {
    assertEquals(0L, GeolocationCollector.locationAgeMs(1_000L, 5_000L))
  }
}
