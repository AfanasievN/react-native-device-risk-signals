package io.github.afanasievn.devicerisksignals

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.security.MessageDigest

/**
 * application — host app identity, install provenance, and process/permission state. Every read is of
 * our OWN package only — permission-free, no QUERY_ALL_PACKAGES. Repackaging / version / installer /
 * permission-grant / split-delivery observations exposed through [ApplicationSignals].
 */
internal class ApplicationCollector(private val context: Context) {

  fun collect(): ApplicationSignals {
    val map = ApplicationSignalBuilder()
    val pm = context.packageManager
    val packageName = context.packageName

    safe {
      val flags = PackageManager.GET_PERMISSIONS or
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
      val info = pm.getPackageInfo(packageName, flags)
      info.versionName?.let { map.appVersion = it }
      val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          info.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          info.versionCode.toLong()
        }
      map.appBuild = versionCode.toString()
      map.bundleId = packageName
      // Own-package install provenance. Double milliseconds preserve the shared raw contract.
      map.firstInstallTimeMs = info.firstInstallTime.toDouble()
      map.lastUpdateTimeMs = info.lastUpdateTime.toDouble()
      addGrantedPermissions(map, info)
      addSplits(map, info.applicationInfo)
      addApplicationPolicy(map, pm, packageName, info.applicationInfo)
      addSigningCertificates(map, info)
    }

    safe { pm.getApplicationLabel(context.applicationInfo).toString() }
      ?.let { if (it.isNotEmpty()) map.appName = it }
    addInstaller(map, pm, packageName)
    addForeground(map)
    safe { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() }
      ?.let { if (it >= 0) map.processUptimeMs = it.toDouble() }

    return map.build()
  }

  private fun addApplicationPolicy(
    map: ApplicationSignalBuilder,
    pm: PackageManager,
    packageName: String,
    appInfo: ApplicationInfo?,
  ) {
    if (appInfo == null) return
    map.targetSdkVersion = appInfo.targetSdkVersion
    map.minSdkVersion = appInfo.minSdkVersion
    map.isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    map.isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    map.isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    @Suppress("DEPRECATION")
    map.isInstalledOnExternalStorage = (appInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      safe { pm.isInstantApp(packageName) }?.let { map.isInstantApp = it }
    }
  }

  private fun addSigningCertificates(map: ApplicationSignalBuilder, info: PackageInfo) {
    val current: List<ByteArray>
    val history: List<ByteArray>
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val signing = info.signingInfo ?: return
      map.hasMultipleSigners = signing.hasMultipleSigners()
      current = signing.apkContentsSigners.orEmpty().map { it.toByteArray() }
      history = (signing.signingCertificateHistory ?: signing.apkContentsSigners).orEmpty().map { it.toByteArray() }
    } else {
      @Suppress("DEPRECATION")
      val signatures = info.signatures.orEmpty().map { it.toByteArray() }
      current = signatures
      history = signatures
      map.hasMultipleSigners = signatures.size > 1
    }
    map.signingCertificateSha256 = toDigestArray(current)
    map.signingCertificateHistorySha256 = toDigestArray(history)
  }

  private fun toDigestArray(certificates: List<ByteArray>): List<String> =
    certificates.map(::sha256).distinct().sorted()

  private fun sha256(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

  // The host app's OWN requested permissions that are currently granted. Reads only our package —
  // no QUERY_ALL_PACKAGES, no new permission. An unusual grant set is a consistency/automation tell.
  private fun addGrantedPermissions(map: ApplicationSignalBuilder, info: PackageInfo) {
    val requested = info.requestedPermissions ?: return
    val flags = info.requestedPermissionsFlags
    val granted = mutableListOf<String>()
    requested.forEachIndexed { i, permission ->
      val isGranted =
        flags != null &&
          i < flags.size &&
          (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
      if (isGranted) granted.add(permission)
    }
    map.grantedPermissions = granted
  }

  // App-Bundle split delivery. A repackaged/cloned build is typically a single monolithic APK, so
  // absence of the expected splits is a tamper tell. splitNames is API 26+.
  private fun addSplits(map: ApplicationSignalBuilder, appInfo: ApplicationInfo?) {
    if (appInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val splits = appInfo.splitNames ?: emptyArray()
    map.isSplitApks = splits.isNotEmpty()
    map.splitNames = splits.toList()
  }

  // Own-package install provenance only. These are raw, installer-supplied observations rather than
  // trusted attestation. `installerPackage` remains an alias for compatibility.
  private fun addInstaller(map: ApplicationSignalBuilder, pm: PackageManager, packageName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val source = safe { pm.getInstallSourceInfo(packageName) } ?: return
      source.installingPackageName?.takeIf(String::isNotEmpty)?.let {
        map.installerPackage = it
        map.installingPackageName = it
      }
      source.initiatingPackageName?.takeIf(String::isNotEmpty)
        ?.let { map.initiatingPackageName = it }
      source.initiatingPackageSigningInfo?.apkContentsSigners
        ?.map { it.toByteArray() }
        ?.takeIf(List<ByteArray>::isNotEmpty)
        ?.let { map.initiatingPackageSigningCertificateSha256 = toDigestArray(it) }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        InstallSourceSignalMapper.packageSource(source.packageSource)
          ?.let { map.installPackageSource = it }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        source.updateOwnerPackageName?.takeIf(String::isNotEmpty)
          ?.let { map.updateOwnerPackageName = it }
      }
      return
    }

    val installer = safe {
      @Suppress("DEPRECATION")
      pm.getInstallerPackageName(packageName)
    }
    installer?.takeIf(String::isNotEmpty)?.let {
      map.installerPackage = it
      map.installingPackageName = it
    }
  }

  // On modern Android runningAppProcesses returns only our OWN process (privacy) — exactly what we
  // want: our process's foreground importance. Permission-free.
  private fun addForeground(map: ApplicationSignalBuilder) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
    val mine = safe { am.runningAppProcesses }?.firstOrNull { it.pid == Process.myPid() } ?: return
    map.isForeground = mine.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
  }

  private inline fun <T> safe(block: () -> T): T? =
    try {
      block()
    } catch (e: Throwable) {
      null
    }
}
