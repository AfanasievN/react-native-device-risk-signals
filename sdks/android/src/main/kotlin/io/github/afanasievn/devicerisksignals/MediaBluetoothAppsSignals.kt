package io.github.afanasievn.devicerisksignals

/** Local media, Bluetooth count, finite app visibility and accessibility observations. */
data class MediaBluetoothAppsSignals(
  val isMusicActive: Boolean? = null,
  val audioOutputRoute: String? = null,
  val bluetoothBondedDeviceCount: Int? = null,
  val displayCount: Int? = null,
  val presentationDisplayCount: Int? = null,
  val installedFlaggedApps: List<String>? = null,
  val enabledAccessibilityServices: List<String>? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    isMusicActive?.let { put("isMusicActive", it) }
    audioOutputRoute?.let { put("audioOutputRoute", it) }
    bluetoothBondedDeviceCount?.let { put("bluetoothBondedDeviceCount", it) }
    displayCount?.let { put("displayCount", it) }
    presentationDisplayCount?.let { put("presentationDisplayCount", it) }
    installedFlaggedApps?.let { put("installedFlaggedApps", it) }
    enabledAccessibilityServices?.let { put("enabledAccessibilityServices", it) }
  }
}
