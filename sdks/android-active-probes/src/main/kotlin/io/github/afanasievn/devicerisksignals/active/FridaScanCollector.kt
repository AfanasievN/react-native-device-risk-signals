package io.github.afanasievn.devicerisksignals.active

import java.net.InetSocketAddress
import java.net.Socket

/**
 * ACTIVE tamper probe: attempts one TCP connect to frida-server's default listener
 * (127.0.0.1:27042) and records whether the first bytes read back look like a D-Bus/frida `REJECT`
 * line.
 *
 * This is the one component that performs socket I/O. It is deliberately separate from the passive
 * `sdks/android` core, which never opens a socket: the connect is BLOCKING, so the caller must run
 * `collect()` on a background thread (a main-thread call raises `NetworkOnMainThreadException` on
 * Android). Only the loopback address and only the single documented port are touched — this is not
 * a port scanner and it sends nothing off-device.
 *
 * Ported unchanged from the React Native `FridaScanProvider`; the observable behavior (emitted
 * keys, types, host, port, 700 ms timeout, single 6-byte read, `REJECT` prefix check,
 * swallow-to-false) is reproduced exactly, including the defects documented below.
 *
 * Host, port and timeout are constructor seams so tests can point the collector at a local
 * `ServerSocket`. They are not part of the published surface: [DeviceRiskActiveProbes] always uses
 * the production defaults.
 */
internal class FridaScanCollector(
  private val host: String = LOOPBACK_HOST,
  private val port: Int = FRIDA_DEFAULT_PORT,
  private val timeoutMs: Int = CONNECT_TIMEOUT_MS,
) {

  fun collect(): FridaScanSignals {
    val probe = probePort(host, port, timeoutMs)
    return FridaScanSignals(
      // `scanPerformed` is always true: it records that the attempt was made, not that it succeeded.
      scanPerformed = true,
      scannedPort = port,
      // A plain connect only says "something listens on this port".
      defaultPortOpen = probe.open,
      // PRESERVED DEFECT: a REJECT-like reply does not authenticate any service. Any listener can
      // answer with these bytes, so this flag is evidence, never identification.
      fridaHandshakeReject = probe.handshakeReject,
    )
  }

  private data class PortProbe(val open: Boolean, val handshakeReject: Boolean)

  private fun probePort(host: String, port: Int, timeoutMs: Int): PortProbe {
    return try {
      // `use` closes the socket on every path, including the throwing ones.
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        val reject = try {
          // The same value is used as connect and read timeout, as in the original probe.
          socket.soTimeout = timeoutMs
          val out = socket.getOutputStream()
          out.write(0x00)
          out.write("AUTH\r\n".toByteArray(Charsets.US_ASCII))
          out.flush()
          val buf = ByteArray(HANDSHAKE_READ_BYTES)
          // PRESERVED DEFECT: exactly one `read` of up to 6 bytes. A partial or slow response reads
          // as "no REJECT" — the prefix is never reassembled across reads.
          val read = socket.getInputStream().read(buf)
          read > 0 && String(buf, 0, read, Charsets.US_ASCII).startsWith("REJECT")
        } catch (e: Exception) {
          // PRESERVED DEFECT: every handshake failure (read timeout, reset, EOF, write error)
          // collapses to `false`, indistinguishable from a listener that answered non-REJECT.
          false
        }
        PortProbe(open = true, handshakeReject = reject)
      }
    } catch (e: Exception) {
      // PRESERVED DEFECT: `defaultPortOpen = false` conflates connection refused, connect timeout
      // and any other socket failure. The consumer cannot tell "nothing listens" from "we could
      // not find out".
      PortProbe(open = false, handshakeReject = false)
    }
  }

  companion object {
    private const val LOOPBACK_HOST = "127.0.0.1"
    private const val FRIDA_DEFAULT_PORT = 27042
    private const val CONNECT_TIMEOUT_MS = 700
    private const val HANDSHAKE_READ_BYTES = 6
  }
}
