package com.reactnativedeviceintel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.net.NetworkInterface

/**
 * network — connectivity type, VPN/proxy presence, interface topology, and cumulative traffic
 * counters. Permission-free (this package declares no permission; ACCESS_NETWORK_STATE is already in
 * the merged app manifest via netinfo/Firebase/etc., and the reads degrade gracefully without it).
 * No SSID: the
 * app removes ACCESS_FINE_LOCATION (`tools:node="remove"`) by existing decision, and the modern
 * WifiManager SSID read requires it — so `wifiSsid`/`wifiBssid` are intentionally omitted here
 * rather than returning the "<unknown ssid>" placeholder the OS hands back.
 */
class NetworkInfoProvider(private val context: Context) {

  private data class ActiveNetworkSnapshot(
    val activeNetworkPresent: Boolean?,
    val capabilities: NetworkCapabilities?,
    val capabilitiesPresent: Boolean?,
    val linkProperties: LinkProperties?,
  )

  fun getNetworkSignals(): WritableMap {
    val map = Arguments.createMap()

    val hasNetworkState =
      context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val snapshot = if (hasNetworkState && cm != null) readActiveNetwork(cm) else null
    val caps = snapshot?.capabilities
    val linkProperties = snapshot?.linkProperties

    NetworkObservationPolicy.connectivity(
      hasNetworkState,
      snapshot?.activeNetworkPresent,
      snapshot?.capabilitiesPresent,
    )
      ?.let { observation ->
        map.putBoolean("isConnected", observation.isConnected)
        val type = observation.connectionType ?: caps?.let(::connectionType)
        type?.let { map.putString("connectionType", it) }
      }

    if (caps != null) {
      map.putBoolean(
        "isMetered",
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
      )
      map.putBoolean("isVpnActive", caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
      map.putBoolean(
        "isInternetValidated",
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
      )
      map.putBoolean(
        "hasCaptivePortal",
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
      )
      map.putArray("networkTransportTypes", toStringArray(networkTransportTypes(caps)))
      val down = caps.linkDownstreamBandwidthKbps
      val up = caps.linkUpstreamBandwidthKbps
      if (down > 0) map.putInt("linkDownstreamKbps", down)
      if (up > 0) map.putInt("linkUpstreamKbps", up)
    }

    linkProperties?.let { addLinkProperties(map, it) }

    // Interface topology — reveals tun/tap/ppp VPN overlays regardless of the capabilities read.
    val inventory = interfaceInventory()
    inventory?.let { (names, addresses) ->
      map.putArray("interfaceNames", toStringArray(names))
      map.putArray("localIpAddresses", toStringArray(addresses))
      if (caps == null) {
        // Interface state remains observable without ACCESS_NETWORK_STATE.
        map.putBoolean("isVpnActive", names.any(::isVpnInterfaceName))
      }
    }

    // System HTTP proxy.
    val proxyHostRead = runCatching { System.getProperty("http.proxyHost") }
    if (proxyHostRead.isSuccess) {
      val proxyHost = proxyHostRead.getOrNull()
      val proxyConfigured = !proxyHost.isNullOrEmpty()
      map.putBoolean("isProxyConfigured", proxyConfigured)
      if (proxyConfigured) {
        map.putString("proxyHost", proxyHost)
        safeString { System.getProperty("http.proxyPort") }
          ?.toIntOrNull()
          ?.let { map.putInt("proxyPort", it) }
      }
    }

    addTrafficCounters(map)
    return map
  }

  /**
   * Called only after the host app's ACCESS_NETWORK_STATE grant has been verified. Lint cannot
   * propagate that runtime check into this helper, so the suppression is intentionally scoped to
   * the three guarded ConnectivityManager reads rather than the provider or module.
   */
  @SuppressLint("MissingPermission")
  private fun readActiveNetwork(cm: ConnectivityManager): ActiveNetworkSnapshot {
    val activeNetworkRead = runCatching { cm.activeNetwork }
    val activeNetwork = activeNetworkRead.getOrNull()
    val activeNetworkPresent = if (activeNetworkRead.isSuccess) activeNetwork != null else null
    if (activeNetwork == null) {
      return ActiveNetworkSnapshot(activeNetworkPresent, null, null, null)
    }

    val capabilitiesRead = runCatching { cm.getNetworkCapabilities(activeNetwork) }
    val capabilities = capabilitiesRead.getOrNull()
    val capabilitiesPresent = if (capabilitiesRead.isSuccess) capabilities != null else null
    val linkProperties = runCatching { cm.getLinkProperties(activeNetwork) }.getOrNull()
    return ActiveNetworkSnapshot(
      activeNetworkPresent,
      capabilities,
      capabilitiesPresent,
      linkProperties,
    )
  }

  private fun addLinkProperties(map: WritableMap, properties: LinkProperties) {
    safe {
      val dns = properties.dnsServers.mapNotNull { it.hostAddress }.distinct()
      map.putArray("dnsServerAddresses", toStringArray(dns))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      safe { properties.isPrivateDnsActive }
        ?.let { map.putBoolean("isPrivateDnsActive", it) }
      safe { properties.privateDnsServerName }
        ?.takeIf(String::isNotEmpty)
        ?.let { map.putString("privateDnsServerName", it) }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      safe { properties.mtu }
        ?.takeIf { it > 0 }
        ?.let { map.putInt("activeNetworkMtu", it) }
    }
  }

  private fun networkTransportTypes(caps: NetworkCapabilities): List<String> = buildList {
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) add("wifiAware")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
      caps.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) add("lowpan")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) add("usb")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
      caps.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE)) add("satellite")
  }

  /**
   * TrafficStats cumulative byte counters SINCE BOOT (not a rate). Wifi = total − mobile. Returns
   * TrafficStats.UNSUPPORTED (-1) on devices without stats — guard and omit. Permission-free.
   */
  private fun addTrafficCounters(map: WritableMap) {
    val mobileRx = safeLong { TrafficStats.getMobileRxBytes() }
    val mobileTx = safeLong { TrafficStats.getMobileTxBytes() }
    val totalRx = safeLong { TrafficStats.getTotalRxBytes() }
    val totalTx = safeLong { TrafficStats.getTotalTxBytes() }

    if (mobileRx != null && mobileRx >= 0) map.putDouble("mobileRxBytes", mobileRx.toDouble())
    if (mobileTx != null && mobileTx >= 0) map.putDouble("mobileTxBytes", mobileTx.toDouble())
    if (totalRx != null && totalRx >= 0 && mobileRx != null && mobileRx >= 0) {
      map.putDouble("wifiRxBytes", (totalRx - mobileRx).coerceAtLeast(0).toDouble())
    }
    if (totalTx != null && totalTx >= 0 && mobileTx != null && mobileTx >= 0) {
      map.putDouble("wifiTxBytes", (totalTx - mobileTx).coerceAtLeast(0).toDouble())
    }
  }

  private fun connectionType(caps: NetworkCapabilities): String = when {
    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
    else -> "other"
  }

  private fun isVpnInterfaceName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.startsWith("tun") || lower.startsWith("tap") || lower.startsWith("ppp") ||
      lower.startsWith("utun") || lower.startsWith("ipsec")
  }

  private fun interfaceInventory(): Pair<List<String>, List<String>>? {
    val names = mutableListOf<String>()
    val addresses = mutableListOf<String>()
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces() ?: return Pair(names, addresses)
      for (nif in interfaces) {
        try {
          if (!nif.isUp || nif.isLoopback) continue
          names.add(nif.name)
          for (addr in nif.inetAddresses) {
            if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
              addr.hostAddress?.let { addresses.add(it) }
            }
          }
        } catch (e: Exception) {
          // Skip an interface we can't introspect; keep the rest.
        }
      }
    } catch (e: Exception) {
      // No interface visibility — omit instead of treating an empty inventory as a negative result.
      return null
    }
    return Pair(names, addresses)
  }

  private fun toStringArray(values: List<String>): WritableArray {
    val arr = Arguments.createArray()
    for (v in values) arr.pushString(v)
    return arr
  }

  private inline fun safeString(block: () -> String?): String? = try {
    block()
  } catch (e: Throwable) {
    null
  }

  private inline fun safeLong(block: () -> Long): Long? = try {
    block()
  } catch (e: Throwable) {
    null
  }

  private inline fun <T> safe(block: () -> T): T? = try {
    block()
  } catch (e: Throwable) {
    null
  }
}
