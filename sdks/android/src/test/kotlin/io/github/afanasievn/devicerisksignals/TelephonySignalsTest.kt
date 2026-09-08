package io.github.afanasievn.devicerisksignals

import org.junit.Assert.*
import org.junit.Test

class TelephonySignalsTest {
  @Test fun unavailableFieldsAreOmitted() {
    assertTrue(TelephonySignals().toRawMap().isEmpty())
  }

  @Test fun allObservationsKeepTheirTypesAndValues() {
    assertEquals(mapOf(
      "phoneType" to "gsm", "networkOperatorName" to "operator",
      "simOperatorName" to "sim", "networkCountryIso" to "md", "simCountryIso" to "ro",
      "simState" to "ready", "dataState" to "connected", "hasIccCard" to false,
      "isNetworkRoaming" to true, "simCount" to 0,
    ), TelephonySignals("gsm", "operator", "sim", "md", "ro", "ready", "connected", false, true, 0).toRawMap())
  }
}
