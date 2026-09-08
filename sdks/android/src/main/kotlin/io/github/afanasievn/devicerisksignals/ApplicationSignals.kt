package io.github.afanasievn.devicerisksignals

/** Own-package application observations. Null means unavailable and is omitted from [toRawMap]. */
data class ApplicationSignals(
  val appVersion: String? = null,
  val appBuild: String? = null,
  val bundleId: String? = null,
  val firstInstallTimeMs: Double? = null,
  val lastUpdateTimeMs: Double? = null,
  val appName: String? = null,
  val processUptimeMs: Double? = null,
  val targetSdkVersion: Int? = null,
  val minSdkVersion: Int? = null,
  val isDebuggable: Boolean? = null,
  val isSystemApp: Boolean? = null,
  val isUpdatedSystemApp: Boolean? = null,
  val isInstantApp: Boolean? = null,
  val hasMultipleSigners: Boolean? = null,
  val signingCertificateSha256: List<String>? = null,
  val signingCertificateHistorySha256: List<String>? = null,
  val grantedPermissions: List<String>? = null,
  val isSplitApks: Boolean? = null,
  val splitNames: List<String>? = null,
  val installerPackage: String? = null,
  val installingPackageName: String? = null,
  val initiatingPackageName: String? = null,
  val initiatingPackageSigningCertificateSha256: List<String>? = null,
  val installPackageSource: String? = null,
  val updateOwnerPackageName: String? = null,
  val isInstalledOnExternalStorage: Boolean? = null,
  val isForeground: Boolean? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    appVersion?.let { put("appVersion", it) }
    appBuild?.let { put("appBuild", it) }
    bundleId?.let { put("bundleId", it) }
    firstInstallTimeMs?.let { put("firstInstallTimeMs", it) }
    lastUpdateTimeMs?.let { put("lastUpdateTimeMs", it) }
    appName?.let { put("appName", it) }
    processUptimeMs?.let { put("processUptimeMs", it) }
    targetSdkVersion?.let { put("targetSdkVersion", it) }
    minSdkVersion?.let { put("minSdkVersion", it) }
    isDebuggable?.let { put("isDebuggable", it) }
    isSystemApp?.let { put("isSystemApp", it) }
    isUpdatedSystemApp?.let { put("isUpdatedSystemApp", it) }
    isInstantApp?.let { put("isInstantApp", it) }
    hasMultipleSigners?.let { put("hasMultipleSigners", it) }
    signingCertificateSha256?.let { put("signingCertificateSha256", it) }
    signingCertificateHistorySha256?.let { put("signingCertificateHistorySha256", it) }
    grantedPermissions?.let { put("grantedPermissions", it) }
    isSplitApks?.let { put("isSplitApks", it) }
    splitNames?.let { put("splitNames", it) }
    installerPackage?.let { put("installerPackage", it) }
    installingPackageName?.let { put("installingPackageName", it) }
    initiatingPackageName?.let { put("initiatingPackageName", it) }
    initiatingPackageSigningCertificateSha256?.let { put("initiatingPackageSigningCertificateSha256", it) }
    installPackageSource?.let { put("installPackageSource", it) }
    updateOwnerPackageName?.let { put("updateOwnerPackageName", it) }
    isInstalledOnExternalStorage?.let { put("isInstalledOnExternalStorage", it) }
    isForeground?.let { put("isForeground", it) }
  }
}

/** Retains observations completed before a later platform read fails. */
internal class ApplicationSignalBuilder {
  var appVersion: String? = null
  var appBuild: String? = null
  var bundleId: String? = null
  var firstInstallTimeMs: Double? = null
  var lastUpdateTimeMs: Double? = null
  var appName: String? = null
  var processUptimeMs: Double? = null
  var targetSdkVersion: Int? = null
  var minSdkVersion: Int? = null
  var isDebuggable: Boolean? = null
  var isSystemApp: Boolean? = null
  var isUpdatedSystemApp: Boolean? = null
  var isInstantApp: Boolean? = null
  var hasMultipleSigners: Boolean? = null
  var signingCertificateSha256: List<String>? = null
  var signingCertificateHistorySha256: List<String>? = null
  var grantedPermissions: List<String>? = null
  var isSplitApks: Boolean? = null
  var splitNames: List<String>? = null
  var installerPackage: String? = null
  var installingPackageName: String? = null
  var initiatingPackageName: String? = null
  var initiatingPackageSigningCertificateSha256: List<String>? = null
  var installPackageSource: String? = null
  var updateOwnerPackageName: String? = null
  var isInstalledOnExternalStorage: Boolean? = null
  var isForeground: Boolean? = null
  fun build(): ApplicationSignals = ApplicationSignals(
    appVersion = appVersion,
    appBuild = appBuild,
    bundleId = bundleId,
    firstInstallTimeMs = firstInstallTimeMs,
    lastUpdateTimeMs = lastUpdateTimeMs,
    appName = appName,
    processUptimeMs = processUptimeMs,
    targetSdkVersion = targetSdkVersion,
    minSdkVersion = minSdkVersion,
    isDebuggable = isDebuggable,
    isSystemApp = isSystemApp,
    isUpdatedSystemApp = isUpdatedSystemApp,
    isInstantApp = isInstantApp,
    hasMultipleSigners = hasMultipleSigners,
    signingCertificateSha256 = signingCertificateSha256,
    signingCertificateHistorySha256 = signingCertificateHistorySha256,
    grantedPermissions = grantedPermissions,
    isSplitApks = isSplitApks,
    splitNames = splitNames,
    installerPackage = installerPackage,
    installingPackageName = installingPackageName,
    initiatingPackageName = initiatingPackageName,
    initiatingPackageSigningCertificateSha256 = initiatingPackageSigningCertificateSha256,
    installPackageSource = installPackageSource,
    updateOwnerPackageName = updateOwnerPackageName,
    isInstalledOnExternalStorage = isInstalledOnExternalStorage,
    isForeground = isForeground,
  )
}
