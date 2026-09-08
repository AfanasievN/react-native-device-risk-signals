package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Test

class OsIntegritySignalsTest {
  @Test fun everyBuilderFieldSurvivesIndividuallyWithoutCrossedAssignments() {
    val fields = OsIntegritySignals.Builder::class.java.declaredFields
    assertEquals(58, fields.size)
    fields.forEachIndexed { index, field ->
      val value: Any = when (field.type) {
        String::class.java -> "value:${field.name}"
        java.lang.Integer::class.java -> 100 + index
        java.lang.Boolean::class.java -> true
        List::class.java -> listOf("value:${field.name}", "second:${field.name}")
        else -> error("Unsupported field type ${field.type}")
      }
      val observations = OsIntegritySignals.Builder()
      field.isAccessible = true
      field.set(observations, value)
      assertEquals(field.name, mapOf(field.name to value), observations.build().toRawMap())
      field.set(observations, null)
      assertEquals(field.name, emptyMap<String, Any>(), observations.build().toRawMap())
    }
  }

  @Test fun unavailableFieldsAreOmitted() {
    assertEquals(emptyMap<String, Any>(), OsIntegritySignals().toRawMap())
  }

  @Test fun allFieldsPreserveNamesTypesAndValues() {
    val signals = OsIntegritySignals(
      isEmulator = false,
      isDebuggerAttached = false,
      isDebuggerWaiting = false,
      developerModeEnabled = false,
      suBinaryFound = false,
      suOnPath = false,
      rootManagementAppFound = false,
      dangerousAppFound = false,
      rootCloakingAppFound = false,
      suspiciousFilePathsFound = false,
      suspiciousFilePaths = listOf("evidence-10"),
      suspiciousPathCount = 11,
      writableSystemPathFound = false,
      dangerousPropsPresent = false,
      dangerousSystemProperties = listOf("evidence-14"),
      injectedLibrariesFound = false,
      injectedLibraryNames = listOf("evidence-16"),
      injectedLibraryCount = 17,
      loadedHookClassNames = listOf("evidence-18"),
      hookStackFrameFound = false,
      hookStackFrames = listOf("evidence-20"),
      hookFrameworkFound = false,
      magiskMountsFound = false,
      suspiciousMountsFound = false,
      zygiskIndicatorsFound = false,
      magiskAbstractSocketFound = false,
      magicMountModulesFound = false,
      fridaThreadNamesFound = listOf("evidence-27"),
      fridaInjectorPipeFound = false,
      fridaListenerPortFound = false,
      suspiciousExecutableMappingsFound = false,
      tracerPid = 31,
      tracedByOtherProcess = false,
      testKeysBuild = false,
      suspiciousEnvironmentVariablesFound = false,
      suspiciousEnvironmentVariableNames = listOf("evidence-35"),
      verifiedBootState = "value-36",
      bootloaderLocked = false,
      selinuxEnforcing = false,
      ldPreloadSet = false,
      ldPreloadValue = "value-40",
      hiddenApiPolicy = "value-41",
      emulatorFingerprintMatch = false,
      emulatorFilesFound = false,
      emulatorBuildMarkers = listOf("evidence-44"),
      emulatorFilePaths = listOf("evidence-45"),
      emulatorSystemPropertyMarkers = listOf("evidence-46"),
      emulatorCpuMarkers = listOf("evidence-47"),
      emulatorVendorMarkers = listOf("evidence-48"),
      sensorCount = 49,
      hasAccelerometer = false,
      hasGyroscope = false,
      hasMagnetometer = false,
      hasProximitySensor = false,
      isRunningInUserTestHarness = false,
      deviceFarmMarkers = listOf("evidence-55"),
      emulatorChecksPerformed = listOf("evidence-56"),
      abi = "value-57",
    )
    val expected = mapOf<String, Any>(
      "isEmulator" to false,
      "isDebuggerAttached" to false,
      "isDebuggerWaiting" to false,
      "developerModeEnabled" to false,
      "suBinaryFound" to false,
      "suOnPath" to false,
      "rootManagementAppFound" to false,
      "dangerousAppFound" to false,
      "rootCloakingAppFound" to false,
      "suspiciousFilePathsFound" to false,
      "suspiciousFilePaths" to listOf("evidence-10"),
      "suspiciousPathCount" to 11,
      "writableSystemPathFound" to false,
      "dangerousPropsPresent" to false,
      "dangerousSystemProperties" to listOf("evidence-14"),
      "injectedLibrariesFound" to false,
      "injectedLibraryNames" to listOf("evidence-16"),
      "injectedLibraryCount" to 17,
      "loadedHookClassNames" to listOf("evidence-18"),
      "hookStackFrameFound" to false,
      "hookStackFrames" to listOf("evidence-20"),
      "hookFrameworkFound" to false,
      "magiskMountsFound" to false,
      "suspiciousMountsFound" to false,
      "zygiskIndicatorsFound" to false,
      "magiskAbstractSocketFound" to false,
      "magicMountModulesFound" to false,
      "fridaThreadNamesFound" to listOf("evidence-27"),
      "fridaInjectorPipeFound" to false,
      "fridaListenerPortFound" to false,
      "suspiciousExecutableMappingsFound" to false,
      "tracerPid" to 31,
      "tracedByOtherProcess" to false,
      "testKeysBuild" to false,
      "suspiciousEnvironmentVariablesFound" to false,
      "suspiciousEnvironmentVariableNames" to listOf("evidence-35"),
      "verifiedBootState" to "value-36",
      "bootloaderLocked" to false,
      "selinuxEnforcing" to false,
      "ldPreloadSet" to false,
      "ldPreloadValue" to "value-40",
      "hiddenApiPolicy" to "value-41",
      "emulatorFingerprintMatch" to false,
      "emulatorFilesFound" to false,
      "emulatorBuildMarkers" to listOf("evidence-44"),
      "emulatorFilePaths" to listOf("evidence-45"),
      "emulatorSystemPropertyMarkers" to listOf("evidence-46"),
      "emulatorCpuMarkers" to listOf("evidence-47"),
      "emulatorVendorMarkers" to listOf("evidence-48"),
      "sensorCount" to 49,
      "hasAccelerometer" to false,
      "hasGyroscope" to false,
      "hasMagnetometer" to false,
      "hasProximitySensor" to false,
      "isRunningInUserTestHarness" to false,
      "deviceFarmMarkers" to listOf("evidence-55"),
      "emulatorChecksPerformed" to listOf("evidence-56"),
      "abi" to "value-57",
    )
    assertEquals(expected, signals.toRawMap())
  }

  @Test fun emptyEvidenceAndZeroRemainObservedValues() {
    assertEquals(mapOf("suspiciousFilePaths" to emptyList<String>(), "tracerPid" to 0, "isDebuggerAttached" to false),
      OsIntegritySignals(suspiciousFilePaths = emptyList(), tracerPid = 0, isDebuggerAttached = false).toRawMap())
  }
}
