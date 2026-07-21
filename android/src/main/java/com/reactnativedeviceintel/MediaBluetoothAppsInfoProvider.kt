package com.reactnativedeviceintel

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
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

/**
 * media_bluetooth_apps (Android side, one class per the plan's accepted asymmetry). Covers: audio
 * route + music state, bonded Bluetooth devices (permissions already granted at app level), an
 * app-audit against [KnownAppLists], and enabled accessibility services (a device-farm automation
 * tell). Screen-capture is intentionally omitted on Android — there is no point-in-time query API
 * before API 35 (registerScreenCaptureCallback is lifecycle, not a snapshot). iOS reports it.
 */
class MediaBluetoothAppsInfoProvider(private val context: Context) {

  fun getMediaBluetoothAppsSignals(): WritableMap {
    val map = Arguments.createMap()

    // ── Audio ──
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio != null) {
      safeBool { audio.isMusicActive }.let { map.putBoolean("isMusicActive", it) }
      audioOutputRoute(audio)?.let { map.putString("audioOutputRoute", it) }
    }

    // ── Bluetooth (bonded devices) ──
    addBondedBluetooth(map)

    // ── Display topology ──
    addDisplayTopology(map)

    // ── App audit ──
    val flagged = KnownAppLists.allQueriedPackages.filter { isInstalled(it) }
    map.putArray("installedFlaggedApps", toStringArray(flagged))

    // ── Accessibility services (raw enumeration) ──
    map.putArray("enabledAccessibilityServices", toStringArray(enabledAccessibilityServices()))

    return map
  }

  private fun addDisplayTopology(map: WritableMap) {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
    safe { manager.displays.size }?.let { map.putInt("displayCount", it) }
    safe { manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).size }
      ?.let { map.putInt("presentationDisplayCount", it) }
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
  private fun addBondedBluetooth(map: WritableMap) {
    try {
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
      ) return

      val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
      val adapter = manager.adapter ?: return
      // COUNT only. bondedDevices requires BLUETOOTH_CONNECT (already granted at app level); guard
      // anyway since a user can revoke it. We deliberately do NOT read device.name — the bonded device
      // NAMES are PII (names of the user's other devices/peripherals). Compliance minimization.
      val bonded = adapter.bondedDevices ?: return
      map.putInt("bluetoothBondedDeviceCount", bonded.size)
    } catch (e: Exception) {
      // BT off, unsupported, or permission revoked — omit the fields.
    }
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

  private fun toStringArray(values: List<String>): WritableArray {
    val arr = Arguments.createArray()
    for (v in values) arr.pushString(v)
    return arr
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
