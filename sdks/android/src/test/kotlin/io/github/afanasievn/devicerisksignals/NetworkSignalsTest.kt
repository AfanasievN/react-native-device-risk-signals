package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkSignalsTest {
  @Test fun `each builder field maps independently without populating unavailable neighbors`() {
    assertEquals(mapOf("isConnected" to false),
      NetworkSignals.Builder().apply { isConnected = false }.build().toRawMap())
    assertEquals(mapOf("connectionType" to "none"),
      NetworkSignals.Builder().apply { connectionType = "none" }.build().toRawMap())
    assertEquals(mapOf("isMetered" to false),
      NetworkSignals.Builder().apply { isMetered = false }.build().toRawMap())
    assertEquals(mapOf("isVpnActive" to false),
      NetworkSignals.Builder().apply { isVpnActive = false }.build().toRawMap())
    assertEquals(mapOf("isInternetValidated" to false),
      NetworkSignals.Builder().apply { isInternetValidated = false }.build().toRawMap())
    assertEquals(mapOf("hasCaptivePortal" to false),
      NetworkSignals.Builder().apply { hasCaptivePortal = false }.build().toRawMap())
    assertEquals(mapOf("networkTransportTypes" to emptyList<String>()),
      NetworkSignals.Builder().apply { networkTransportTypes = emptyList<String>() }.build().toRawMap())
    assertEquals(mapOf("linkDownstreamKbps" to 123),
      NetworkSignals.Builder().apply { linkDownstreamKbps = 123 }.build().toRawMap())
    assertEquals(mapOf("linkUpstreamKbps" to 456),
      NetworkSignals.Builder().apply { linkUpstreamKbps = 456 }.build().toRawMap())
    assertEquals(mapOf("interfaceNames" to listOf("wlan0")),
      NetworkSignals.Builder().apply { interfaceNames = listOf("wlan0") }.build().toRawMap())
    assertEquals(mapOf("localIpAddresses" to listOf("192.0.2.1")),
      NetworkSignals.Builder().apply { localIpAddresses = listOf("192.0.2.1") }.build().toRawMap())
    assertEquals(mapOf("isProxyConfigured" to false),
      NetworkSignals.Builder().apply { isProxyConfigured = false }.build().toRawMap())
    assertEquals(mapOf("proxyHost" to "proxy.example"),
      NetworkSignals.Builder().apply { proxyHost = "proxy.example" }.build().toRawMap())
    assertEquals(mapOf("proxyPort" to 8080),
      NetworkSignals.Builder().apply { proxyPort = 8080 }.build().toRawMap())
    assertEquals(mapOf("dnsServerAddresses" to listOf("192.0.2.53")),
      NetworkSignals.Builder().apply { dnsServerAddresses = listOf("192.0.2.53") }.build().toRawMap())
    assertEquals(mapOf("isPrivateDnsActive" to false),
      NetworkSignals.Builder().apply { isPrivateDnsActive = false }.build().toRawMap())
    assertEquals(mapOf("privateDnsServerName" to "dns.example"),
      NetworkSignals.Builder().apply { privateDnsServerName = "dns.example" }.build().toRawMap())
    assertEquals(mapOf("activeNetworkMtu" to 1500),
      NetworkSignals.Builder().apply { activeNetworkMtu = 1500 }.build().toRawMap())
    assertEquals(mapOf("mobileRxBytes" to 0.0),
      NetworkSignals.Builder().apply { mobileRxBytes = 0.0 }.build().toRawMap())
    assertEquals(mapOf("mobileTxBytes" to 1.0),
      NetworkSignals.Builder().apply { mobileTxBytes = 1.0 }.build().toRawMap())
    assertEquals(mapOf("wifiRxBytes" to 2.0),
      NetworkSignals.Builder().apply { wifiRxBytes = 2.0 }.build().toRawMap())
    assertEquals(mapOf("wifiTxBytes" to 3.0),
      NetworkSignals.Builder().apply { wifiTxBytes = 3.0 }.build().toRawMap())
  }

  @Test fun `unavailable observations are omitted`() {
    assertEquals(emptyMap<String, Any>(), NetworkSignals().toRawMap())
    assertEquals(NetworkSignals(), NetworkSignals.Builder().build())
  }

  @Test fun `builder preserves every observation including false zero and empty arrays`() {
    val signals = NetworkSignals.Builder().apply {
      isConnected = false
      connectionType = "none"
      isMetered = false
      isVpnActive = false
      isInternetValidated = false
      hasCaptivePortal = false
      networkTransportTypes = emptyList()
      linkDownstreamKbps = 123
      linkUpstreamKbps = 456
      interfaceNames = listOf("wlan0")
      localIpAddresses = listOf("192.0.2.1")
      isProxyConfigured = false
      proxyHost = "proxy.example"
      proxyPort = 8080
      dnsServerAddresses = listOf("192.0.2.53")
      isPrivateDnsActive = false
      privateDnsServerName = "dns.example"
      activeNetworkMtu = 1500
      mobileRxBytes = 0.0
      mobileTxBytes = 1.0
      wifiRxBytes = 2.0
      wifiTxBytes = 3.0
    }.build()
    assertEquals(mapOf<String, Any>(
      "isConnected" to false, "connectionType" to "none", "isMetered" to false,
      "isVpnActive" to false, "isInternetValidated" to false, "hasCaptivePortal" to false,
      "networkTransportTypes" to emptyList<String>(), "linkDownstreamKbps" to 123,
      "linkUpstreamKbps" to 456, "interfaceNames" to listOf("wlan0"),
      "localIpAddresses" to listOf("192.0.2.1"), "isProxyConfigured" to false,
      "proxyHost" to "proxy.example", "proxyPort" to 8080,
      "dnsServerAddresses" to listOf("192.0.2.53"), "isPrivateDnsActive" to false,
      "privateDnsServerName" to "dns.example", "activeNetworkMtu" to 1500,
      "mobileRxBytes" to 0.0, "mobileTxBytes" to 1.0, "wifiRxBytes" to 2.0,
      "wifiTxBytes" to 3.0,
    ), signals.toRawMap())
  }
}
