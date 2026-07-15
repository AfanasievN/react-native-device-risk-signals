package com.reactnativedeviceintel

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

/**
 * application — host app identity, install provenance, and process/permission state. Every read is of
 * our OWN package only — permission-free, no QUERY_ALL_PACKAGES. Repackaging / version / installer /
 * permission-grant / split-delivery consistency signals. See NativeDeviceIntel.ts.
 */
class ApplicationInfoProvider(private val context: Context) {

  fun getApplicationSignals(): WritableMap {
    val map = Arguments.createMap()
    val pm = context.packageManager
    val packageName = context.packageName

    safe {
      val info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
      info.versionName?.let { map.putString("appVersion", it) }
      val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          info.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          info.versionCode.toLong()
        }
      map.putString("appBuild", versionCode.toString())
      map.putString("bundleId", packageName)
      // Install provenance (own package; permission-free). WritableMap has no putLong → Double (ms).
      map.putDouble("firstInstallTimeMs", info.firstInstallTime.toDouble())
      map.putDouble("lastUpdateTimeMs", info.lastUpdateTime.toDouble())
      addGrantedPermissions(map, info)
      addSplits(map, info.applicationInfo)
    }

    safe { pm.getApplicationLabel(context.applicationInfo).toString() }
      ?.let { if (it.isNotEmpty()) map.putString("appName", it) }
    addInstaller(map, pm, packageName)
    addForeground(map)
    safe { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() }
      ?.let { if (it >= 0) map.putDouble("processUptimeMs", it.toDouble()) }

    return map
  }

  // The host app's OWN requested permissions that are currently granted. Reads only our package —
  // no QUERY_ALL_PACKAGES, no new permission. An unusual grant set is a consistency/automation tell.
  private fun addGrantedPermissions(map: WritableMap, info: PackageInfo) {
    val requested = info.requestedPermissions ?: return
    val flags = info.requestedPermissionsFlags
    val granted = Arguments.createArray()
    requested.forEachIndexed { i, permission ->
      val isGranted =
        flags != null &&
          i < flags.size &&
          (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
      if (isGranted) granted.pushString(permission)
    }
    map.putArray("grantedPermissions", granted)
  }

  // App-Bundle split delivery. A repackaged/cloned build is typically a single monolithic APK, so
  // absence of the expected splits is a tamper tell. splitNames is API 26+.
  private fun addSplits(map: WritableMap, appInfo: ApplicationInfo?) {
    if (appInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val splits = appInfo.splitNames ?: emptyArray()
    map.putBoolean("isSplitApks", splits.isNotEmpty())
    val names = Arguments.createArray()
    splits.forEach { names.pushString(it) }
    map.putArray("splitNames", names)
  }

  // Install source: "com.android.vending" = Play Store; a null / unknown / sideload installer is a
  // strong fraud tell.
  private fun addInstaller(map: WritableMap, pm: PackageManager, packageName: String) {
    val installer =
      safe {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
          @Suppress("DEPRECATION")
          pm.getInstallerPackageName(packageName)
        }
      }
    installer?.let { if (it.isNotEmpty()) map.putString("installerPackage", it) }
  }

  // On modern Android runningAppProcesses returns only our OWN process (privacy) — exactly what we
  // want: our process's foreground importance. Permission-free.
  private fun addForeground(map: WritableMap) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
    val mine = safe { am.runningAppProcesses }?.firstOrNull { it.pid == Process.myPid() } ?: return
    map.putBoolean(
      "isForeground",
      mine.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
    )
  }

  private inline fun <T> safe(block: () -> T): T? =
    try {
      block()
    } catch (e: Throwable) {
      null
    }
}
