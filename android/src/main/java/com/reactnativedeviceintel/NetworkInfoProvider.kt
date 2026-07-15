package com.reactnativedeviceintel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
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

  fun getNetworkSignals(): WritableMap {
    val map = Arguments.createMap()

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps: NetworkCapabilities? = try {
      cm?.getNetworkCapabilities(cm.activeNetwork)
    } catch (e: Exception) {
      null
    }

    if (caps != null) {
      map.putBoolean("isConnected", true)
      map.putString("connectionType", connectionType(caps))
      map.putBoolean(
        "isMetered",
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
      )
      map.putBoolean("isVpnActive", caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
      val down = caps.linkDownstreamBandwidthKbps
      val up = caps.linkUpstreamBandwidthKbps
      if (down > 0) map.putInt("linkDownstreamKbps", down)
      if (up > 0) map.putInt("linkUpstreamKbps", up)
    } else {
      map.putBoolean("isConnected", false)
      map.putString("connectionType", "none")
      // Fall back to interface scan for VPN presence even when there's no active default network.
      map.putBoolean("isVpnActive", vpnInterfacePresent())
    }

    // Interface topology — reveals tun/tap/ppp VPN overlays regardless of the capabilities read.
    val (names, addresses) = interfaceInventory()
    map.putArray("interfaceNames", toStringArray(names))
    map.putArray("localIpAddresses", toStringArray(addresses))

    // System HTTP proxy.
    val proxyHost = safeString { System.getProperty("http.proxyHost") }
    val proxyPort = safeString { System.getProperty("http.proxyPort") }
    val proxyConfigured = !proxyHost.isNullOrEmpty()
    map.putBoolean("isProxyConfigured", proxyConfigured)
    if (proxyConfigured) {
      map.putString("proxyHost", proxyHost)
      proxyPort?.toIntOrNull()?.let { map.putInt("proxyPort", it) }
    }

    addTrafficCounters(map)
    return map
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

  private fun vpnInterfacePresent(): Boolean = interfaceInventory().first.any { isVpnInterfaceName(it) }

  private fun isVpnInterfaceName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.startsWith("tun") || lower.startsWith("tap") || lower.startsWith("ppp") ||
      lower.startsWith("utun") || lower.startsWith("ipsec")
  }

  private fun interfaceInventory(): Pair<List<String>, List<String>> {
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
      // No interface visibility — return whatever we gathered.
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
}
