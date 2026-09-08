package io.github.afanasievn.devicerisksignals

/** Point-in-time raw observations; no lifecycle monitoring or safety verdict. */
data class TransactionSafetySignals(
  val isDeviceLocked: Boolean? = null,
  val isInteractive: Boolean? = null,
  val enabledAccessibilityServiceCount: Int? = null,
  val accessibilityRunning: Boolean? = null,
  val remoteAccessAppsFound: List<String>? = null,
  val remoteAccessAppCount: Int? = null,
  val audioMode: String? = null,
  val isCallActive: Boolean? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    isDeviceLocked?.let { put("isDeviceLocked", it) }
    isInteractive?.let { put("isInteractive", it) }
    enabledAccessibilityServiceCount?.let { put("enabledAccessibilityServiceCount", it) }
    accessibilityRunning?.let { put("accessibilityRunning", it) }
    remoteAccessAppsFound?.let { put("remoteAccessAppsFound", it) }
    remoteAccessAppCount?.let { put("remoteAccessAppCount", it) }
    audioMode?.let { put("audioMode", it) }
    isCallActive?.let { put("isCallActive", it) }
  }
}
