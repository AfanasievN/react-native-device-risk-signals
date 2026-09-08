package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSecurityPostureSignalsTest {
  @Test fun unavailableFieldsAreOmitted() {
    assertTrue(DeviceSecurityPostureSignals().toRawMap().isEmpty())
  }

  @Test fun observedFalseValuesArePreserved() {
    assertEquals(
      mapOf("hasSecureLockScreen" to false, "automaticTimeEnabled" to false),
      DeviceSecurityPostureSignals(
        hasSecureLockScreen = false,
        automaticTimeEnabled = false,
      ).toRawMap(),
    )
  }

  @Test fun allFieldsKeepTheirRawNamesAndTypes() {
    assertEquals(
      mapOf(
        "hasSecureLockScreen" to true,
        "isDeviceLocked" to false,
        "isUserUnlocked" to true,
        "fingerprintHardwarePresent" to true,
        "faceHardwarePresent" to false,
        "biometryAvailable" to true,
        "biometryType" to "fingerprint",
        "strongBoxAvailable" to false,
        "automaticTimeEnabled" to true,
        "automaticTimeZoneEnabled" to false,
        "deviceProvisioned" to true,
        "securityPatch" to "2026-08-01",
      ),
      DeviceSecurityPostureSignals(
        hasSecureLockScreen = true,
        isDeviceLocked = false,
        isUserUnlocked = true,
        fingerprintHardwarePresent = true,
        faceHardwarePresent = false,
        biometryAvailable = true,
        biometryType = "fingerprint",
        strongBoxAvailable = false,
        automaticTimeEnabled = true,
        automaticTimeZoneEnabled = false,
        deviceProvisioned = true,
        securityPatch = "2026-08-01",
      ).toRawMap(),
    )
  }
}
