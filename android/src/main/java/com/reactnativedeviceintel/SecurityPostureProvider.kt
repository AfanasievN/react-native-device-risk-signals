package com.reactnativedeviceintel

import io.github.afanasievn.devicerisksignals.KnownAppLists

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap

class SecurityPostureProvider(private val context: ReactApplicationContext) {
  private val transactionObserver = TransactionSafetyObserver(context)

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

    transactionObserver.attachAndSnapshot()?.let { addTransactionObservation(it) }
  }

  fun dispose() = transactionObserver.dispose()

  private fun WritableMap.addTransactionObservation(snapshot: TransactionObservationSnapshot) {
    putDouble("transactionObservationStartedElapsedMs", snapshot.startedElapsedMs.toDouble())
    putInt("observedTouchCount", snapshot.observedTouchCount)
    snapshot.obscuredTouchObserved?.let { putBoolean("obscuredTouchObserved", it) }
    snapshot.partiallyObscuredTouchObserved?.let { putBoolean("partiallyObscuredTouchObserved", it) }
    snapshot.lastObscuredTouchElapsedMs?.let { putDouble("lastObscuredTouchElapsedMs", it.toDouble()) }
    snapshot.lastPartiallyObscuredTouchElapsedMs
      ?.let { putDouble("lastPartiallyObscuredTouchElapsedMs", it.toDouble()) }
    if (snapshot.screenshotObservationActive) {
      putBoolean("screenshotObservationActive", true)
    }
    snapshot.screenshotDetectedSinceObservationStart
      ?.let { putBoolean("screenshotDetectedSinceObservationStart", it) }
    snapshot.lastScreenshotDetectedElapsedMs
      ?.let { putDouble("lastScreenshotDetectedElapsedMs", it.toDouble()) }
    snapshot.isVisibleInScreenRecording?.let {
      putBoolean("isVisibleInScreenRecording", it)
      putBoolean("isScreenCaptured", it)
    }
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
