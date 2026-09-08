package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSafetySignalsTest {
  @Test fun unavailableFieldsAreOmitted() {
    assertTrue(TransactionSafetySignals().toRawMap().isEmpty())
  }

  @Test fun observedFalseZeroAndEmptyListArePreserved() {
    assertEquals(
      mapOf(
        "isDeviceLocked" to false,
        "enabledAccessibilityServiceCount" to 0,
        "remoteAccessAppsFound" to emptyList<String>(),
      ),
      TransactionSafetySignals(
        isDeviceLocked = false,
        enabledAccessibilityServiceCount = 0,
        remoteAccessAppsFound = emptyList(),
      ).toRawMap(),
    )
  }

  @Test fun allSnapshotFieldsKeepTheirRawNamesAndTypes() {
    assertEquals(
      mapOf(
        "isDeviceLocked" to true,
        "isInteractive" to false,
        "enabledAccessibilityServiceCount" to 2,
        "accessibilityRunning" to true,
        "remoteAccessAppsFound" to listOf("example.remote"),
        "remoteAccessAppCount" to 1,
        "audioMode" to "inCommunication",
        "isCallActive" to true,
      ),
      TransactionSafetySignals(
        isDeviceLocked = true,
        isInteractive = false,
        enabledAccessibilityServiceCount = 2,
        accessibilityRunning = true,
        remoteAccessAppsFound = listOf("example.remote"),
        remoteAccessAppCount = 1,
        audioMode = "inCommunication",
        isCallActive = true,
      ).toRawMap(),
    )
  }
}
