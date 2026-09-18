package io.github.afanasievn.devicerisksignals.active

import java.net.InetSocketAddress
import java.net.Socket

/**
 * ACTIVE tamper probe: attempts one TCP connect to frida-server's default listener
 * (127.0.0.1:27042) and records whether the bytes read back begin with a D-Bus/frida `REJECT`
 * line.
 *
 * This is the one component that performs socket I/O. It is deliberately separate from the passive
 * `sdks/android` core, which never opens a socket: the connect is BLOCKING, so the caller must run
 * `collect()` on a background thread (a main-thread call raises `NetworkOnMainThreadException` on
 * Android). Only the loopback address and only the single documented port are touched — this is not
 * a port scanner and it sends nothing off-device.
 *
 * ## Timeout budget
 *
 * Connect and read have SEPARATE budgets, so a slow connect can no longer eat the time available to
 * read the reply and a silent listener can no longer hold the call for a long connect budget:
 *
 * - connect: [CONNECT_TIMEOUT_MS] ms, spent on the TCP connect only.
 * - read: [READ_TIMEOUT_MS] ms, a budget for the WHOLE handshake read, not per `read` call. Each
 *   individual read is given whatever is left of it.
 *
 * The worst case for one `collect()` is therefore [WORST_CASE_TIMEOUT_MS] ms
 * (connect + read) plus scheduling. A connect that fails or times out never goes on to spend the
 * read budget.
 *
 * ## Behavior change from the ported React Native provider
 *
 * The original performed exactly one `read` of up to six bytes with a single 700 ms value used as
 * both connect and read timeout, so a reply that arrived in pieces, or later than that one value,
 * read as "no REJECT". The reply is a TCP stream, not a message, and is now reassembled across
 * reads until the six-byte prefix is decided or the read budget is spent. `fridaHandshakeReject`
 * is therefore true for some replies that previously reported false. See the component README and
 * `docs/DATA_DICTIONARY.md`.
 *
 * Host, port and the two timeouts are constructor seams so tests can point the collector at a local
 * `ServerSocket`. They are not part of the published surface: [DeviceRiskActiveProbes] always uses
 * the production defaults.
 */
internal class FridaScanCollector(
  private val host: String = LOOPBACK_HOST,
  private val port: Int = FRIDA_DEFAULT_PORT,
  private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
  private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {

  fun collect(): FridaScanSignals {
    val probe = probePort()
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

  private fun probePort(): PortProbe {
    return try {
      // `use` closes the socket on every path, including the throwing ones.
      Socket().use { socket ->
        // Only the connect budget is spent here. A connect that never completes ends after
        // `connectTimeoutMs` and the read budget is never entered.
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        PortProbe(open = true, handshakeReject = handshakeRejects(socket))
      }
    } catch (e: Exception) {
      // PRESERVED DEFECT: `defaultPortOpen = false` conflates connection refused, connect timeout
      // and any other socket failure. The consumer cannot tell "nothing listens" from "we could
      // not find out".
      PortProbe(open = false, handshakeReject = false)
    }
  }

  private fun handshakeRejects(socket: Socket): Boolean {
    return try {
      val out = socket.getOutputStream()
      out.write(0x00)
      out.write("AUTH\r\n".toByteArray(Charsets.US_ASCII))
      out.flush()
      readsRejectPrefix(socket)
    } catch (e: Exception) {
      // PRESERVED DEFECT: every handshake failure (read timeout, reset, EOF, write error)
      // collapses to `false`, indistinguishable from a listener that answered non-REJECT.
      false
    }
  }

  /**
   * Reads until the `REJECT` prefix is decided or the read budget is spent.
   *
   * The budget covers the whole read, not each `read` call: every iteration gets only what is left,
   * so a listener that trickles one byte at a time cannot extend the call. The answer is decided as
   * soon as it can be — a byte that disagrees with the prefix ends the read immediately, without
   * waiting for six bytes or for the budget.
   */
  private fun readsRejectPrefix(socket: Socket): Boolean {
    val expected = REJECT_PREFIX.toByteArray(Charsets.US_ASCII)
    val buffer = ByteArray(expected.size)
    val input = socket.getInputStream()
    val deadlineNanos = System.nanoTime() + readTimeoutMs.toLong() * NANOS_PER_MILLI
    var filled = 0

    while (filled < expected.size) {
      val remainingMs = (deadlineNanos - System.nanoTime()) / NANOS_PER_MILLI
      // Budget spent with the prefix still undecided. Same collapsed `false` as any other
      // handshake failure.
      if (remainingMs <= 0L) return false
      socket.soTimeout = remainingMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

      val read = input.read(buffer, filled, expected.size - filled)
      // EOF before the prefix was decided.
      if (read <= 0) return false

      val from = filled
      filled += read
      for (index in from until filled) {
        if (buffer[index] != expected[index]) return false
      }
    }
    return true
  }

  internal companion object {
    private const val LOOPBACK_HOST = "127.0.0.1"
    private const val FRIDA_DEFAULT_PORT = 27042
    private const val REJECT_PREFIX = "REJECT"
    private const val NANOS_PER_MILLI = 1_000_000L

    /** Budget for the TCP connect alone. Unchanged from the ported React Native provider. */
    const val CONNECT_TIMEOUT_MS = 700

    /**
     * Budget for the whole handshake read. Deliberately a little larger than the old shared 700 ms
     * so that every reply the single-read version could have caught still fits, with slack for a
     * reply that arrives in more than one TCP segment.
     */
    const val READ_TIMEOUT_MS = 800

    /** Documented worst case for one `collect()`: the connect budget plus the read budget. */
    const val WORST_CASE_TIMEOUT_MS = CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS
  }
}
