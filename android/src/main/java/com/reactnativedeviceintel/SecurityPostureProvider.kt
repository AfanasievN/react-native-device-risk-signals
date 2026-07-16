package com.reactnativedeviceintel

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

class SecurityPostureProvider(private val context: Context) {
  fun getDeviceSecurityPosture(): WritableMap = Arguments.createMap().apply {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    safe { keyguard?.isDeviceSecure }?.let { putBoolean("hasSecureLockScreen", it) }
    safe { keyguard?.isDeviceLocked }?.let { putBoolean("isDeviceLocked", it) }
    val users = context.getSystemService(Context.USER_SERVICE) as? UserManager
    safe { users?.isUserUnlocked }?.let { putBoolean("isUserUnlocked", it) }

    val pm = context.packageManager
    val fingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    val face = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
    val iris = pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
    putBoolean("fingerprintHardwarePresent", fingerprint)
    putBoolean("faceHardwarePresent", face)
    putBoolean("biometryAvailable", fingerprint || face || iris)
    putString("biometryType", when {
      face -> "face"
      fingerprint -> "fingerprint"
      iris -> "iris"
      else -> "none"
    })
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      putBoolean("strongBoxAvailable", pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
    }
    globalSettingEnabled(Settings.Global.AUTO_TIME)?.let { putBoolean("automaticTimeEnabled", it) }
    globalSettingEnabled(Settings.Global.AUTO_TIME_ZONE)?.let { putBoolean("automaticTimeZoneEnabled", it) }
    globalSettingEnabled(Settings.Global.DEVICE_PROVISIONED)?.let { putBoolean("deviceProvisioned", it) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SECURITY_PATCH.isNotEmpty()) {
      putString("securityPatch", Build.VERSION.SECURITY_PATCH)
    }
  }

  fun getTransactionSafetySignals(): WritableMap = Arguments.createMap().apply {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    safe { keyguard?.isDeviceLocked }?.let { putBoolean("isDeviceLocked", it) }
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    safe { power?.isInteractive }?.let { putBoolean("isInteractive", it) }

    enabledAccessibilityServices()?.let { services ->
      putInt("enabledAccessibilityServiceCount", services.size)
      putBoolean("accessibilityRunning", services.isNotEmpty())
    }
    val remoteApps = KnownAppLists.ratRemoteAccessPackages.filter(::isInstalled)
    putArray("remoteAccessAppsFound", Arguments.createArray().apply { remoteApps.forEach(::pushString) })
    putInt("remoteAccessAppCount", remoteApps.size)

    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val mode = safe { audio?.mode }
    if (mode != null) {
      putString("audioMode", audioMode(mode))
      putBoolean("isCallActive", mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION)
    }
  }

  private fun globalSettingEnabled(key: String): Boolean? =
    safe { Settings.Global.getInt(context.contentResolver, key) != 0 }

  private fun enabledAccessibilityServices(): List<String>? = try {
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
      ?.split(':')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
  } catch (e: Throwable) {
    null
  }

  private fun isInstalled(packageName: String): Boolean =
    safe { context.packageManager.getPackageInfo(packageName, 0) } != null

  private fun audioMode(mode: Int): String = when (mode) {
    AudioManager.MODE_NORMAL -> "normal"
    AudioManager.MODE_RINGTONE -> "ringtone"
    AudioManager.MODE_IN_CALL -> "inCall"
    AudioManager.MODE_IN_COMMUNICATION -> "inCommunication"
    else -> "other"
  }

  private inline fun <T> safe(block: () -> T): T? = try { block() } catch (e: Throwable) { null }
}
