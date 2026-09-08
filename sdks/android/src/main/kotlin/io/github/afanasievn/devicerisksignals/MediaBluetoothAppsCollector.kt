package io.github.afanasievn.devicerisksignals

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings

/**
 * Local Android observations: audio route/music state, bonded Bluetooth count with host permissions,
 * a finite app audit against [KnownAppLists], and enabled accessibility services. Screen-capture is omitted:
 * this collector does not register lifecycle callbacks or infer a capture state.
 */
internal class MediaBluetoothAppsCollector(private val context: Context) {

  fun collect(): MediaBluetoothAppsSignals {
    var signals = MediaBluetoothAppsSignals()

    // ── Audio ──
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio != null) {
      safeBool { audio.isMusicActive }.let { signals = signals.copy(isMusicActive = it) }
      audioOutputRoute(audio)?.let { signals = signals.copy(audioOutputRoute = it) }
    }

    // ── Bluetooth (bonded devices) ──
    signals = addBondedBluetooth(signals)

    // ── Display topology ──
    signals = addDisplayTopology(signals)

    // ── App audit ──
    val flagged = KnownAppLists.allQueriedPackages.filter { isInstalled(it) }
    signals = signals.copy(installedFlaggedApps = flagged)

    // ── Accessibility services (raw enumeration) ──
    signals = signals.copy(enabledAccessibilityServices = enabledAccessibilityServices())

    return signals
  }

  private fun addDisplayTopology(initial: MediaBluetoothAppsSignals): MediaBluetoothAppsSignals {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return initial
    var signals = initial
    safe { manager.displays.size }?.let { signals = signals.copy(displayCount = it) }
    safe { manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).size }
      ?.let { signals = signals.copy(presentationDisplayCount = it) }
    return signals
  }

  private fun audioOutputRoute(audio: AudioManager): String? = safe {
    val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val types = devices.map { it.type }
    when {
      types.any { it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } -> "bluetooth"
      types.any { it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> "wired"
      types.any { it == AudioDeviceInfo.TYPE_USB_HEADSET || it == AudioDeviceInfo.TYPE_USB_DEVICE } -> "usb"
      types.any { it == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } -> "speaker"
      types.isNotEmpty() -> "other"
      else -> null
    }
  }

  @SuppressLint("MissingPermission")
  private fun addBondedBluetooth(signals: MediaBluetoothAppsSignals): MediaBluetoothAppsSignals {
    try {
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
      ) return signals

      val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return signals
      val adapter = manager.adapter ?: return signals
      // Count only; never read peripheral names or addresses. Android 12+ grants are checked above;
      // legacy permissions and revocation failures are handled by the surrounding catch.
      val bonded = adapter.bondedDevices ?: return signals
      return signals.copy(bluetoothBondedDeviceCount = bonded.size)
    } catch (e: Exception) {
      // BT off, unsupported, or permission revoked — omit the fields.
    }
    return signals
  }

  private fun enabledAccessibilityServices(): List<String> {
    val raw = safe {
      Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    } ?: return emptyList()
    return raw.split(':').map { it.trim() }.filter { it.isNotEmpty() }
  }

  private fun isInstalled(pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0)
    true
  } catch (e: Exception) {
    false
  }

  private inline fun safeBool(block: () -> Boolean): Boolean = try {
    block()
  } catch (e: Throwable) {
    false
  }

  private inline fun <T> safe(block: () -> T): T? = try {
    block()
  } catch (e: Throwable) {
    null
  }
}
