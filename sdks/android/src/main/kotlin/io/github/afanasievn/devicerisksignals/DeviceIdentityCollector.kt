package io.github.afanasievn.devicerisksignals

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.system.Os

internal class DeviceIdentityCollector(private val context: Context) {
  fun collect(): DeviceIdentitySignals {
    var kernelVersion: String? = null
    var kernelOsRelease: String? = null
    var kernelOsType: String? = null
    try {
      val uname = Os.uname()
      kernelVersion = uname.version.takeIf(String::isNotEmpty)
      kernelOsRelease = uname.release.takeIf(String::isNotEmpty)
      kernelOsType = uname.sysname.takeIf(String::isNotEmpty)
    } catch (_: Throwable) {
      // Unavailable kernel fields remain omitted.
    }

    return DeviceIdentitySignals(
      manufacturer = read { Build.MANUFACTURER }.orEmpty(),
      model = read { Build.MODEL }.orEmpty(),
      brand = read { Build.BRAND }.orEmpty(),
      systemVersion = read { Build.VERSION.RELEASE }.orEmpty(),
      isTablet = isTablet(),
      osBuild = readString { Build.DISPLAY },
      kernelVersion = kernelVersion,
      kernelOsRelease = kernelOsRelease,
      kernelOsType = kernelOsType,
      androidBuild = collectAndroidBuild(),
    )
  }

  private fun collectAndroidBuild() = AndroidBuildSignals(
    board = readString { Build.BOARD },
    bootloader = readString { Build.BOOTLOADER },
    device = readString { Build.DEVICE },
    display = readString { Build.DISPLAY },
    fingerprint = readString { Build.FINGERPRINT },
    hardware = readString { Build.HARDWARE },
    host = readString { Build.HOST },
    id = readString { Build.ID },
    product = readString { Build.PRODUCT },
    tags = readString { Build.TAGS },
    buildType = readString { Build.TYPE },
    supportedAbis = read { Build.SUPPORTED_ABIS.toList() },
    sdkInt = read { Build.VERSION.SDK_INT },
    codename = readString { Build.VERSION.CODENAME },
    incremental = readString { Build.VERSION.INCREMENTAL },
    securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      readString { Build.VERSION.SECURITY_PATCH }
    } else null,
    baseOs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      readString { Build.VERSION.BASE_OS }
    } else null,
    socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      readString { Build.SOC_MANUFACTURER }
    } else null,
    socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      readString { Build.SOC_MODEL }
    } else null,
    buildTimeMs = read { Build.TIME }?.takeIf { it > 0 },
  )

  private fun isTablet(): Boolean {
    val screenLayout = context.resources.configuration.screenLayout
    return (screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
      Configuration.SCREENLAYOUT_SIZE_LARGE
  }

  private fun readString(block: () -> String?): String? = read(block)?.takeIf(String::isNotEmpty)

  private inline fun <T> read(block: () -> T): T? = try {
    block()
  } catch (_: Throwable) {
    null
  }
}
