package com.reactnativedeviceintel

/**
 * Canonical, single source of truth for the security-relevant Android package names the SDK looks
 * for. Used by [OsIntegrityProvider] (root-manager / hook-framework detection) today, and by the
 * app-audit (RAT / remote-access apps).
 *
 * IMPORTANT — package visibility: on Android 11+ (API 30) `PackageManager.getPackageInfo` cannot see
 * an arbitrary installed package unless it is declared in a `<queries>` block in the merged manifest
 * (and we deliberately do NOT request the `QUERY_ALL_PACKAGES` sensitive permission — it triggers a
 * Play Console policy review). So every package name here MUST also appear under `<queries>` in
 * `android/src/main/AndroidManifest.xml`. `knownAppLists.drift.spec.ts` fails CI if the two ever
 * diverge.
 */
object KnownAppLists {
  /** Superuser / root-manager apps. */
  val rootManagementPackages: List<String> = listOf(
    "com.topjohnwu.magisk",
    "com.noshufou.android.su",
    "com.noshufou.android.su.elite",
    "eu.chainfire.supersu",
    "com.koushikdutta.superuser",
    "com.thirdparty.superuser",
    "com.yellowes.su",
    "com.kingroot.kinguser",
    "com.kingo.root",
    "com.smedialink.oneclickroot",
    "com.zhiqupk.root.global",
    "com.alephzain.framaroot",
    "me.phh.superuser",
  )

  /** Runtime-instrumentation / hooking / SSL-interception frameworks. */
  val hookFrameworkPackages: List<String> = listOf(
    "de.robv.android.xposed.installer",
    "io.va.exposed",
    "org.meowcat.edxposed.manager",
    "org.lsposed.manager",
    "com.saurik.substrate",
    "com.guoshi.httpcanary",
    "app.greyshirts.sslcapture",
    "com.minhui.networkcapture",
    "com.emanuelef.remote_capture",
    "jp.co.taosoftware.android.packetcapture",
  )

  /** Remote-access / RAT / anonymity apps — device-farm remote-control and de-anonymization tools. */
  val ratRemoteAccessPackages: List<String> = listOf(
    "com.teamviewer.teamviewer.market.mobile",
    "com.teamviewer.quicksupport.market",
    "com.anydesk.anydeskandroid",
    "com.sand.airdroid",
    "com.microsoft.rdc.androidx",
    "com.splashtop.remote.pad.v2",
    "com.carriez.flutter_hbb",
    "org.torproject.android",
    "org.torproject.torbrowser",
  )

  /**
   * Potentially-dangerous apps — patchers / ROM managers / in-app-purchase spoofers that usually
   * imply a rooted or tampered device (subset of RootBeer's `knownDangerousAppsPackages`, curated).
   */
  val potentiallyDangerousAppPackages: List<String> = listOf(
    "com.dimonvideo.luckypatcher",
    "com.chelpus.luckypatcher",
    "com.chelpus.lackypatch",
    "com.koushikdutta.rommanager",
    "com.ramdroid.appquarantine",
    "cc.madkite.freedom",
  )

  /** Root-cloaking / root-hiding apps — presence itself is a strong tampering tell. */
  val rootCloakingPackages: List<String> = listOf(
    "com.devadvance.rootcloak",
    "com.devadvance.rootcloakplus",
    "com.zachspong.temprootremovejb",
    "com.amphoras.hidemyroot",
    "com.amphoras.hidemyrootadfree",
    "com.formyhm.hideroot",
  )

  /** Every package the SDK queries — the exact set that must be mirrored into `<queries>`. */
  val allQueriedPackages: List<String>
    get() = (
      rootManagementPackages + hookFrameworkPackages + ratRemoteAccessPackages +
        potentiallyDangerousAppPackages + rootCloakingPackages
    ).distinct()
}
