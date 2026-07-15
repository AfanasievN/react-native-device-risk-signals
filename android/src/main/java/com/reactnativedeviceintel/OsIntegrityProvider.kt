package com.reactnativedeviceintel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.io.File

/**
 * Multi-method root / hook / emulator detection — the FAST, synchronous, permission-free bundle.
 * Each check returns a RAW observation; there is NO on-device verdict (the risk backend fuses them).
 * The active socket scan (frida port) lives in [FridaScanProvider] because it does blocking I/O.
 *
 * Every individual check is wrapped so it can never throw out of [getOsIntegrity]: on a locked-down
 * kernel SELinux routinely denies these reads with SecurityException, and "couldn't read" is a valid
 * (omitted) observation, not a crash.
 */
class OsIntegrityProvider(private val context: Context) {

  fun getOsIntegrity(): WritableMap {
    val map = Arguments.createMap()

    // ── Baseline (kept required for backward-compat with the skeleton contract) ──────────────────
    map.putBoolean("isEmulator", emulatorFingerprintMatch() || emulatorFilesFound())
    map.putBoolean("isDebuggerAttached", safeBool { Debug.isDebuggerConnected() })
    // developerModeEnabled = the Developer-Options master toggle; usbDebuggingEnabled = the (distinct)
    // USB-debugging switch. A device can have Developer Options on with ADB off — they are separate
    // raw signals, both read from Settings.Global (ADB_ENABLED was moved out of Settings.Secure in API 17).
    map.putBoolean("developerModeEnabled", isGlobalSettingEnabled(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED))
    map.putBoolean("usbDebuggingEnabled", isGlobalSettingEnabled(Settings.Global.ADB_ENABLED))

    // ── Root: files / binaries / packages ────────────────────────────────────────────────────────
    val suFound = SU_BINARY_PATHS.filter { safeExists(it) }
    val rootFilesFound = ROOT_FILE_PATHS.filter { safeExists(it) }
    val allSuspiciousPaths = (suFound + rootFilesFound).distinct()
    map.putBoolean("suBinaryFound", suFound.isNotEmpty())
    map.putBoolean("rootManagementAppFound", anyPackageInstalled(KnownAppLists.rootManagementPackages))
    map.putBoolean("suspiciousFilePathsFound", allSuspiciousPaths.isNotEmpty())
    map.putArray("suspiciousFilePaths", toStringArray(allSuspiciousPaths))
    map.putBoolean("writableSystemPathFound", writableSystemPaths().isNotEmpty())
    map.putBoolean("dangerousPropsPresent", dangerousProps())

    // ── Hooks / injection ────────────────────────────────────────────────────────────────────────
    val injected = suspiciousMappedLibraries()
    map.putBoolean("injectedLibrariesFound", injected.isNotEmpty())
    map.putArray("injectedLibraryNames", toStringArray(injected))
    map.putBoolean(
      "hookFrameworkFound",
      injected.isNotEmpty() || anyPackageInstalled(KnownAppLists.hookFrameworkPackages),
    )

    // ── Android integrity properties ────────────────────────────────────────────────────────────
    map.putBoolean("magiskMountsFound", magiskMountsFound())
    getSystemProperty("ro.boot.verifiedbootstate")?.let { map.putString("verifiedBootState", it) }
    getSystemProperty("ro.boot.flash.locked")?.let { map.putBoolean("bootloaderLocked", it == "1") }
    selinuxEnforcing()?.let { map.putBoolean("selinuxEnforcing", it) }
    val ldPreload = safeString { System.getenv("LD_PRELOAD") }
    map.putBoolean("ldPreloadSet", !ldPreload.isNullOrEmpty())
    if (!ldPreload.isNullOrEmpty()) map.putString("ldPreloadValue", ldPreload)
    hiddenApiPolicy()?.let { map.putString("hiddenApiPolicy", it) }

    // ── Emulator / device-farm heuristics ─────────────────────────────────────────────────────────
    map.putBoolean("emulatorFingerprintMatch", emulatorFingerprintMatch())
    map.putBoolean("emulatorFilesFound", emulatorFilesFound())
    map.putInt("sensorCount", sensorCount())
    (Build.SUPPORTED_ABIS?.firstOrNull())?.let { map.putString("abi", it) }

    return map
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

  private fun isGlobalSettingEnabled(key: String): Boolean = safeBool {
    Settings.Global.getInt(context.contentResolver, key, 0) != 0
  }

  private fun anyPackageInstalled(packages: List<String>): Boolean {
    val pm = context.packageManager
    for (pkg in packages) {
      try {
        pm.getPackageInfo(pkg, 0)
        return true
      } catch (e: Exception) {
        // NameNotFoundException (not installed) or invisible without a <queries> entry — treat as
        // "not found" and keep scanning the rest.
      }
    }
    return false
  }

  private fun writableSystemPaths(): List<String> =
    WRITABLE_PROBE_PATHS.filter { path -> safeBool { File(path).canWrite() } }

  private fun dangerousProps(): Boolean {
    val debuggable = getSystemProperty("ro.debuggable")
    val secure = getSystemProperty("ro.secure")
    return debuggable == "1" || secure == "0"
  }

  /**
   * Pure-Kotlin text scan of /proc/self/maps (v1 — no JNI/NDK, which would add a 4-ABI build matrix
   * this repo does not currently require). Returns the distinct
   * basenames of mapped files whose path matches a known hook/injection signature.
   */
  private fun suspiciousMappedLibraries(): List<String> {
    val hits = LinkedHashSet<String>()
    try {
      File("/proc/self/maps").useLines { lines ->
        for (line in lines) {
          val lower = line.lowercase()
          if (INJECTION_SIGNATURES.any { lower.contains(it) }) {
            val path = line.substringAfterLast(' ').trim()
            if (path.isNotEmpty() && path.startsWith("/")) {
              hits.add(path.substringAfterLast('/'))
            }
          }
        }
      }
    } catch (e: Exception) {
      // maps unreadable — leave hits empty.
    }
    return hits.toList()
  }

  private fun magiskMountsFound(): Boolean {
    for (path in MOUNT_INFO_PATHS) {
      try {
        val found = File(path).useLines { lines ->
          lines.any { line ->
            val lower = line.lowercase()
            MAGISK_MOUNT_SIGNATURES.any { lower.contains(it) }
          }
        }
        if (found) return true
      } catch (e: Exception) {
        // SELinux commonly blocks mountinfo on stock kernels — best-effort, keep trying the rest.
      }
    }
    return false
  }

  private fun selinuxEnforcing(): Boolean? {
    // /sys/fs/selinux/enforce: "1" enforcing, "0" permissive. Unreadable ⇒ unknown (null).
    return try {
      val content = File("/sys/fs/selinux/enforce").readText().trim()
      when (content) {
        "1" -> true
        "0" -> false
        else -> null
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun hiddenApiPolicy(): String? = safeString {
    Settings.Global.getString(context.contentResolver, "hidden_api_policy")
  }

  private fun emulatorFingerprintMatch(): Boolean {
    val fp = Build.FINGERPRINT ?: ""
    val model = Build.MODEL ?: ""
    val manufacturer = Build.MANUFACTURER ?: ""
    val brand = Build.BRAND ?: ""
    val device = Build.DEVICE ?: ""
    val product = Build.PRODUCT ?: ""
    val hardware = Build.HARDWARE ?: ""
    return fp.contains("generic", true) ||
      fp.contains("unknown", true) ||
      fp.contains("emulator", true) ||
      fp.contains("vbox", true) ||
      fp.contains("test-keys", true) ||
      model.contains("google_sdk", true) ||
      model.contains("emulator", true) ||
      model.contains("android sdk built for", true) ||
      manufacturer.contains("genymotion", true) ||
      manufacturer.contains("unknown", true) ||
      product.contains("sdk", true) ||
      product.contains("vbox", true) ||
      product.contains("emulator", true) ||
      hardware.contains("goldfish", true) ||
      hardware.contains("ranchu", true) ||
      hardware.contains("vbox", true) ||
      (brand.startsWith("generic", true) && device.startsWith("generic", true))
  }

  private fun emulatorFilesFound(): Boolean = EMULATOR_FILE_PATHS.any { safeExists(it) }

  private fun sensorCount(): Int = safeInt(-1) {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    sm?.getSensorList(Sensor.TYPE_ALL)?.size ?: -1
  }

  private fun getSystemProperty(key: String): String? {
    return try {
      val clazz = Class.forName("android.os.SystemProperties")
      val getter = clazz.getMethod("get", String::class.java)
      val value = getter.invoke(null, key) as? String
      if (value.isNullOrEmpty()) null else value
    } catch (e: Throwable) {
      null
    }
  }

  private fun safeExists(path: String): Boolean = safeBool { File(path).exists() }

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

  private inline fun safeString(block: () -> String?): String? = try {
    block()
  } catch (e: Throwable) {
    null
  }

  private inline fun safeInt(fallback: Int, block: () -> Int): Int = try {
    block()
  } catch (e: Throwable) {
    fallback
  }

  companion object {
    private val SU_BINARY_PATHS = listOf(
      "/system/bin/su",
      "/system/xbin/su",
      "/sbin/su",
      "/system/su",
      "/vendor/bin/su",
      "/su/bin/su",
      "/data/local/xbin/su",
      "/data/local/bin/su",
      "/data/local/su",
      "/system/sd/xbin/su",
      "/system/bin/failsafe/su",
      "/system/bin/.ext/.su",
      "/system/usr/we-need-root/su",
      "/magisk/.core/bin/su",
    )

    private val ROOT_FILE_PATHS = listOf(
      "/system/app/Superuser.apk",
      "/sbin/magisk",
      "/system/xbin/daemonsu",
      "/data/adb/magisk",
      "/data/adb/modules",
      "/cache/su",
      "/dev/com.koushikdutta.superuser.daemon/",
      "/system/etc/init.d/99SuperSUDaemon",
      "/system/xbin/busybox",
      "/data/local/tmp/frida-server",
    )

    private val WRITABLE_PROBE_PATHS = listOf(
      "/system",
      "/system/bin",
      "/system/sbin",
      "/system/xbin",
      "/vendor/bin",
      "/sbin",
      "/etc",
      "/system/etc",
      "/proc",
      "/data",
    )

    private val EMULATOR_FILE_PATHS = listOf(
      "/dev/qemu_pipe",
      "/dev/socket/qemud",
      "/dev/socket/genyd",
      "/dev/socket/baseband_genyd",
      "/system/lib/libc_malloc_debug_qemu.so",
      "/sys/qemu_trace",
      "/system/bin/qemu-props",
      "/dev/goldfish_pipe",
      "/dev/vboxguest",
      "/dev/vboxuser",
    )

    private val MOUNT_INFO_PATHS = listOf(
      "/proc/self/mountinfo",
      "/proc/mounts",
      "/proc/self/mounts",
    )

    private val INJECTION_SIGNATURES = listOf(
      "frida",
      "gum-js-loop",
      "gadget",
      "xposed",
      "edxposed",
      "lsposed",
      "riru",
      "substrate",
      "cydia",
      "cynject",
    )

    private val MAGISK_MOUNT_SIGNATURES = listOf(
      "magisk",
      "core/mirror",
      "core/img",
      "/sbin/.magisk",
    )
  }
}
