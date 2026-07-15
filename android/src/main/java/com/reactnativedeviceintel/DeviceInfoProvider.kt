package com.reactnativedeviceintel

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.system.Os
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

/**
 * device_identity — device/OS/build identity + kernel fingerprint. All permission-free reads. The
 * rich android.os.Build surface (nested `androidBuild`) and the uname kernel strings are high-entropy
 * fingerprint + consistency/emulator tells. os_integrity (root/hook/emulator detection) lives in its
 * own provider.
 */
class DeviceInfoProvider(private val context: Context) {

  fun getDeviceIdentity(): WritableMap {
    val map = Arguments.createMap()
    map.putString("manufacturer", Build.MANUFACTURER ?: "")
    map.putString("model", Build.MODEL ?: "")
    map.putString("brand", Build.BRAND ?: "")
    map.putString("systemName", "android")
    map.putString("systemVersion", Build.VERSION.RELEASE ?: "")
    map.putBoolean("isTablet", isTablet())

    // OS / kernel fingerprint (uname is not a Required-Reason concern on Android).
    putIfNotEmpty(map, "osBuild", readProp { Build.DISPLAY })
    try {
      val uname = Os.uname()
      putIfNotEmpty(map, "kernelVersion", uname.version)
      putIfNotEmpty(map, "kernelOsRelease", uname.release)
      putIfNotEmpty(map, "kernelOsType", uname.sysname)
    } catch (e: Throwable) {
      // uname unavailable — omit kernel fields.
    }

    map.putMap("androidBuild", collectAndroidBuild())
    return map
  }

  private fun collectAndroidBuild(): WritableMap {
    val b = Arguments.createMap()
    putIfNotEmpty(b, "board", readProp { Build.BOARD })
    putIfNotEmpty(b, "bootloader", readProp { Build.BOOTLOADER })
    putIfNotEmpty(b, "device", readProp { Build.DEVICE })
    putIfNotEmpty(b, "display", readProp { Build.DISPLAY })
    putIfNotEmpty(b, "fingerprint", readProp { Build.FINGERPRINT })
    putIfNotEmpty(b, "hardware", readProp { Build.HARDWARE })
    putIfNotEmpty(b, "host", readProp { Build.HOST })
    putIfNotEmpty(b, "id", readProp { Build.ID })
    putIfNotEmpty(b, "product", readProp { Build.PRODUCT })
    putIfNotEmpty(b, "tags", readProp { Build.TAGS })
    putIfNotEmpty(b, "buildType", readProp { Build.TYPE })
    readProp { Build.SUPPORTED_ABIS?.toList() }?.let { b.putArray("supportedAbis", toStringArray(it)) }
    readProp { Build.VERSION.SDK_INT }?.let { b.putInt("sdkInt", it) }
    putIfNotEmpty(b, "codename", readProp { Build.VERSION.CODENAME })
    putIfNotEmpty(b, "incremental", readProp { Build.VERSION.INCREMENTAL })
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      putIfNotEmpty(b, "securityPatch", readProp { Build.VERSION.SECURITY_PATCH })
      putIfNotEmpty(b, "baseOs", readProp { Build.VERSION.BASE_OS })
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      putIfNotEmpty(b, "socManufacturer", readProp { Build.SOC_MANUFACTURER })
      putIfNotEmpty(b, "socModel", readProp { Build.SOC_MODEL })
    }
    return b
  }

  private fun isTablet(): Boolean {
    val screenLayout = context.resources.configuration.screenLayout
    return (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
      Configuration.SCREENLAYOUT_SIZE_LARGE
  }

  private fun putIfNotEmpty(map: WritableMap, key: String, value: String?) {
    if (!value.isNullOrEmpty()) map.putString(key, value)
  }

  private fun toStringArray(values: List<String>): WritableArray {
    val arr = Arguments.createArray()
    for (v in values) arr.pushString(v)
    return arr
  }

  private inline fun <T> readProp(block: () -> T): T? = try {
    block()
  } catch (e: Throwable) {
    null
  }
}
