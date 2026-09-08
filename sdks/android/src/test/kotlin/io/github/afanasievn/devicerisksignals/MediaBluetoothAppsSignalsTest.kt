package io.github.afanasievn.devicerisksignals

import org.junit.Assert.*
import org.junit.Test

class MediaBluetoothAppsSignalsTest {
  @Test fun unavailableFieldsAreOmitted() {
    assertTrue(MediaBluetoothAppsSignals().toRawMap().isEmpty())
  }

  @Test fun falseZeroAndEmptyListsRemainObservations() {
    assertEquals(mapOf(
      "isMusicActive" to false,
      "bluetoothBondedDeviceCount" to 0,
      "displayCount" to 0,
      "presentationDisplayCount" to 0,
      "installedFlaggedApps" to emptyList<String>(),
      "enabledAccessibilityServices" to emptyList<String>(),
    ), MediaBluetoothAppsSignals(
      isMusicActive = false,
      bluetoothBondedDeviceCount = 0,
      displayCount = 0,
      presentationDisplayCount = 0,
      installedFlaggedApps = emptyList(),
      enabledAccessibilityServices = emptyList(),
    ).toRawMap())
  }

  @Test fun allObservationsKeepTheirNamesTypesAndOrder() {
    assertEquals(mapOf(
      "isMusicActive" to true,
      "audioOutputRoute" to "bluetooth",
      "bluetoothBondedDeviceCount" to 2,
      "displayCount" to 3,
      "presentationDisplayCount" to 1,
      "installedFlaggedApps" to listOf("example.first", "example.second"),
      "enabledAccessibilityServices" to listOf("example.reader/.Service", "example.control/.Service"),
    ), MediaBluetoothAppsSignals(
      isMusicActive = true,
      audioOutputRoute = "bluetooth",
      bluetoothBondedDeviceCount = 2,
      displayCount = 3,
      presentationDisplayCount = 1,
      installedFlaggedApps = listOf("example.first", "example.second"),
      enabledAccessibilityServices = listOf("example.reader/.Service", "example.control/.Service"),
    ).toRawMap())
  }
}
