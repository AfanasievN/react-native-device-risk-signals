package io.github.afanasievn.devicerisksignals

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings

/** Reads existing system state without prompting, authenticating, or creating keys. */
internal class DeviceSecurityPostureCollector(private val context: Context) {
  fun collect(): DeviceSecurityPostureSignals {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    val hasSecureLockScreen = safe { keyguard?.isDeviceSecure }
    val isDeviceLocked = safe { keyguard?.isDeviceLocked }
    val users = context.getSystemService(Context.USER_SERVICE) as? UserManager
    val isUserUnlocked = safe { users?.isUserUnlocked }

    val pm = context.packageManager
    val fingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    val face = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
    val iris = pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
    val biometryType = when {
      face -> "face"
      fingerprint -> "fingerprint"
      iris -> "iris"
      else -> "none"
    }
    val strongBoxAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    } else null
    val automaticTimeEnabled = globalSettingEnabled(Settings.Global.AUTO_TIME)
    val automaticTimeZoneEnabled = globalSettingEnabled(Settings.Global.AUTO_TIME_ZONE)
    val deviceProvisioned = globalSettingEnabled(Settings.Global.DEVICE_PROVISIONED)
    val securityPatch = if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SECURITY_PATCH.isNotEmpty()
    ) Build.VERSION.SECURITY_PATCH else null

    return DeviceSecurityPostureSignals(
      hasSecureLockScreen = hasSecureLockScreen,
      isDeviceLocked = isDeviceLocked,
      isUserUnlocked = isUserUnlocked,
      fingerprintHardwarePresent = fingerprint,
      faceHardwarePresent = face,
      biometryAvailable = fingerprint || face || iris,
      biometryType = biometryType,
      strongBoxAvailable = strongBoxAvailable,
      automaticTimeEnabled = automaticTimeEnabled,
      automaticTimeZoneEnabled = automaticTimeZoneEnabled,
      deviceProvisioned = deviceProvisioned,
      securityPatch = securityPatch,
    )
  }

  private fun globalSettingEnabled(key: String): Boolean? =
    safe { Settings.Global.getInt(context.contentResolver, key) != 0 }

  private inline fun <T> safe(block: () -> T): T? = try { block() } catch (e: Throwable) { null }
}
