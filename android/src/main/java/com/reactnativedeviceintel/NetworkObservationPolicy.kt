package com.reactnativedeviceintel

internal data class BaseConnectivityObservation(
  val isConnected: Boolean,
  val connectionType: String?,
)

/**
 * Keeps "could not observe" separate from a successfully observed disconnected state.
 * Nullable inputs mean the corresponding platform read failed or was unavailable.
 */
internal object NetworkObservationPolicy {
  fun connectivity(
    hasPermission: Boolean,
    activeNetworkPresent: Boolean?,
    capabilitiesPresent: Boolean?,
  ): BaseConnectivityObservation? {
    if (!hasPermission || activeNetworkPresent == null) return null
    if (!activeNetworkPresent) return BaseConnectivityObservation(false, "none")
    if (capabilitiesPresent != true) return null
    return BaseConnectivityObservation(true, null)
  }
}
