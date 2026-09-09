package io.github.afanasievn.devicerisksignals.active

/**
 * Raw observations from the single-port loopback frida-server scan.
 *
 * These are observations, not a verdict: `defaultPortOpen` says only that something accepted a TCP
 * connection on the scanned loopback port, and `fridaHandshakeReject` says only that the first
 * bytes read back looked like a D-Bus/frida `REJECT` line. Neither field identifies or
 * authenticates the listening service. Unavailable fields are omitted rather than defaulted.
 */
data class FridaScanSignals(
  val scanPerformed: Boolean? = null,
  val scannedPort: Int? = null,
  val defaultPortOpen: Boolean? = null,
  val fridaHandshakeReject: Boolean? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    scanPerformed?.let { put("scanPerformed", it) }
    scannedPort?.let { put("scannedPort", it) }
    defaultPortOpen?.let { put("defaultPortOpen", it) }
    fridaHandshakeReject?.let { put("fridaHandshakeReject", it) }
  }
}
