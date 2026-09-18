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
 * - The module declares no permission and no manifest entry, but the probe needs the HOST to have
 *   declared `INTERNET`: an app outside the `inet` group cannot open a socket at all, loopback
 *   included, and every flag then reads false exactly as if nothing were listening. Verified on a
 *   device. `INTERNET` is a normal permission and raises no prompt.
 * - The calls BLOCK. The caller must run them off the UI thread; on Android a main-thread call
 *   raises `NetworkOnMainThreadException`.
 * - Raw observations only. Nothing here scores, aggregates, or returns a trusted/untrusted verdict,
 *   and no persistent identifier is produced.
 *
 * ## Declared default: the frida scan is ENABLED by default
 *
 * This component has no probe registry and no configuration object of its own — a native host gets
 * whatever it calls. That made the component's default implicit, so it is declared here instead, as
 * [FRIDA_SCAN_ENABLED_BY_DEFAULT], and pinned by a test against the React Native probe catalog
 * (`contract/source/probe-catalog.source.json`, probe [FRIDA_SCAN_PROBE_ID]) so the two cannot
 * drift apart silently. A host that assembles its own probe set should read that constant rather
 * than hard-code `true`.
 *
 * ### Why this is an exception to "new sensitive or expensive probes ship disabled"
 *
 * The repository rule (`AGENTS.md`) is that a new sensitive or expensive probe ships DISABLED until
 * representative physical-device QA and a documented benchmark justify enabling it. This probe is
 * exempt for one reason only: **it is not new.** It shipped enabled in the React Native package
 * before extraction, and ADR-0003 (`docs/adr/0003-active-loopback-probe-component.md`)
 * deliberately preserved that default so the extraction flipped nothing silently. Turning it off
 * here would itself be the behavior change, and it would be a change made by a refactor rather than
 * by a product decision.
 *
 * State plainly what that exemption does NOT mean:
 * - Representative physical-device QA has **NOT** happened. Behavior against a real frida-server,
 *   OEM builds, IPv6-only loopback stacks, hosts with a captive local proxy and latency under load
 *   is still unverified; JVM socket tests are not device validation.
 * - No benchmark justifies the cost. The bound is a documented worst case
 *   (`FridaScanCollector.WORST_CASE_TIMEOUT_MS`), not a measurement.
 * - "Enabled by default" is not a recommendation. The probe is sensitive (it is the only socket I/O
 *   in the ecosystem, and it is inert without host-declared `INTERNET`), and its observations prove
 *   less than they appear to — see the component README.
 *
 * Whether an active probe *should* stay on by default remains an open product question in the
 * migration checklist. Until it is answered, the answer in code is this constant.
 */
class DeviceRiskActiveProbes {

  /**
   * Attempts one TCP connect to 127.0.0.1:27042 and an unauthenticated `AUTH` handshake, bounded by
   * a 700 ms connect budget and a separate 800 ms read budget (worst case 1500 ms). Must be called
   * from a background thread.
   */
  fun collectFridaScan(): FridaScanSignals = FridaScanCollector().collect()

  companion object {
    /**
     * The probe id this component collects, as spelled in the shared probe catalog. Declared so the
     * component's default can be pinned to the catalog entry by id rather than by convention.
     */
    const val FRIDA_SCAN_PROBE_ID = "os_integrity_frida_scan"

    /**
     * The declared default state of [collectFridaScan]: **enabled**.
     *
     * This mirrors `enabledByDefault` for [FRIDA_SCAN_PROBE_ID] in the shared probe catalog, which
     * is what the React Native package ships. See the class KDoc for why this probe is exempt from
     * the "new sensitive probes ship disabled" rule, and for what that exemption does not claim.
     */
    const val FRIDA_SCAN_ENABLED_BY_DEFAULT = true
  }
}
