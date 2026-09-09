package io.github.afanasievn.devicerisksignals.active

/**
 * Standalone entry point for ACTIVE device-risk probes.
 *
 * This component is the boundary exception in the ecosystem: it performs LOOPBACK socket I/O
 * (127.0.0.1 only). The passive `sdks/android` core never opens a socket, and this module is kept
 * separate and optional precisely so that a host can adopt passive collection without taking on
 * any socket I/O at all.
 *
 * Contract:
 * - Loopback only. No off-device request, no vendor endpoint, no hostname resolution beyond
 *   127.0.0.1, and no port scanning past the single documented port.
 * - No Android permission is required and none is requested; the module declares no manifest entry.
 * - The calls BLOCK. The caller must run them off the UI thread; on Android a main-thread call
 *   raises `NetworkOnMainThreadException`.
 * - Raw observations only. Nothing here scores, aggregates, or returns a trusted/untrusted verdict,
 *   and no persistent identifier is produced.
 */
class DeviceRiskActiveProbes {

  /**
   * Attempts one TCP connect to 127.0.0.1:27042 and an unauthenticated `AUTH` handshake, bounded by
   * a 700 ms connect and read timeout. Must be called from a background thread.
   */
  fun collectFridaScan(): FridaScanSignals = FridaScanCollector().collect()
}
