package io.github.afanasievn.devicerisksignals

/** Raw Android integrity observations. Null fields are omitted; existing observation semantics are preserved. */
data class OsIntegritySignals(
  val isEmulator: Boolean? = null,
  val isDebuggerAttached: Boolean? = null,
  val isDebuggerWaiting: Boolean? = null,
  val developerModeEnabled: Boolean? = null,
  val suBinaryFound: Boolean? = null,
  val suOnPath: Boolean? = null,
  val rootManagementAppFound: Boolean? = null,
  val dangerousAppFound: Boolean? = null,
  val rootCloakingAppFound: Boolean? = null,
  val suspiciousFilePathsFound: Boolean? = null,
  val suspiciousFilePaths: List<String>? = null,
  val suspiciousPathCount: Int? = null,
  val writableSystemPathFound: Boolean? = null,
  val dangerousPropsPresent: Boolean? = null,
  val dangerousSystemProperties: List<String>? = null,
  val injectedLibrariesFound: Boolean? = null,
  val injectedLibraryNames: List<String>? = null,
  val injectedLibraryCount: Int? = null,
  val loadedHookClassNames: List<String>? = null,
  val hookStackFrameFound: Boolean? = null,
  val hookStackFrames: List<String>? = null,
  val hookFrameworkFound: Boolean? = null,
  val magiskMountsFound: Boolean? = null,
  val suspiciousMountsFound: Boolean? = null,
  val zygiskIndicatorsFound: Boolean? = null,
  val magiskAbstractSocketFound: Boolean? = null,
  val magicMountModulesFound: Boolean? = null,
  val fridaThreadNamesFound: List<String>? = null,
  val fridaInjectorPipeFound: Boolean? = null,
  val fridaListenerPortFound: Boolean? = null,
  val suspiciousExecutableMappingsFound: Boolean? = null,
  val tracerPid: Int? = null,
  val tracedByOtherProcess: Boolean? = null,
  val testKeysBuild: Boolean? = null,
  val suspiciousEnvironmentVariablesFound: Boolean? = null,
  val suspiciousEnvironmentVariableNames: List<String>? = null,
  val verifiedBootState: String? = null,
  val bootloaderLocked: Boolean? = null,
  val selinuxEnforcing: Boolean? = null,
  val ldPreloadSet: Boolean? = null,
  val ldPreloadValue: String? = null,
  val hiddenApiPolicy: String? = null,
  val emulatorFingerprintMatch: Boolean? = null,
  val emulatorFilesFound: Boolean? = null,
  val emulatorBuildMarkers: List<String>? = null,
  val emulatorFilePaths: List<String>? = null,
  val emulatorSystemPropertyMarkers: List<String>? = null,
  val emulatorCpuMarkers: List<String>? = null,
  val emulatorVendorMarkers: List<String>? = null,
  val sensorCount: Int? = null,
  val hasAccelerometer: Boolean? = null,
  val hasGyroscope: Boolean? = null,
  val hasMagnetometer: Boolean? = null,
  val hasProximitySensor: Boolean? = null,
  val isRunningInUserTestHarness: Boolean? = null,
  val deviceFarmMarkers: List<String>? = null,
  val emulatorChecksPerformed: List<String>? = null,
  val abi: String? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    isEmulator?.let { put("isEmulator", it) }
    isDebuggerAttached?.let { put("isDebuggerAttached", it) }
    isDebuggerWaiting?.let { put("isDebuggerWaiting", it) }
    developerModeEnabled?.let { put("developerModeEnabled", it) }
    suBinaryFound?.let { put("suBinaryFound", it) }
    suOnPath?.let { put("suOnPath", it) }
    rootManagementAppFound?.let { put("rootManagementAppFound", it) }
    dangerousAppFound?.let { put("dangerousAppFound", it) }
    rootCloakingAppFound?.let { put("rootCloakingAppFound", it) }
    suspiciousFilePathsFound?.let { put("suspiciousFilePathsFound", it) }
    suspiciousFilePaths?.let { put("suspiciousFilePaths", it) }
    suspiciousPathCount?.let { put("suspiciousPathCount", it) }
    writableSystemPathFound?.let { put("writableSystemPathFound", it) }
    dangerousPropsPresent?.let { put("dangerousPropsPresent", it) }
    dangerousSystemProperties?.let { put("dangerousSystemProperties", it) }
    injectedLibrariesFound?.let { put("injectedLibrariesFound", it) }
    injectedLibraryNames?.let { put("injectedLibraryNames", it) }
    injectedLibraryCount?.let { put("injectedLibraryCount", it) }
    loadedHookClassNames?.let { put("loadedHookClassNames", it) }
    hookStackFrameFound?.let { put("hookStackFrameFound", it) }
    hookStackFrames?.let { put("hookStackFrames", it) }
    hookFrameworkFound?.let { put("hookFrameworkFound", it) }
    magiskMountsFound?.let { put("magiskMountsFound", it) }
    suspiciousMountsFound?.let { put("suspiciousMountsFound", it) }
    zygiskIndicatorsFound?.let { put("zygiskIndicatorsFound", it) }
    magiskAbstractSocketFound?.let { put("magiskAbstractSocketFound", it) }
    magicMountModulesFound?.let { put("magicMountModulesFound", it) }
    fridaThreadNamesFound?.let { put("fridaThreadNamesFound", it) }
    fridaInjectorPipeFound?.let { put("fridaInjectorPipeFound", it) }
    fridaListenerPortFound?.let { put("fridaListenerPortFound", it) }
    suspiciousExecutableMappingsFound?.let { put("suspiciousExecutableMappingsFound", it) }
    tracerPid?.let { put("tracerPid", it) }
    tracedByOtherProcess?.let { put("tracedByOtherProcess", it) }
    testKeysBuild?.let { put("testKeysBuild", it) }
    suspiciousEnvironmentVariablesFound?.let { put("suspiciousEnvironmentVariablesFound", it) }
    suspiciousEnvironmentVariableNames?.let { put("suspiciousEnvironmentVariableNames", it) }
    verifiedBootState?.let { put("verifiedBootState", it) }
    bootloaderLocked?.let { put("bootloaderLocked", it) }
    selinuxEnforcing?.let { put("selinuxEnforcing", it) }
    ldPreloadSet?.let { put("ldPreloadSet", it) }
    ldPreloadValue?.let { put("ldPreloadValue", it) }
    hiddenApiPolicy?.let { put("hiddenApiPolicy", it) }
    emulatorFingerprintMatch?.let { put("emulatorFingerprintMatch", it) }
    emulatorFilesFound?.let { put("emulatorFilesFound", it) }
    emulatorBuildMarkers?.let { put("emulatorBuildMarkers", it) }
    emulatorFilePaths?.let { put("emulatorFilePaths", it) }
    emulatorSystemPropertyMarkers?.let { put("emulatorSystemPropertyMarkers", it) }
    emulatorCpuMarkers?.let { put("emulatorCpuMarkers", it) }
    emulatorVendorMarkers?.let { put("emulatorVendorMarkers", it) }
    sensorCount?.let { put("sensorCount", it) }
    hasAccelerometer?.let { put("hasAccelerometer", it) }
    hasGyroscope?.let { put("hasGyroscope", it) }
    hasMagnetometer?.let { put("hasMagnetometer", it) }
    hasProximitySensor?.let { put("hasProximitySensor", it) }
    isRunningInUserTestHarness?.let { put("isRunningInUserTestHarness", it) }
    deviceFarmMarkers?.let { put("deviceFarmMarkers", it) }
    emulatorChecksPerformed?.let { put("emulatorChecksPerformed", it) }
    abi?.let { put("abi", it) }
  }

  internal class Builder {
    var isEmulator: Boolean? = null
    var isDebuggerAttached: Boolean? = null
    var isDebuggerWaiting: Boolean? = null
    var developerModeEnabled: Boolean? = null
    var suBinaryFound: Boolean? = null
    var suOnPath: Boolean? = null
    var rootManagementAppFound: Boolean? = null
    var dangerousAppFound: Boolean? = null
    var rootCloakingAppFound: Boolean? = null
    var suspiciousFilePathsFound: Boolean? = null
    var suspiciousFilePaths: List<String>? = null
    var suspiciousPathCount: Int? = null
    var writableSystemPathFound: Boolean? = null
    var dangerousPropsPresent: Boolean? = null
    var dangerousSystemProperties: List<String>? = null
    var injectedLibrariesFound: Boolean? = null
    var injectedLibraryNames: List<String>? = null
    var injectedLibraryCount: Int? = null
    var loadedHookClassNames: List<String>? = null
    var hookStackFrameFound: Boolean? = null
    var hookStackFrames: List<String>? = null
    var hookFrameworkFound: Boolean? = null
    var magiskMountsFound: Boolean? = null
    var suspiciousMountsFound: Boolean? = null
    var zygiskIndicatorsFound: Boolean? = null
    var magiskAbstractSocketFound: Boolean? = null
    var magicMountModulesFound: Boolean? = null
    var fridaThreadNamesFound: List<String>? = null
    var fridaInjectorPipeFound: Boolean? = null
    var fridaListenerPortFound: Boolean? = null
    var suspiciousExecutableMappingsFound: Boolean? = null
    var tracerPid: Int? = null
    var tracedByOtherProcess: Boolean? = null
    var testKeysBuild: Boolean? = null
    var suspiciousEnvironmentVariablesFound: Boolean? = null
    var suspiciousEnvironmentVariableNames: List<String>? = null
    var verifiedBootState: String? = null
    var bootloaderLocked: Boolean? = null
    var selinuxEnforcing: Boolean? = null
    var ldPreloadSet: Boolean? = null
    var ldPreloadValue: String? = null
    var hiddenApiPolicy: String? = null
    var emulatorFingerprintMatch: Boolean? = null
    var emulatorFilesFound: Boolean? = null
    var emulatorBuildMarkers: List<String>? = null
    var emulatorFilePaths: List<String>? = null
    var emulatorSystemPropertyMarkers: List<String>? = null
    var emulatorCpuMarkers: List<String>? = null
    var emulatorVendorMarkers: List<String>? = null
    var sensorCount: Int? = null
    var hasAccelerometer: Boolean? = null
    var hasGyroscope: Boolean? = null
    var hasMagnetometer: Boolean? = null
    var hasProximitySensor: Boolean? = null
    var isRunningInUserTestHarness: Boolean? = null
    var deviceFarmMarkers: List<String>? = null
    var emulatorChecksPerformed: List<String>? = null
    var abi: String? = null

    fun build(): OsIntegritySignals = OsIntegritySignals(
      isEmulator = isEmulator,
      isDebuggerAttached = isDebuggerAttached,
      isDebuggerWaiting = isDebuggerWaiting,
      developerModeEnabled = developerModeEnabled,
      suBinaryFound = suBinaryFound,
      suOnPath = suOnPath,
      rootManagementAppFound = rootManagementAppFound,
      dangerousAppFound = dangerousAppFound,
      rootCloakingAppFound = rootCloakingAppFound,
      suspiciousFilePathsFound = suspiciousFilePathsFound,
      suspiciousFilePaths = suspiciousFilePaths,
      suspiciousPathCount = suspiciousPathCount,
      writableSystemPathFound = writableSystemPathFound,
      dangerousPropsPresent = dangerousPropsPresent,
      dangerousSystemProperties = dangerousSystemProperties,
      injectedLibrariesFound = injectedLibrariesFound,
      injectedLibraryNames = injectedLibraryNames,
      injectedLibraryCount = injectedLibraryCount,
      loadedHookClassNames = loadedHookClassNames,
      hookStackFrameFound = hookStackFrameFound,
      hookStackFrames = hookStackFrames,
      hookFrameworkFound = hookFrameworkFound,
      magiskMountsFound = magiskMountsFound,
      suspiciousMountsFound = suspiciousMountsFound,
      zygiskIndicatorsFound = zygiskIndicatorsFound,
      magiskAbstractSocketFound = magiskAbstractSocketFound,
      magicMountModulesFound = magicMountModulesFound,
      fridaThreadNamesFound = fridaThreadNamesFound,
      fridaInjectorPipeFound = fridaInjectorPipeFound,
      fridaListenerPortFound = fridaListenerPortFound,
      suspiciousExecutableMappingsFound = suspiciousExecutableMappingsFound,
      tracerPid = tracerPid,
      tracedByOtherProcess = tracedByOtherProcess,
      testKeysBuild = testKeysBuild,
      suspiciousEnvironmentVariablesFound = suspiciousEnvironmentVariablesFound,
      suspiciousEnvironmentVariableNames = suspiciousEnvironmentVariableNames,
      verifiedBootState = verifiedBootState,
      bootloaderLocked = bootloaderLocked,
      selinuxEnforcing = selinuxEnforcing,
      ldPreloadSet = ldPreloadSet,
      ldPreloadValue = ldPreloadValue,
      hiddenApiPolicy = hiddenApiPolicy,
      emulatorFingerprintMatch = emulatorFingerprintMatch,
      emulatorFilesFound = emulatorFilesFound,
      emulatorBuildMarkers = emulatorBuildMarkers,
      emulatorFilePaths = emulatorFilePaths,
      emulatorSystemPropertyMarkers = emulatorSystemPropertyMarkers,
      emulatorCpuMarkers = emulatorCpuMarkers,
      emulatorVendorMarkers = emulatorVendorMarkers,
      sensorCount = sensorCount,
      hasAccelerometer = hasAccelerometer,
      hasGyroscope = hasGyroscope,
      hasMagnetometer = hasMagnetometer,
      hasProximitySensor = hasProximitySensor,
      isRunningInUserTestHarness = isRunningInUserTestHarness,
      deviceFarmMarkers = deviceFarmMarkers,
      emulatorChecksPerformed = emulatorChecksPerformed,
      abi = abi,
    )
  }
}
