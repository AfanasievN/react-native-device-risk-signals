package com.reactnativedeviceintel

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.io.File

private data class SensorEvidence(
  val count: Int,
  val hasAccelerometer: Boolean,
  val hasGyroscope: Boolean,
  val hasMagnetometer: Boolean,
  val hasProximitySensor: Boolean,
)

private data class SystemPropertySnapshot(
  val available: Boolean,
  val values: Map<String, String>,
)

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

    val bstSharedFolder = safeString {
      "${Environment.getExternalStorageDirectory()?.absolutePath}/windows/BstSharedFolder"
    }
    val emulatorFiles = (EMULATOR_FILE_PATHS + listOfNotNull(bstSharedFolder)).filter { safeExists(it) }
    val emulatorProperties = emulatorSystemProperties()
    // /proc/tty/drivers carries "goldfish" on the Android emulator; fold it into the CPU-token scan.
    val cpuInfo = listOfNotNull(
      safeString { File("/proc/cpuinfo").readText() },
      safeString { File("/proc/tty/drivers").readText() },
    ).joinToString("\n").ifEmpty { null }
    val emulatorEvidence = EmulatorEvidenceClassifier.classify(
      build = EmulatorBuildSnapshot(
        fingerprint = Build.FINGERPRINT ?: "",
        model = Build.MODEL ?: "",
        manufacturer = Build.MANUFACTURER ?: "",
        brand = Build.BRAND ?: "",
        device = Build.DEVICE ?: "",
        product = Build.PRODUCT ?: "",
        hardware = Build.HARDWARE ?: "",
        host = Build.HOST ?: "",
        supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
      ),
      emulatorFilePaths = emulatorFiles,
      systemProperties = emulatorProperties.values,
      cpuInfo = cpuInfo,
    )

    // ── Baseline (kept required for backward-compat with the skeleton contract) ──────────────────
    map.putBoolean("isEmulator", emulatorEvidence.isStrongEmulatorEvidence)
    map.putBoolean("isDebuggerAttached", safeBool { Debug.isDebuggerConnected() })
    map.putBoolean("isDebuggerWaiting", safeBool { Debug.waitingForDebugger() })
    // Developer Options remains readable. Settings.Global.ADB_ENABLED is not a trustworthy
    // third-party observation on modern Android: ordinary apps receive 0. Keep its optional public
    // field reserved, but omit it rather than translating "unavailable" into "ADB disabled".
    map.putBoolean("developerModeEnabled", isGlobalSettingEnabled(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED))

    // ── Root: files / binaries / packages ────────────────────────────────────────────────────────
    val suFound = suPathCandidates().filter { safeExists(it) }
    val rootFilesFound = ROOT_FILE_PATHS.filter { safeExists(it) }
    val allSuspiciousPaths = (suFound + rootFilesFound).distinct()
    map.putBoolean("suBinaryFound", suFound.isNotEmpty())
    map.putBoolean("suOnPath", suExistsOnPath())
    map.putBoolean("rootManagementAppFound", anyPackageInstalled(KnownAppLists.rootManagementPackages))
    map.putBoolean("dangerousAppFound", anyPackageInstalled(KnownAppLists.potentiallyDangerousAppPackages))
    map.putBoolean("rootCloakingAppFound", anyPackageInstalled(KnownAppLists.rootCloakingPackages))
    map.putBoolean("suspiciousFilePathsFound", allSuspiciousPaths.isNotEmpty())
    map.putArray("suspiciousFilePaths", toStringArray(allSuspiciousPaths))
    map.putInt("suspiciousPathCount", allSuspiciousPaths.size)
    map.putBoolean("writableSystemPathFound", writableSystemPaths().isNotEmpty())
    val rootProperties = systemProperties(ROOT_RELEVANT_SYSTEM_PROPERTIES)
    if (rootProperties.available) {
      val dangerousProperties = IntegrityEvidenceClassifier.dangerousSystemProperties(rootProperties.values)
      map.putBoolean("dangerousPropsPresent", dangerousProperties.isNotEmpty())
      map.putArray("dangerousSystemProperties", toStringArray(dangerousProperties))
    }

    // ── Hooks / injection ────────────────────────────────────────────────────────────────────────
    val injected = suspiciousMappedLibraries()
    val loadedHookClasses = loadedHookClassNames()
    val hookStackFrames = hookStackFrameEvidence()
    map.putBoolean("injectedLibrariesFound", injected.isNotEmpty())
    map.putArray("injectedLibraryNames", toStringArray(injected))
    map.putInt("injectedLibraryCount", injected.size)
    map.putArray("loadedHookClassNames", toStringArray(loadedHookClasses))
    map.putBoolean("hookStackFrameFound", hookStackFrames.isNotEmpty())
    map.putArray("hookStackFrames", toStringArray(hookStackFrames))
    map.putBoolean(
      "hookFrameworkFound",
      injected.isNotEmpty() || loadedHookClasses.isNotEmpty() || hookStackFrames.isNotEmpty() ||
        anyPackageInstalled(KnownAppLists.hookFrameworkPackages),
    )

    // ── Android integrity properties ────────────────────────────────────────────────────────────
    map.putBoolean("magiskMountsFound", magiskMountsFound())
    map.putBoolean("suspiciousMountsFound", suspiciousMountsFound())
    map.putBoolean("zygiskIndicatorsFound", zygiskIndicatorsFound())
    // Magisk DenyList/Shamiko-resistant tells (still read from the app process — a future isolated-process
    // re-check would harden these further; see docs/native-security-borrowed-signals.md).
    map.putBoolean("magiskAbstractSocketFound", magiskAbstractSocketFound())
    map.putBoolean("magicMountModulesFound", magicMountModuleCount() > 0)
    // Frida evidence beyond the port connect + maps-basename scan.
    val fridaThreads = fridaThreadNames()
    map.putArray("fridaThreadNamesFound", toStringArray(fridaThreads))
    map.putBoolean("fridaInjectorPipeFound", fridaInjectorPipeFound())
    map.putBoolean("fridaListenerPortFound", fridaListenerFound())
    map.putBoolean("suspiciousExecutableMappingsFound", suspiciousExecutableMappingsFound())
    val tracerPid = tracerPid()
    if (tracerPid != null) {
      map.putInt("tracerPid", tracerPid)
      map.putBoolean("tracedByOtherProcess", tracerPid > 0)
    }
    map.putBoolean("testKeysBuild", (Build.TAGS ?: "").contains("test-keys", ignoreCase = true))
    val suspiciousEnvironment = suspiciousEnvironmentVariableNames()
    map.putBoolean("suspiciousEnvironmentVariablesFound", suspiciousEnvironment.isNotEmpty())
    map.putArray("suspiciousEnvironmentVariableNames", toStringArray(suspiciousEnvironment))
    getSystemProperty("ro.boot.verifiedbootstate")?.let { map.putString("verifiedBootState", it) }
    getSystemProperty("ro.boot.flash.locked")?.let { map.putBoolean("bootloaderLocked", it == "1") }
    selinuxEnforcing()?.let { map.putBoolean("selinuxEnforcing", it) }
    val ldPreload = safeString { System.getenv("LD_PRELOAD") }
    map.putBoolean("ldPreloadSet", !ldPreload.isNullOrEmpty())
    if (!ldPreload.isNullOrEmpty()) map.putString("ldPreloadValue", ldPreload)
    hiddenApiPolicy()?.let { map.putString("hiddenApiPolicy", it) }

    // ── Emulator / device-farm heuristics ─────────────────────────────────────────────────────────
    map.putBoolean("emulatorFingerprintMatch", emulatorEvidence.hasStrongBuildEvidence)
    map.putBoolean("emulatorFilesFound", emulatorEvidence.filePaths.isNotEmpty())
    map.putArray("emulatorBuildMarkers", toStringArray(emulatorEvidence.buildMarkers))
    map.putArray("emulatorFilePaths", toStringArray(emulatorEvidence.filePaths))
    map.putArray("emulatorSystemPropertyMarkers", toStringArray(emulatorEvidence.systemPropertyMarkers))
    map.putArray("emulatorCpuMarkers", toStringArray(emulatorEvidence.cpuMarkers))
    map.putArray("emulatorVendorMarkers", toStringArray(emulatorEvidence.emulatorVendorMarkers))
    val deviceFarmMarkers = emulatorEvidence.deviceFarmMarkers.toMutableList()
    val emulatorChecksPerformed = mutableListOf("build", "file_paths")
    if (emulatorProperties.available) emulatorChecksPerformed.add("system_properties")
    if (cpuInfo != null) emulatorChecksPerformed.add("cpu_info")
    val sensors = sensorEvidence()
    if (sensors != null) {
      emulatorChecksPerformed.add("sensors")
      map.putInt("sensorCount", sensors.count)
      map.putBoolean("hasAccelerometer", sensors.hasAccelerometer)
      map.putBoolean("hasGyroscope", sensors.hasGyroscope)
      map.putBoolean("hasMagnetometer", sensors.hasMagnetometer)
      map.putBoolean("hasProximitySensor", sensors.hasProximitySensor)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      emulatorChecksPerformed.add("test_harness")
      val isRunningInUserTestHarness = safeBool { ActivityManager.isRunningInUserTestHarness() }
      map.putBoolean("isRunningInUserTestHarness", isRunningInUserTestHarness)
      if (isRunningInUserTestHarness) deviceFarmMarkers.add("android_test_harness")
    }
    map.putArray("deviceFarmMarkers", toStringArray(deviceFarmMarkers.distinct()))
    map.putArray("emulatorChecksPerformed", toStringArray(emulatorChecksPerformed))
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

  private fun loadedHookClassNames(): List<String> =
    IntegrityEvidenceClassifier.loadedHookClassNames(HOOK_CLASS_NAMES) { name ->
      try {
        Class.forName(name, false, context.classLoader)
        true
      } catch (e: Throwable) {
        false
      }
    }

  // su-binary candidates: the static list PLUS the process $PATH directories (each normalized to
  // ".../su") — catches an `su` installed on a non-standard mount a fixed list would miss.
  private fun suPathCandidates(): List<String> {
    val fromEnv = safeString { System.getenv("PATH") }
      ?.split(':')
      ?.filter { it.isNotEmpty() }
      ?.map { "${it.trimEnd('/')}/su" }
      ?: emptyList()
    return (SU_BINARY_PATHS + EXTRA_SU_BINARY_PATHS + fromEnv).distinct()
  }

  // `which su` — orthogonal to file-existence: it resolves `su` via the process PATH. Best-effort;
  // absent `which` / SELinux denial ⇒ false (unknown, not "clean").
  private fun suExistsOnPath(): Boolean = safeBool {
    val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
    val line = process.inputStream.bufferedReader().use { it.readLine() }
    process.destroy()
    !line.isNullOrBlank()
  }

  // Stack-probe for hook frameworks: capture the current stack and match Xposed/LSPosed/Substrate
  // bridge frames (+ a re-injected-Zygote tell). Orthogonal to loaded-class and /proc/self/maps scans.
  private fun hookStackFrameEvidence(): List<String> = try {
    throw Exception("device-intel stack probe")
  } catch (e: Exception) {
    IntegrityEvidenceClassifier.hookStackFrameMatches(e.stackTrace.toList())
  }

  private fun magiskAbstractSocketFound(): Boolean = safeBool {
    IntegrityEvidenceClassifier.magiskAbstractSocketPresent(File("/proc/net/unix").readText())
  }

  private fun magicMountModuleCount(): Int = try {
    IntegrityEvidenceClassifier.magicMountModuleCount(
      File("/proc/self/mountinfo").readText(),
      File("/proc/self/maps").readText(),
    )
  } catch (e: Throwable) {
    0
  }

  private fun fridaListenerFound(): Boolean = safeBool {
    val tcp = listOfNotNull(
      safeString { File("/proc/net/tcp").readText() },
      safeString { File("/proc/net/tcp6").readText() },
    ).joinToString("\n")
    IntegrityEvidenceClassifier.fridaListenerPresent(tcp)
  }

  // Frida injects worker threads with recognizable names; enumerate /proc/self/task/<tid>/comm.
  private fun fridaThreadNames(): List<String> = try {
    val names = File("/proc/self/task").listFiles()
      ?.mapNotNull { taskDir -> safeString { File(taskDir, "comm").readText().trim() } }
      .orEmpty()
    IntegrityEvidenceClassifier.fridaThreadNamesFound(names)
  } catch (e: Throwable) {
    emptyList()
  }

  // Frida's injector opens a pipe whose fd symlink target contains "linjector".
  private fun fridaInjectorPipeFound(): Boolean = try {
    File("/proc/self/fd").listFiles()
      ?.any { fd -> safeString { fd.canonicalPath }?.contains("linjector") == true }
      ?: false
  } catch (e: Throwable) {
    false
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

  private fun suspiciousMountsFound(): Boolean = scanFiles(MOUNT_INFO_PATHS, SUSPICIOUS_MOUNT_SIGNATURES)

  private fun zygiskIndicatorsFound(): Boolean =
    scanFiles(MOUNT_INFO_PATHS + "/proc/self/maps", ZYGISK_SIGNATURES)

  private fun scanFiles(paths: List<String>, signatures: List<String>): Boolean {
    for (path in paths.distinct()) {
      try {
        if (File(path).useLines { lines -> lines.any { line -> signatures.any { line.contains(it, true) } } }) {
          return true
        }
      } catch (e: Exception) {
        // Protected procfs entry: unknown rather than fatal.
      }
    }
    return false
  }

  private fun tracerPid(): Int? = try {
    File("/proc/self/status").useLines { lines ->
      lines.firstOrNull { it.startsWith("TracerPid:") }
        ?.substringAfter(':')
        ?.trim()
        ?.toIntOrNull()
    }
  } catch (e: Exception) {
    null
  }

  private fun suspiciousExecutableMappingsFound(): Boolean = try {
    File("/proc/self/maps").useLines { lines ->
      lines.any { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 6)
        val permissions = parts.getOrNull(1).orEmpty()
        val path = parts.getOrNull(5).orEmpty().lowercase()
        permissions.contains('x') &&
          (permissions.contains('w') || path.contains("/data/local/tmp") || path.contains("(deleted)"))
      }
    }
  } catch (e: Exception) {
    false
  }

  private fun suspiciousEnvironmentVariableNames(): List<String> =
    SUSPICIOUS_ENVIRONMENT_VARIABLES.filter { !safeString { System.getenv(it) }.isNullOrEmpty() }

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

  private fun sensorEvidence(): SensorEvidence? = try {
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
      ?: return null
    SensorEvidence(
      count = sm.getSensorList(Sensor.TYPE_ALL).size,
      hasAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
      hasGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
      hasMagnetometer = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null,
      hasProximitySensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null,
    )
  } catch (e: Throwable) {
    null
  }

  private fun emulatorSystemProperties(): SystemPropertySnapshot = systemProperties(EMULATOR_SYSTEM_PROPERTIES)

  private fun systemProperties(keys: List<String>): SystemPropertySnapshot {
    return try {
      val clazz = Class.forName("android.os.SystemProperties")
      val getter = clazz.getMethod("get", String::class.java)
      val values = keys.mapNotNull { key ->
        val value = getter.invoke(null, key) as? String
        if (value.isNullOrEmpty()) null else key to value
      }.toMap()
      SystemPropertySnapshot(available = true, values = values)
    } catch (e: Throwable) {
      SystemPropertySnapshot(available = false, values = emptyMap())
    }
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

  companion object {
    private val SUSPICIOUS_ENVIRONMENT_VARIABLES = listOf(
      "LD_PRELOAD",
      "DYLD_INSERT_LIBRARIES",
      "DYLD_LIBRARY_PATH",
      "FRIDA_GADGET_CONFIG",
    )
    private val ROOT_RELEVANT_SYSTEM_PROPERTIES = listOf(
      "ro.debuggable",
      "ro.secure",
      "service.adb.root",
      "ro.sys.initd",
    )
    private val HOOK_CLASS_NAMES = listOf(
      "de.robv.android.xposed.XposedBridge",
      "com.saurik.substrate.MS\$SubstrateClass",
      "com.saurik.substrate.MS",
      "org.lsposed.lspd.core.Main",
    )
    private val SUSPICIOUS_MOUNT_SIGNATURES = listOf(
      "magisk",
      "kernelsu",
      "apatch",
      "overlayfs",
      "/data/adb/modules",
    )
    private val ZYGISK_SIGNATURES = listOf("zygisk", "riru", "lsposed", "libzygisk")
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

    // Extra su directories beyond SU_BINARY_PATHS (borrowed from RootBeer's path set).
    private val EXTRA_SU_BINARY_PATHS = listOf(
      "/system_ext/bin/su",
      "/cache/su",
    )

    private val ROOT_FILE_PATHS = listOf(
      "/system/app/Superuser.apk",
      "/sbin/magisk",
      "/system/xbin/daemonsu",
      "/data/adb/magisk",
      "/data/adb/modules",
      "/sys/kernel/kernelsu",
      "/data/adb/ksu",
      "/data/adb/ap",
      "/data/magisk/resetprop",
      "/cache/su",
      "/dev/com.koushikdutta.superuser.daemon/",
      "/system/etc/init.d/99SuperSUDaemon",
      "/system/xbin/busybox",
      "/data/local/tmp/frida-server",
      "/data/local/tmp/frida-gadget.so",
      "/data/local/tmp/re.frida.server",
      "/data/adb/magisk.db",
      "/sbin/.magisk",
      "/cache/.disable_magisk",
      "/system/framework/XposedBridge.jar",
      "/system/app/SuperSU.apk",
      "/system/app/Kinguser.apk",
      "/sbin/magiskinit",
      "/data/adb/magisk.img",
      "/data/adb/.boot_count",
      "/data/adb/magisk_simple",
      "/cache/magisk.log",
      "/init.magisk.rc",
      "/dev/.magisk.unblock",
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
      "/system/bin/nox-prop",
      "/system/bin/nox-vbox-sf",
      "/system/bin/microvirtd",
      "/system/bin/memud",
      "/system/bin/ldinit",
      "/system/bin/ldmountsf",
      "/system/bin/droid4x-prop",
      "/system/bin/bstfolder",
      "/system/bin/androVM-prop",
      "/fstab.andy",
      "/ueventd.andy.rc",
      "/fstab.nox",
      "/init.nox.rc",
      "/ueventd.nox.rc",
      "/ueventd.android_x86.rc",
      "/x86.prop",
      "/ueventd.ttVM_x86.rc",
      "/init.ttVM_x86.rc",
      "/fstab.ttVM_x86",
      "/fstab.vbox86",
      "/init.vbox86.rc",
      "/ueventd.vbox86.rc",
    )

    private val EMULATOR_SYSTEM_PROPERTIES = listOf(
      "ro.kernel.qemu",
      "ro.boot.qemu",
      "ro.hardware",
      "ro.boot.hardware",
      "ro.genymotion.version",
      "ro.nox.version",
      "ro.bluestacks.version",
      "ro.microvirt.version",
      "ro.ld.player.version",
      "firebase.test.lab",
      "ro.boot.test_harness",
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
