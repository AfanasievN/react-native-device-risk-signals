package io.github.afanasievn.devicerisksignals

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings

/** Reads current local state and a finite app list, without registering observers or prompting. */
internal class TransactionSafetyCollector(private val context: Context) {
  fun collect(): TransactionSafetySignals {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    val isDeviceLocked = safe { keyguard?.isDeviceLocked }
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isInteractive = safe { power?.isInteractive }

    val services = enabledAccessibilityServices()
    val remoteApps = KnownAppLists.ratRemoteAccessPackages.filter(::isInstalled)

    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val mode = safe { audio?.mode }

    return TransactionSafetySignals(
      isDeviceLocked = isDeviceLocked,
      isInteractive = isInteractive,
      enabledAccessibilityServiceCount = services?.size,
      accessibilityRunning = services?.isNotEmpty(),
      remoteAccessAppsFound = remoteApps,
      remoteAccessAppCount = remoteApps.size,
      audioMode = mode?.let(::audioMode),
      isCallActive = mode?.let {
        it == AudioManager.MODE_IN_CALL || it == AudioManager.MODE_IN_COMMUNICATION
      },
    )
  }

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
