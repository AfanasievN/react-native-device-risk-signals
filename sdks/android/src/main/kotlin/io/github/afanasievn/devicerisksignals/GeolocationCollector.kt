package io.github.afanasievn.devicerisksignals

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build

/**
 * Opportunistic cached-location reads with an already granted COARSE or FINE permission.
 * Never requests permission or a fresh fix. Coordinates and mock-provider observations are emitted
 * only when a cached fix exists; interpretation belongs to the caller.
 */
internal class GeolocationCollector(private val context: Context) {

  fun collect(): GeolocationSignals {
    val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    val servicesEnabled = locationServicesEnabled()
    val gnss = safeBool { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) }
    val observations = GeolocationSignals(
      hasCoarsePermission = hasCoarse,
      authorizationStatus = if (hasCoarse || hasFine) "granted" else "denied",
      locationServicesEnabled = servicesEnabled,
      gnssSupported = gnss,
    )

    if (hasCoarse || hasFine) {
      val location = freshestLastKnownLocation()
      if (location != null) {
        return observations.copy(
          latitude = location.latitude,
          longitude = location.longitude,
          accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
          altitudeMeters = if (location.hasAltitude()) location.altitude else null,
          provider = location.provider,
          isFromMockProvider = isMock(location),
          locationAgeMs = locationAgeMs(System.currentTimeMillis(), location.time),
        )
      }
    }

    return observations
  }

  @SuppressLint("MissingPermission")
  private fun freshestLastKnownLocation(): Location? {
    // Re-check at the exact protected API boundary. Permission can be revoked between the outer
    // observation and this call, and Android lint intentionally does not trust a distant check.
    val coarseGranted =
      context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val fineGranted =
      context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!coarseGranted && !fineGranted) return null

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

  private fun locationServicesEnabled(): Boolean? = safeValue {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      ?: return@safeValue null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      manager.isLocationEnabled
    } else {
      @Suppress("DEPRECATION")
      manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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

  private inline fun <T> safeValue(block: () -> T?): T? = try {
    block()
  } catch (e: Throwable) {
    null
  }

  companion object {
    /**
     * Age of a cached fix in milliseconds, clamped at zero for a fix timestamped in the future.
     * Kept as a `Long`: narrowing to `Int` wraps past `Int.MAX_VALUE` ms (~24.86 days), which a
     * long-cached fix or a backwards device clock reaches routinely.
     */
    internal fun locationAgeMs(nowMs: Long, fixTimeMs: Long): Long =
      (nowMs - fixTimeMs).coerceAtLeast(0)

    private val PROVIDERS = listOf(
      LocationManager.NETWORK_PROVIDER,
      LocationManager.GPS_PROVIDER,
      LocationManager.PASSIVE_PROVIDER,
    )
  }
}
