package io.github.afanasievn.devicerisksignals

/** Point-in-time platform observations, not an authentication or security verdict. */
data class DeviceSecurityPostureSignals(
  val hasSecureLockScreen: Boolean? = null,
  val isDeviceLocked: Boolean? = null,
  val isUserUnlocked: Boolean? = null,
  val fingerprintHardwarePresent: Boolean? = null,
  val faceHardwarePresent: Boolean? = null,
  val biometryAvailable: Boolean? = null,
  val biometryType: String? = null,
  val strongBoxAvailable: Boolean? = null,
  val automaticTimeEnabled: Boolean? = null,
  val automaticTimeZoneEnabled: Boolean? = null,
  val deviceProvisioned: Boolean? = null,
  val securityPatch: String? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    hasSecureLockScreen?.let { put("hasSecureLockScreen", it) }
    isDeviceLocked?.let { put("isDeviceLocked", it) }
    isUserUnlocked?.let { put("isUserUnlocked", it) }
    fingerprintHardwarePresent?.let { put("fingerprintHardwarePresent", it) }
    faceHardwarePresent?.let { put("faceHardwarePresent", it) }
    biometryAvailable?.let { put("biometryAvailable", it) }
    biometryType?.let { put("biometryType", it) }
    strongBoxAvailable?.let { put("strongBoxAvailable", it) }
    automaticTimeEnabled?.let { put("automaticTimeEnabled", it) }
    automaticTimeZoneEnabled?.let { put("automaticTimeZoneEnabled", it) }
    deviceProvisioned?.let { put("deviceProvisioned", it) }
    securityPatch?.let { put("securityPatch", it) }
  }
}
