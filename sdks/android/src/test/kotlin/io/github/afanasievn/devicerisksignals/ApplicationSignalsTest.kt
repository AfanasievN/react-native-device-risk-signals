package io.github.afanasievn.devicerisksignals

import org.junit.Assert.*
import org.junit.Test

class ApplicationSignalsTest {
  @Test fun unavailableObservationsAreOmitted() {
    assertTrue(ApplicationSignals().toRawMap().isEmpty())
  }

  @Test fun preservesFalseZeroAndEmptyArrays() {
    val raw = ApplicationSignals(
      isDebuggable = false, isForeground = false, isSplitApks = false,
      firstInstallTimeMs = 0.0, grantedPermissions = emptyList(), splitNames = emptyList(),
    ).toRawMap()
    assertEquals(false, raw["isDebuggable"])
    assertEquals(false, raw["isForeground"])
    assertEquals(false, raw["isSplitApks"])
    assertEquals(0.0, raw["firstInstallTimeMs"])
    assertEquals(emptyList<String>(), raw["grantedPermissions"])
    assertEquals(emptyList<String>(), raw["splitNames"])
    assertFalse(raw.containsKey("appVersion"))
  }

  @Test fun retainsInstallAliasAndCertificateArrays() {
    val raw = ApplicationSignals(
      appBuild = "9007199254740993", installerPackage = "store", installingPackageName = "store",
      signingCertificateSha256 = listOf("a", "b"),
      signingCertificateHistorySha256 = listOf("a", "b", "c"),
      initiatingPackageSigningCertificateSha256 = listOf("d"),
      hasMultipleSigners = true, installPackageSource = "store",
    ).toRawMap()
    assertEquals("9007199254740993", raw["appBuild"])
    assertEquals(raw["installerPackage"], raw["installingPackageName"])
    assertEquals(listOf("a", "b"), raw["signingCertificateSha256"])
    assertEquals(listOf("a", "b", "c"), raw["signingCertificateHistorySha256"])
    assertEquals(listOf("d"), raw["initiatingPackageSigningCertificateSha256"])
    assertEquals(true, raw["hasMultipleSigners"])
    assertEquals("store", raw["installPackageSource"])
  }

  @Test fun builderRetainsCompletedReadsWhenLaterFieldsAreUnavailable() {
    val observations = ApplicationSignalBuilder()
    observations.appVersion = "1.2.3"
    observations.targetSdkVersion = 36
    observations.isInstalledOnExternalStorage = false
    val raw = observations.build().toRawMap()
    assertEquals(mapOf(
      "appVersion" to "1.2.3",
      "targetSdkVersion" to 36,
      "isInstalledOnExternalStorage" to false,
    ), raw)
  }

  @Test fun everyBuilderFieldSurvivesModelAndRawSerialization() {
    val observations = ApplicationSignalBuilder()
    val expected = linkedMapOf<String, Any>()
    val fields = ApplicationSignalBuilder::class.java.declaredFields
    assertEquals(27, fields.size)
    fields.forEachIndexed { index, field ->
      val value: Any = when (field.type) {
        String::class.java -> "value:${field.name}"
        java.lang.Integer::class.java -> 100 + index
        java.lang.Double::class.java -> 1000.5 + index
        java.lang.Boolean::class.java -> index % 2 == 0
        List::class.java -> listOf("value:${field.name}", "second:${field.name}")
        else -> error("Unsupported field type ${field.type}")
      }
      field.isAccessible = true
      field.set(observations, value)
      expected[field.name] = value
    }
    assertEquals(expected, observations.build().toRawMap())
    // Exercise each boolean alone as well: two boolean fields must not hide a crossed assignment.
    fields.filter { it.type == java.lang.Boolean::class.java }.forEach { field ->
      val single = ApplicationSignalBuilder()
      field.set(single, true)
      assertEquals(mapOf(field.name to true), single.build().toRawMap())
    }
  }
}
