package com.reactnativedeviceintel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.system.Os
import android.system.OsConstants
import android.util.DisplayMetrics
import android.view.Display
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import java.io.File
import java.security.MessageDigest

/**
 * hardware — permission-free device-class + fingerprint entropy (screen / CPU / RAM / battery /
 * brightness / installed-fonts). Every read is wrapped so a vendor quirk can't throw the probe.
 * Uptime stands in for boot time (iOS boot time is a Required-Reason API; Android has no such limit).
 * Deliberately omits disk/storage size and any persistent id — see NativeDeviceIntel.ts.
 */
class HardwareInfoProvider(private val context: Context) {

  fun getHardwareSignals(): WritableMap {
    val map = Arguments.createMap()

    addScreen(map)
    addOrientation(map)
    addBrightness(map)
    addCpu(map)
    addMemory(map)
    addStorage(map)
    addBattery(map)
    addPower(map)
    safe { SystemClock.elapsedRealtime() }?.let { map.putDouble("uptimeMs", it.toDouble()) }

    return map
  }

  // fonts — now its OWN probe (split out of hardware). fontsDigest (enumeration + SHA-256) is the one
  // heavy read; isolating it means a slow device can time out THIS probe without dropping the fast
  // hardware fields. The probe framework's (generous) timeout + the module's background executor bound
  // it — no in-method thread juggling needed here anymore.
  fun getFontsFingerprint(): WritableMap {
    val map = Arguments.createMap()
    fontsDigest()?.let { map.putString("fontsDigest", it) }
    return map
  }

  private fun addScreen(map: WritableMap) {
    val display: Display =
      safe { (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY) }
        ?: return
    safe {
      val metrics = DisplayMetrics().also(display::getMetrics)
      map.putInt("screenWidthPx", metrics.widthPixels)
      map.putInt("screenHeightPx", metrics.heightPixels)
      map.putDouble("screenDensity", metrics.density.toDouble())
      map.putInt("screenDpi", metrics.densityDpi)
    }
    safe {
      val real = DisplayMetrics().also(display::getRealMetrics)
      map.putInt("screenPhysicalWidthPx", real.widthPixels)
      map.putInt("screenPhysicalHeightPx", real.heightPixels)
      map.putDouble("screenPhysicalDensity", real.density.toDouble())
    }
  }

  private fun addBrightness(map: WritableMap) {
    // Settings.System.SCREEN_BRIGHTNESS is 0..255 (raw, linear — server can normalize vs iOS perceptual).
    safe { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }?.let {
      map.putDouble("screenBrightness", it.coerceIn(0, 255) / 255.0)
    }
  }

  private fun addCpu(map: WritableMap) {
    safe { Runtime.getRuntime().availableProcessors() }?.let { if (it > 0) map.putInt("processorCount", it) }
    safe { System.getProperty("os.arch") }?.let { if (it.isNotEmpty()) map.putString("cpuArchitecture", it) }
    // Max CPU frequency: sysfs reports kHz → MHz. Permission-free; SELinux may block the read on some
    // ROMs, in which case it is simply omitted.
    safe { File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }
      ?.let { if (it > 0) map.putDouble("cpuMaxFrequencyMhz", (it / 1000).toDouble()) }
  }

  private fun addOrientation(map: WritableMap) {
    safe {
      val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
      map.putString("screenOrientation", if (landscape) "landscape" else "portrait")
    }
  }

  private fun addStorage(map: WritableMap) {
    // StatFs on the app's internal data dir — permission-free and not a restricted API on Android.
    // iOS deliberately omits storage (disk-space is an Apple Required-Reason API there).
    safe {
      val stat = StatFs(context.filesDir.absolutePath)
      map.putDouble("storageTotalBytes", stat.totalBytes.toDouble())
      map.putDouble("storageFreeBytes", stat.availableBytes.toDouble())
    }
  }

  private fun addMemory(map: WritableMap) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
    safe {
      val info = ActivityManager.MemoryInfo()
      am.getMemoryInfo(info)
      map.putDouble("totalMemoryBytes", info.totalMem.toDouble())
      map.putDouble("freeMemoryBytes", info.availMem.toDouble())
      map.putBoolean("isLowMemory", info.lowMemory)
    }
    safe { am.isLowRamDevice }?.let { map.putBoolean("isLowRamDevice", it) }
    val statm = safe { File("/proc/self/statm").readText() }
    val pageSize = safe { Os.sysconf(OsConstants._SC_PAGESIZE) }
    if (statm != null && pageSize != null) {
      ProcessMemoryCalculator.residentBytes(statm, pageSize)
        ?.let { if (it > 0) map.putDouble("processResidentMemoryBytes", it.toDouble()) }
    }
    safe { Runtime.getRuntime().maxMemory() }
      ?.let { if (it > 0) map.putDouble("runtimeMaxMemoryBytes", it.toDouble()) }
  }

  private fun addPower(map: WritableMap) {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    safe { power.isPowerSaveMode }?.let { map.putBoolean("lowPowerModeEnabled", it) }
  }

  private fun addBattery(map: WritableMap) {
    val battery = safe { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } ?: return
    val level = safe { battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } ?: -1
    val scale = safe { battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } ?: -1
    if (level in 0..scale && scale > 0) {
      map.putDouble("batteryLevel", level.toDouble() / scale.toDouble())
    }
    val state = when (safe { battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1) } ?: -1) {
      BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
      BatteryManager.BATTERY_STATUS_FULL -> "full"
      BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "unplugged"
      else -> "unknown"
    }
    map.putString("batteryState", state)
    val temp = safe { battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) } ?: Int.MIN_VALUE
    if (temp != Int.MIN_VALUE) {
      map.putDouble("batteryTemperatureC", temp / 10.0) // EXTRA_TEMPERATURE is tenths of a degree C.
    }
  }

  private fun fontsDigest(): String? {
    // Hash the sorted names of the installed font FILES by listing the standard font directories
    // directly — a stable, high-entropy fingerprint (OEM/ROM font sets differ) that is MUCH cheaper than
    // SystemFonts.getAvailableFonts(), which builds a Font object per font and took >1.5s on low-end
    // MediaTek devices (timing out the probe). Works on all API levels; the dirs are world-readable so
    // no permission is involved. An unreadable/absent dir just contributes nothing.
    return safe {
      val names =
        listOf("/system/fonts", "/product/fonts", "/system/font", "/data/fonts")
          .flatMap { dir -> File(dir).listFiles()?.map { it.name } ?: emptyList() }
          .filter { it.endsWith(".ttf", true) || it.endsWith(".otf", true) || it.endsWith(".ttc", true) }
          .distinct()
          .sorted()
      if (names.isEmpty()) {
        null
      } else {
        val md = MessageDigest.getInstance("SHA-256")
        names.forEach { md.update(it.toByteArray()) }
        md.digest().joinToString("") { "%02x".format(it) }
      }
    }
  }

  private inline fun <T> safe(block: () -> T): T? = try {
    block()
  } catch (e: Throwable) {
    null
  }
}
