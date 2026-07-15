package com.reactnativedeviceintel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

/**
 * geolocation — OPPORTUNISTIC. Never requests permission: reads a last-known fix only if COARSE is
 * already granted, and always reports the mock-provider flag on whatever fix exists. Coarse is fine —
 * the fraud value is the mock-location tell and coordinate-vs-claimed-address mismatch.
 */
class GeolocationInfoProvider(private val context: Context) {

  fun getGeolocationSignals(): WritableMap {
    val map = Arguments.createMap()

    val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    map.putBoolean("hasCoarsePermission", hasCoarse)
    map.putString("authorizationStatus", if (hasCoarse || hasFine) "granted" else "denied")
    map.putBoolean(
      "gnssSupported",
      safeBool { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) },
    )

    if (hasCoarse || hasFine) {
      val location = freshestLastKnownLocation()
      if (location != null) {
        map.putDouble("latitude", location.latitude)
        map.putDouble("longitude", location.longitude)
        if (location.hasAccuracy()) map.putDouble("accuracyMeters", location.accuracy.toDouble())
        if (location.hasAltitude()) map.putDouble("altitudeMeters", location.altitude)
        location.provider?.let { map.putString("provider", it) }
        map.putBoolean("isFromMockProvider", isMock(location))
        map.putInt("locationAgeMs", (System.currentTimeMillis() - location.time).coerceAtLeast(0).toInt())
      }
    }

    return map
  }

  private fun freshestLastKnownLocation(): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    var best: Location? = null
    for (provider in PROVIDERS) {
      try {
        val loc = lm.getLastKnownLocation(provider) ?: continue
        if (best == null || loc.time > best.time) best = loc
      } catch (e: Exception) {
        // Provider disabled or permission edge — skip, keep any we already have.
      }
    }
    return best
  }

  private fun isMock(location: Location): Boolean = safeBool {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      location.isMock
    } else {
      @Suppress("DEPRECATION")
      location.isFromMockProvider
    }
  }

  private fun hasPermission(permission: String): Boolean = safeBool {
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
  }

  private inline fun safeBool(block: () -> Boolean): Boolean = try {
    block()
  } catch (e: Throwable) {
    false
  }

  companion object {
    private val PROVIDERS = listOf(
      LocationManager.NETWORK_PROVIDER,
      LocationManager.GPS_PROVIDER,
      LocationManager.PASSIVE_PROVIDER,
    )
  }
}
