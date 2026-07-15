package com.reactnativedeviceintel

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ACTIVE tamper probe: attempts a TCP connect to frida-server's default listener (127.0.0.1:27042).
 * Isolated from the fast [OsIntegrityProvider] bundle because it does BLOCKING socket I/O — the
 * matching JS probe (`os_integrity_frida_scan`) gives it a longer timeout and its own kill-switch.
 *
 * TurboModule promise methods do not run on the main/UI thread, so this connect does not trip
 * NetworkOnMainThreadException; the short connect timeout bounds the worst case regardless.
 */
class FridaScanProvider {

  fun getFridaScanSignals(): WritableMap {
    val map = Arguments.createMap()
    map.putBoolean("scanPerformed", true)
    map.putInt("scannedPort", FRIDA_DEFAULT_PORT)
    map.putBoolean("defaultPortOpen", isPortOpen("127.0.0.1", FRIDA_DEFAULT_PORT, CONNECT_TIMEOUT_MS))
    return map
  }

  private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        true
      }
    } catch (e: Exception) {
      false
    }
  }

  companion object {
    private const val FRIDA_DEFAULT_PORT = 27042
    private const val CONNECT_TIMEOUT_MS = 700
  }
}
