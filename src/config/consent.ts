import type {Probe} from "../probes/types";

/**
 * Consent gate — decides, per probe id, whether the user has consented to that signal being
 * collected. This is a SUBTRACTIVE layer: it can only ever force a probe OFF, never on.
 * `applyConsent` ANDs it with the probe's already-resolved `enabled`, so no remote config or per-call
 * profile can re-enable a probe the user declined — consent always wins downward.
 *
 * The gate operates on probe ids so callers can map their own consent categories however they like
 * (e.g. treat every id starting with "os_integrity" as one "security" consent). `consentFor` builds
 * a simple allowlist gate from a set of ids.
 */
export type ConsentGate = (probeId: string) => boolean;

export const ALLOW_ALL_CONSENT: ConsentGate = () => true;

export function consentFor(allowedProbeIds: Iterable<string>): ConsentGate {
  const allowed = new Set(allowedProbeIds);
  return (probeId) => allowed.has(probeId);
}

/** Combine two gates — a probe is allowed only if BOTH allow it (further restriction, never widening). */
export function bothConsent(a: ConsentGate, b: ConsentGate): ConsentGate {
  return (probeId) => a(probeId) && b(probeId);
}

/**
 * Force-disable any probe the consent gate rejects. Applied AFTER applyConfig so it is the final
 * word: `enabled` becomes `base() && consent(id)` — consent can subtract from the resolved decision
 * but never add.
 */
export function applyConsent(probes: Probe[], consent: ConsentGate): Probe[] {
  return probes.map((probe) => {
    const base = probe.enabled;
    return {...probe, enabled: () => base() && consent(probe.id)};
  });
}
