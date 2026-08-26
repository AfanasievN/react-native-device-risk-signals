package io.github.afanasievn.devicerisksignals

/** Typed raw Android build observations. Null means unavailable and is omitted from [toRawMap]. */
data class AndroidBuildSignals(
  val board: String? = null,
  val bootloader: String? = null,
  val device: String? = null,
  val display: String? = null,
  val fingerprint: String? = null,
  val hardware: String? = null,
  val host: String? = null,
  val id: String? = null,
  val product: String? = null,
  val tags: String? = null,
  val buildType: String? = null,
  val supportedAbis: List<String>? = null,
  val sdkInt: Int? = null,
  val codename: String? = null,
  val incremental: String? = null,
  val securityPatch: String? = null,
  val baseOs: String? = null,
  val socManufacturer: String? = null,
  val socModel: String? = null,
  val buildTimeMs: Long? = null,
) {
  fun toRawMap(): Map<String, Any> = rawSignalMap(
    "board" to board,
    "bootloader" to bootloader,
    "device" to device,
    "display" to display,
    "fingerprint" to fingerprint,
    "hardware" to hardware,
    "host" to host,
    "id" to id,
    "product" to product,
    "tags" to tags,
    "buildType" to buildType,
    "supportedAbis" to supportedAbis,
    "sdkInt" to sdkInt,
    "codename" to codename,
    "incremental" to incremental,
    "securityPatch" to securityPatch,
    "baseOs" to baseOs,
    "socManufacturer" to socManufacturer,
    "socModel" to socModel,
    "buildTimeMs" to buildTimeMs,
  )
}

/** Permission-free device and OS identity observations. */
data class DeviceIdentitySignals(
  val manufacturer: String,
  val model: String,
  val brand: String,
  val systemVersion: String,
  val isTablet: Boolean,
  val osBuild: String? = null,
  val kernelVersion: String? = null,
  val kernelOsRelease: String? = null,
  val kernelOsType: String? = null,
  val androidBuild: AndroidBuildSignals,
) {
  fun toRawMap(): Map<String, Any> = rawSignalMap(
    "manufacturer" to manufacturer,
    "model" to model,
    "brand" to brand,
    "systemName" to "android",
    "systemVersion" to systemVersion,
    "isTablet" to isTablet,
    "osBuild" to osBuild,
    "kernelVersion" to kernelVersion,
    "kernelOsRelease" to kernelOsRelease,
    "kernelOsType" to kernelOsType,
    "androidBuild" to androidBuild.toRawMap(),
  )
}

/** Locale and regional-format observations. */
data class LocaleSignals(
  val language: String? = null,
  val country: String? = null,
  val languages: List<String>,
  val timezoneId: String? = null,
  val timezoneOffsetMinutes: Int? = null,
  val uses24HourClock: Boolean? = null,
  val currencyCode: String? = null,
  val decimalSeparator: String? = null,
  val groupingSeparator: String? = null,
  val firstDayOfWeek: Int? = null,
  val keyboardLanguages: List<String>? = null,
) {
  fun toRawMap(): Map<String, Any> = rawSignalMap(
    "language" to language,
    "country" to country,
    "languages" to languages,
    "timezoneId" to timezoneId,
    "timezoneOffsetMinutes" to timezoneOffsetMinutes,
    "uses24HourClock" to uses24HourClock,
    "currencyCode" to currencyCode,
    "decimalSeparator" to decimalSeparator,
    "groupingSeparator" to groupingSeparator,
    "firstDayOfWeek" to firstDayOfWeek,
    "keyboardLanguages" to keyboardLanguages,
  )
}

private fun rawSignalMap(vararg values: Pair<String, Any?>): Map<String, Any> = buildMap {
  for ((key, value) in values) {
    if (value != null) put(key, value)
  }
}
