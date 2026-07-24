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
    val probe = probePort("127.0.0.1", FRIDA_DEFAULT_PORT, CONNECT_TIMEOUT_MS)
    map.putBoolean("defaultPortOpen", probe.open)
    // A plain connect only says "something listens on 27042"; the D-Bus/frida AUTH handshake below
    // confirms it is actually frida-server (it answers "REJECT"), distinguishing it from any other
    // service that happens to bind that port.
    map.putBoolean("fridaHandshakeReject", probe.handshakeReject)
    return map
  }

  private data class PortProbe(val open: Boolean, val handshakeReject: Boolean)

  private fun probePort(host: String, port: Int, timeoutMs: Int): PortProbe {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        val reject = try {
          socket.soTimeout = timeoutMs
          val out = socket.getOutputStream()
          out.write(0x00)
          out.write("AUTH\r\n".toByteArray(Charsets.US_ASCII))
          out.flush()
          val buf = ByteArray(6)
          val read = socket.getInputStream().read(buf)
          read > 0 && String(buf, 0, read, Charsets.US_ASCII).startsWith("REJECT")
        } catch (e: Exception) {
          false
        }
        PortProbe(open = true, handshakeReject = reject)
      }
    } catch (e: Exception) {
      PortProbe(open = false, handshakeReject = false)
    }
  }

  companion object {
    private const val FRIDA_DEFAULT_PORT = 27042
    private const val CONNECT_TIMEOUT_MS = 700
  }
}
