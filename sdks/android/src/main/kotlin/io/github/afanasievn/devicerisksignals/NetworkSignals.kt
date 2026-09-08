package io.github.afanasievn.devicerisksignals

/**
 * Passive network observations. The host owns ACCESS_NETWORK_STATE; unavailable values are omitted.
 * Local addresses and topology are sensitive. Traffic counters preserve the existing Double format;
 * wifi counters mean total minus mobile traffic and can include other non-mobile transports.
 */
data class NetworkSignals(
  val isConnected: Boolean? = null,
  val connectionType: String? = null,
  val isMetered: Boolean? = null,
  val isVpnActive: Boolean? = null,
  val isInternetValidated: Boolean? = null,
  val hasCaptivePortal: Boolean? = null,
  val networkTransportTypes: List<String>? = null,
  val linkDownstreamKbps: Int? = null,
  val linkUpstreamKbps: Int? = null,
  val interfaceNames: List<String>? = null,
  val localIpAddresses: List<String>? = null,
  val isProxyConfigured: Boolean? = null,
  val proxyHost: String? = null,
  val proxyPort: Int? = null,
  val dnsServerAddresses: List<String>? = null,
  val isPrivateDnsActive: Boolean? = null,
  val privateDnsServerName: String? = null,
  val activeNetworkMtu: Int? = null,
  val mobileRxBytes: Double? = null,
  val mobileTxBytes: Double? = null,
  val wifiRxBytes: Double? = null,
  val wifiTxBytes: Double? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    isConnected?.let { put("isConnected", it) }
    connectionType?.let { put("connectionType", it) }
    isMetered?.let { put("isMetered", it) }
    isVpnActive?.let { put("isVpnActive", it) }
    isInternetValidated?.let { put("isInternetValidated", it) }
    hasCaptivePortal?.let { put("hasCaptivePortal", it) }
    networkTransportTypes?.let { put("networkTransportTypes", it) }
    linkDownstreamKbps?.let { put("linkDownstreamKbps", it) }
    linkUpstreamKbps?.let { put("linkUpstreamKbps", it) }
    interfaceNames?.let { put("interfaceNames", it) }
    localIpAddresses?.let { put("localIpAddresses", it) }
    isProxyConfigured?.let { put("isProxyConfigured", it) }
    proxyHost?.let { put("proxyHost", it) }
    proxyPort?.let { put("proxyPort", it) }
    dnsServerAddresses?.let { put("dnsServerAddresses", it) }
    isPrivateDnsActive?.let { put("isPrivateDnsActive", it) }
    privateDnsServerName?.let { put("privateDnsServerName", it) }
    activeNetworkMtu?.let { put("activeNetworkMtu", it) }
    mobileRxBytes?.let { put("mobileRxBytes", it) }
    mobileTxBytes?.let { put("mobileTxBytes", it) }
    wifiRxBytes?.let { put("wifiRxBytes", it) }
    wifiTxBytes?.let { put("wifiTxBytes", it) }
  }

  internal class Builder {
    var isConnected: Boolean? = null
    var connectionType: String? = null
    var isMetered: Boolean? = null
    var isVpnActive: Boolean? = null
    var isInternetValidated: Boolean? = null
    var hasCaptivePortal: Boolean? = null
    var networkTransportTypes: List<String>? = null
    var linkDownstreamKbps: Int? = null
    var linkUpstreamKbps: Int? = null
    var interfaceNames: List<String>? = null
    var localIpAddresses: List<String>? = null
    var isProxyConfigured: Boolean? = null
    var proxyHost: String? = null
    var proxyPort: Int? = null
    var dnsServerAddresses: List<String>? = null
    var isPrivateDnsActive: Boolean? = null
    var privateDnsServerName: String? = null
    var activeNetworkMtu: Int? = null
    var mobileRxBytes: Double? = null
    var mobileTxBytes: Double? = null
    var wifiRxBytes: Double? = null
    var wifiTxBytes: Double? = null

    fun build(): NetworkSignals = NetworkSignals(
      isConnected = isConnected,
      connectionType = connectionType,
      isMetered = isMetered,
      isVpnActive = isVpnActive,
      isInternetValidated = isInternetValidated,
      hasCaptivePortal = hasCaptivePortal,
      networkTransportTypes = networkTransportTypes,
      linkDownstreamKbps = linkDownstreamKbps,
      linkUpstreamKbps = linkUpstreamKbps,
      interfaceNames = interfaceNames,
      localIpAddresses = localIpAddresses,
      isProxyConfigured = isProxyConfigured,
      proxyHost = proxyHost,
      proxyPort = proxyPort,
      dnsServerAddresses = dnsServerAddresses,
      isPrivateDnsActive = isPrivateDnsActive,
      privateDnsServerName = privateDnsServerName,
      activeNetworkMtu = activeNetworkMtu,
      mobileRxBytes = mobileRxBytes,
      mobileTxBytes = mobileTxBytes,
      wifiRxBytes = wifiRxBytes,
      wifiTxBytes = wifiTxBytes,
    )
  }
}
