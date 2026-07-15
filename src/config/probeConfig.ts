import type {Probe} from "../probes/types";

/**
 * Field-level selection for a single probe's collected data. Applied to the TOP-LEVEL keys of the
 * probe's success payload AFTER collection, before the event is assembled/sent — so it controls both
 * what is kept and what is transmitted. Use `include` for an allowlist (keep only these), `exclude`
 * for a denylist (drop these). Omit `fields` to keep everything.
 *
 * NOTE: this trims an ALREADY-COLLECTED payload. To avoid even computing/touching a sensitive or
 * expensive signal, disable the whole probe (`enabled: false`) or split it into a finer-grained probe
 * (as os_integrity is split into the fast bundle / frida scan / fork test) — projection cannot gate a
 * value that the native method already produced.
 */
export type FieldSelection = {include: string[]} | {exclude: string[]};

export type ProbeOverride = {
  enabled?: boolean;
  timeoutMs?: number;
  /** Field selection applied to what is COLLECTED — the payload returned from collect() and kept for
   * on-device use. */
  fields?: FieldSelection;
  /** Field selection applied ADDITIONALLY at send time (collectAndSend), on top of `fields`. Lets you
   * collect a fuller payload locally but transmit a narrower one — the sent data is always ⊆ the
   * collected data. Omit to send exactly what was collected. */
  sendFields?: FieldSelection;
};

/**
 * The collection config. This is a PLAIN, SOURCE-AGNOSTIC object — the SDK does not care where it
 * comes from. Set it however you like:
 *   - hardcoded at init:      `new DeviceIntel({config: {probes: {...}}})`
 *   - a bundled JSON file:    `new DeviceIntel({config: require("./device-intel.json")})`
 *   - your own backend / any config service you already run
 *   - Firebase Remote Config (optional — one source among many, NOT a dependency)
 * and change it at any entry point with `deviceIntel.setConfig(...)`, or per-flow with
 * `collect({config})`. There is no fetch/network logic in this package.
 */
export type ProbeConfig = {
  probes: Record<string, ProbeOverride>;
};

export const DEFAULT_PROBE_CONFIG: ProbeConfig = {probes: {}};

/**
 * Layer several configs into one, later wins. This is how the resolution order is expressed:
 *   mergeConfigs(baseConfig, profileConfig, perCallConfig)
 * Per-probe overrides merge field-by-field ({...earlier, ...later}), so a later layer that sets only
 * `enabled` does not wipe an earlier layer's `timeoutMs`/`fields`. A `fields`/`sendFields` selection
 * is replaced wholesale by a later layer that specifies it (not deep-merged) — predictable and easy
 * to reason about.
 */
export function mergeConfigs(...configs: ProbeConfig[]): ProbeConfig {
  const probes: Record<string, ProbeOverride> = {};
  for (const config of configs) {
    for (const [id, override] of Object.entries(config.probes)) {
      probes[id] = {...probes[id], ...override};
    }
  }
  return {probes};
}

/**
 * Pure, synchronous merge: config overrides the static `enabled`/`timeoutMs` defaults baked into each
 * Probe at source. Unknown probe ids in the config are ignored (forward-compatible with older app
 * builds receiving a config written for a newer build, and with probe ids removed in a later release).
 * Missing per-probe overrides fall through to the probe's own default untouched. (`fields`/`sendFields`
 * are not applied here — they are applied to collected data after the probe runs; see
 * fieldProjection.ts. This function only decides whether/how long a probe RUNS.)
 *
 * This is a hard prerequisite for gpu_benchmark/audio_latency: those probes ship with
 * `enabled: () => false` in source, and config is what flips them on for a cohort — and what kills
 * them instantly without an app release. The merge must work in BOTH directions (config enabling a
 * source-disabled probe; config disabling a source-enabled probe) — see probeConfig.spec.ts.
 *
 * No fetch/network logic here by design — this function takes an already-resolved ProbeConfig object.
 * Where that object comes from is the app's choice (see ProbeConfig); keeping this function pure and
 * I/O-free is what keeps the package unopinionated about the config source.
 */
export function applyConfig(probes: Probe[], config: ProbeConfig): Probe[] {
  return probes.map((probe) => {
    const override = config.probes[probe.id];
    if (!override) return probe;

    const sourceEnabled = probe.enabled;

    return {
      ...probe,
      // Stays a function, per the Probe type — resolves the override if present, else defers to
      // the probe's own source-level default. Config can win in EITHER direction: enable a
      // source-disabled probe, or disable a source-enabled one.
      enabled: () => override.enabled ?? sourceEnabled(),
      timeoutMs: override.timeoutMs ?? probe.timeoutMs,
    };
  });
}
