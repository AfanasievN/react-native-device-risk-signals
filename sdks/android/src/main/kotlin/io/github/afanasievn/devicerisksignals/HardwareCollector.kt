package io.github.afanasievn.devicerisksignals

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.system.Os
import android.system.OsConstants
import android.util.DisplayMetrics
import android.view.Display
import java.io.File
import java.security.MessageDigest

/**
 * hardware — permission-free device-class + fingerprint entropy (screen / CPU / RAM / battery /
 * brightness / installed-fonts). Every read is wrapped so a vendor quirk can't throw the probe.
 * Uptime stands in for boot time (iOS boot time is a Required-Reason API; Android has no such limit).
 * Storage observations are Android-only; persistent identifiers are never collected.
 */
internal class HardwareCollector(private val context: Context) {

  fun collect(): HardwareSignals {
    val map = HardwareSignals.Builder()

    addScreen(map)
    addOrientation(map)
    addBrightness(map)
    addCpu(map)
    addMemory(map)
    addStorage(map)
    addBattery(map)
    addPower(map)
    addNfc(map)
    safe { SystemClock.elapsedRealtime() }?.let { map.uptimeMs = it.toDouble() }

    return map.build()
  }

  // Font enumeration and SHA-256 are collected separately so callers can schedule this heavier read
  // independently of hardware observations. Execution and timeout policy belong to the caller.
  fun collectFonts(): FontsSignals = FontsSignals(fontsDigest = fontsDigest())

  private fun addScreen(map: HardwareSignals.Builder) {
    val display: Display =
      safe { (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY) }
        ?: return
    safe {
      val metrics = DisplayMetrics().also(display::getMetrics)
      map.screenWidthPx = metrics.widthPixels
      map.screenHeightPx = metrics.heightPixels
      map.screenDensity = metrics.density.toDouble()
      map.screenDpi = metrics.densityDpi
    }
    safe {
      val real = DisplayMetrics().also(display::getRealMetrics)
      map.screenPhysicalWidthPx = real.widthPixels
      map.screenPhysicalHeightPx = real.heightPixels
      map.screenPhysicalDensity = real.density.toDouble()
    }
  }

  private fun addBrightness(map: HardwareSignals.Builder) {
    // Settings.System.SCREEN_BRIGHTNESS is 0..255 (raw, linear — server can normalize vs iOS perceptual).
    safe { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }?.let {
      map.screenBrightness = it.coerceIn(0, 255) / 255.0
    }
  }

  private fun addCpu(map: HardwareSignals.Builder) {
    safe { Runtime.getRuntime().availableProcessors() }?.let { if (it > 0) map.processorCount = it }
    safe { System.getProperty("os.arch") }?.let { if (it.isNotEmpty()) map.cpuArchitecture = it }
    // Max CPU frequency: sysfs reports kHz → MHz. Permission-free; SELinux may block the read on some
    // ROMs, in which case it is simply omitted.
    safe { File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }
      ?.let { if (it > 0) map.cpuMaxFrequencyMhz = (it / 1000).toDouble() }
  }

  private fun addOrientation(map: HardwareSignals.Builder) {
    safe {
      val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
      map.screenOrientation = if (landscape) "landscape" else "portrait"
    }
  }

  private fun addStorage(map: HardwareSignals.Builder) {
    // StatFs on the app's internal data dir — permission-free and not a restricted API on Android.
    // iOS deliberately omits storage (disk-space is an Apple Required-Reason API there).
    safe {
      val stat = StatFs(context.filesDir.absolutePath)
      map.storageTotalBytes = stat.totalBytes.toDouble()
      map.storageFreeBytes = stat.availableBytes.toDouble()
    }
  }

  private fun addMemory(map: HardwareSignals.Builder) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
    safe {
      val info = ActivityManager.MemoryInfo()
      am.getMemoryInfo(info)
      map.totalMemoryBytes = info.totalMem.toDouble()
      map.freeMemoryBytes = info.availMem.toDouble()
      map.isLowMemory = info.lowMemory
    }
    safe { am.isLowRamDevice }?.let { map.isLowRamDevice = it }
    val statm = safe { File("/proc/self/statm").readText() }
    val pageSize = safe { Os.sysconf(OsConstants._SC_PAGESIZE) }
    if (statm != null && pageSize != null) {
      ProcessMemoryCalculator.residentBytes(statm, pageSize)
        ?.let { if (it > 0) map.processResidentMemoryBytes = it.toDouble() }
    }
    safe { Runtime.getRuntime().maxMemory() }
      ?.let { if (it > 0) map.runtimeMaxMemoryBytes = it.toDouble() }
  }

  private fun addPower(map: HardwareSignals.Builder) {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    safe { power.isPowerSaveMode }?.let { map.lowPowerModeEnabled = it }
  }

  private fun addBattery(map: HardwareSignals.Builder) {
    val battery = safe { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } ?: return
    val level = safe { battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } ?: -1
    val scale = safe { battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } ?: -1
    if (level in 0..scale && scale > 0) {
      map.batteryLevel = level.toDouble() / scale.toDouble()
    }
    val state = when (safe { battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1) } ?: -1) {
      BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
      BatteryManager.BATTERY_STATUS_FULL -> "full"
      BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "unplugged"
      else -> "unknown"
    }
    map.batteryState = state
    val temp = safe { battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) } ?: Int.MIN_VALUE
    if (temp != Int.MIN_VALUE) {
      map.batteryTemperatureC = temp / 10.0 // EXTRA_TEMPERATURE is tenths of a degree C.
    }
    if (battery.hasExtra(BatteryManager.EXTRA_HEALTH)) {
      BatterySignalClassifier.healthName(battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))
        ?.let { map.batteryHealth = it }
    }
    if (battery.hasExtra(BatteryManager.EXTRA_VOLTAGE)) {
      battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        .takeIf { it > 0 }
        ?.let { map.batteryVoltageMv = it }
    }
    if (battery.hasExtra(BatteryManager.EXTRA_TECHNOLOGY)) {
      battery.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        ?.takeIf(String::isNotEmpty)
        ?.let { map.batteryTechnology = it }
    }
    if (battery.hasExtra(BatteryManager.EXTRA_PRESENT)) {
      map.batteryPresent = battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && battery.hasExtra(BatteryManager.EXTRA_BATTERY_LOW)) {
      map.batteryLow = battery.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false)
    }
    if (battery.hasExtra(BatteryManager.EXTRA_PLUGGED)) {
      BatterySignalClassifier.powerSourceName(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1))
        ?.let { map.powerSource = it }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && battery.hasExtra(BatteryManager.EXTRA_CYCLE_COUNT)) {
      BatterySignalClassifier.nonNegative(battery.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).toLong())
        ?.let { map.batteryCycleCount = it.toDouble() }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
      safe { manager?.computeChargeTimeRemaining() }
        ?.let(BatterySignalClassifier::nonNegative)
        ?.let { map.chargeTimeRemainingMs = it.toDouble() }
    }
  }

  private fun addNfc(map: HardwareSignals.Builder) {
    val available = safe { context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) } ?: return
    map.nfcAvailable = available
    if (!available) return
    val adapter = safe { NfcAdapter.getDefaultAdapter(context) } ?: return
    safe { adapter.isEnabled }?.let { map.nfcEnabled = it }
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
