package io.github.afanasievn.devicerisksignals

/** Permission observations and an optional cached fix. No fix means no mock-provider observation. */
data class GeolocationSignals(
  val hasCoarsePermission: Boolean? = null,
  val authorizationStatus: String? = null,
  val locationServicesEnabled: Boolean? = null,
  val gnssSupported: Boolean? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val accuracyMeters: Double? = null,
  val altitudeMeters: Double? = null,
  val provider: String? = null,
  val isFromMockProvider: Boolean? = null,
  /**
   * Age of the cached fix in milliseconds. `Long` because an `Int` overflows past ~24.86 days and
   * a cached fix — or a device clock moved backwards — can easily exceed that.
   */
  val locationAgeMs: Long? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    hasCoarsePermission?.let { put("hasCoarsePermission", it) }
    authorizationStatus?.let { put("authorizationStatus", it) }
    locationServicesEnabled?.let { put("locationServicesEnabled", it) }
    gnssSupported?.let { put("gnssSupported", it) }
    latitude?.let { put("latitude", it) }
    longitude?.let { put("longitude", it) }
    accuracyMeters?.let { put("accuracyMeters", it) }
    altitudeMeters?.let { put("altitudeMeters", it) }
    provider?.let { put("provider", it) }
    isFromMockProvider?.let { put("isFromMockProvider", it) }
    locationAgeMs?.let { put("locationAgeMs", it) }
  }
}
