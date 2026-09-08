package io.github.afanasievn.devicerisksignals

/** Permission-free hardware observations. Unavailable values are omitted from the raw map. */
data class HardwareSignals(
  val uptimeMs: Double? = null,
  val screenWidthPx: Int? = null,
  val screenHeightPx: Int? = null,
  val screenDensity: Double? = null,
  val screenDpi: Int? = null,
  val screenPhysicalWidthPx: Int? = null,
  val screenPhysicalHeightPx: Int? = null,
  val screenPhysicalDensity: Double? = null,
  val screenBrightness: Double? = null,
  val processorCount: Int? = null,
  val cpuArchitecture: String? = null,
  val cpuMaxFrequencyMhz: Double? = null,
  val screenOrientation: String? = null,
  val storageTotalBytes: Double? = null,
  val storageFreeBytes: Double? = null,
  val totalMemoryBytes: Double? = null,
  val freeMemoryBytes: Double? = null,
  val isLowMemory: Boolean? = null,
  val isLowRamDevice: Boolean? = null,
  val processResidentMemoryBytes: Double? = null,
  val runtimeMaxMemoryBytes: Double? = null,
  val lowPowerModeEnabled: Boolean? = null,
  val batteryLevel: Double? = null,
  val batteryState: String? = null,
  val batteryTemperatureC: Double? = null,
  val batteryHealth: String? = null,
  val batteryVoltageMv: Int? = null,
  val batteryTechnology: String? = null,
  val batteryPresent: Boolean? = null,
  val batteryLow: Boolean? = null,
  val powerSource: String? = null,
  val batteryCycleCount: Double? = null,
  val chargeTimeRemainingMs: Double? = null,
  val nfcAvailable: Boolean? = null,
  val nfcEnabled: Boolean? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    uptimeMs?.let { put("uptimeMs", it) }
    screenWidthPx?.let { put("screenWidthPx", it) }
    screenHeightPx?.let { put("screenHeightPx", it) }
    screenDensity?.let { put("screenDensity", it) }
    screenDpi?.let { put("screenDpi", it) }
    screenPhysicalWidthPx?.let { put("screenPhysicalWidthPx", it) }
    screenPhysicalHeightPx?.let { put("screenPhysicalHeightPx", it) }
    screenPhysicalDensity?.let { put("screenPhysicalDensity", it) }
    screenBrightness?.let { put("screenBrightness", it) }
    processorCount?.let { put("processorCount", it) }
    cpuArchitecture?.let { put("cpuArchitecture", it) }
    cpuMaxFrequencyMhz?.let { put("cpuMaxFrequencyMhz", it) }
    screenOrientation?.let { put("screenOrientation", it) }
    storageTotalBytes?.let { put("storageTotalBytes", it) }
    storageFreeBytes?.let { put("storageFreeBytes", it) }
    totalMemoryBytes?.let { put("totalMemoryBytes", it) }
    freeMemoryBytes?.let { put("freeMemoryBytes", it) }
    isLowMemory?.let { put("isLowMemory", it) }
    isLowRamDevice?.let { put("isLowRamDevice", it) }
    processResidentMemoryBytes?.let { put("processResidentMemoryBytes", it) }
    runtimeMaxMemoryBytes?.let { put("runtimeMaxMemoryBytes", it) }
    lowPowerModeEnabled?.let { put("lowPowerModeEnabled", it) }
    batteryLevel?.let { put("batteryLevel", it) }
    batteryState?.let { put("batteryState", it) }
    batteryTemperatureC?.let { put("batteryTemperatureC", it) }
    batteryHealth?.let { put("batteryHealth", it) }
    batteryVoltageMv?.let { put("batteryVoltageMv", it) }
    batteryTechnology?.let { put("batteryTechnology", it) }
    batteryPresent?.let { put("batteryPresent", it) }
    batteryLow?.let { put("batteryLow", it) }
    powerSource?.let { put("powerSource", it) }
    batteryCycleCount?.let { put("batteryCycleCount", it) }
    chargeTimeRemainingMs?.let { put("chargeTimeRemainingMs", it) }
    nfcAvailable?.let { put("nfcAvailable", it) }
    nfcEnabled?.let { put("nfcEnabled", it) }
  }

  internal class Builder {
    var uptimeMs: Double? = null
    var screenWidthPx: Int? = null
    var screenHeightPx: Int? = null
    var screenDensity: Double? = null
    var screenDpi: Int? = null
    var screenPhysicalWidthPx: Int? = null
    var screenPhysicalHeightPx: Int? = null
    var screenPhysicalDensity: Double? = null
    var screenBrightness: Double? = null
    var processorCount: Int? = null
    var cpuArchitecture: String? = null
    var cpuMaxFrequencyMhz: Double? = null
    var screenOrientation: String? = null
    var storageTotalBytes: Double? = null
    var storageFreeBytes: Double? = null
    var totalMemoryBytes: Double? = null
    var freeMemoryBytes: Double? = null
    var isLowMemory: Boolean? = null
    var isLowRamDevice: Boolean? = null
    var processResidentMemoryBytes: Double? = null
    var runtimeMaxMemoryBytes: Double? = null
    var lowPowerModeEnabled: Boolean? = null
    var batteryLevel: Double? = null
    var batteryState: String? = null
    var batteryTemperatureC: Double? = null
    var batteryHealth: String? = null
    var batteryVoltageMv: Int? = null
    var batteryTechnology: String? = null
    var batteryPresent: Boolean? = null
    var batteryLow: Boolean? = null
    var powerSource: String? = null
    var batteryCycleCount: Double? = null
    var chargeTimeRemainingMs: Double? = null
    var nfcAvailable: Boolean? = null
    var nfcEnabled: Boolean? = null

    fun build(): HardwareSignals = HardwareSignals(
      uptimeMs = uptimeMs,
      screenWidthPx = screenWidthPx,
      screenHeightPx = screenHeightPx,
      screenDensity = screenDensity,
      screenDpi = screenDpi,
      screenPhysicalWidthPx = screenPhysicalWidthPx,
      screenPhysicalHeightPx = screenPhysicalHeightPx,
      screenPhysicalDensity = screenPhysicalDensity,
      screenBrightness = screenBrightness,
      processorCount = processorCount,
      cpuArchitecture = cpuArchitecture,
      cpuMaxFrequencyMhz = cpuMaxFrequencyMhz,
      screenOrientation = screenOrientation,
      storageTotalBytes = storageTotalBytes,
      storageFreeBytes = storageFreeBytes,
      totalMemoryBytes = totalMemoryBytes,
      freeMemoryBytes = freeMemoryBytes,
      isLowMemory = isLowMemory,
      isLowRamDevice = isLowRamDevice,
      processResidentMemoryBytes = processResidentMemoryBytes,
      runtimeMaxMemoryBytes = runtimeMaxMemoryBytes,
      lowPowerModeEnabled = lowPowerModeEnabled,
      batteryLevel = batteryLevel,
      batteryState = batteryState,
      batteryTemperatureC = batteryTemperatureC,
      batteryHealth = batteryHealth,
      batteryVoltageMv = batteryVoltageMv,
      batteryTechnology = batteryTechnology,
      batteryPresent = batteryPresent,
      batteryLow = batteryLow,
      powerSource = powerSource,
      batteryCycleCount = batteryCycleCount,
      chargeTimeRemainingMs = chargeTimeRemainingMs,
      nfcAvailable = nfcAvailable,
      nfcEnabled = nfcEnabled,
    )
  }
}
