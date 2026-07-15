import {ALLOW_ALL_CONSENT, applyConsent, bothConsent, type ConsentGate} from "./config/consent";
import {projectData} from "./config/fieldProjection";
import {
  applyConfig,
  DEFAULT_PROBE_CONFIG,
  type FieldSelection,
  mergeConfigs,
  type ProbeConfig,
} from "./config/probeConfig";
import {allProbes} from "./probes";
import {collectAll} from "./probes/registry";
import type {ProbeResult} from "./probes/types";
import {Transport, type TransportConfig} from "./transport/transport";

/**
 * Everything you can set AT INIT, in one place — no Firebase, no network, all plain objects/functions:
 *   new DeviceIntel({ transport: {...}, config: {...}, consent })
 * Each field can also be changed later at any entry point (setConfig / setConsent) or per-collect.
 */
export type DeviceIntelOptions = {
  /** Where events are sent. Point at the same host as the main app API (see TransportConfig). */
  transport?: TransportConfig;
  /**
   * Initial collection config — WHAT is collected/sent. A plain object from ANY source (hardcoded
   * here, a bundled JSON, your own backend, or Firebase — see ProbeConfig). The SDK never fetches it.
   */
  config?: ProbeConfig;
  /** Initial consent gate. Subtractive. Default: allow all. */
  consent?: ConsentGate;
  /**
   * The session id stamped onto every emitted event. Pass YOUR app's own session id here to correlate
   * device-intel events with the rest of your telemetry. Change it later with setSessionId(), or
   * override for one call via collect({sessionId}). If omitted, the SDK generates an ephemeral one.
   */
  sessionId?: string;
  /**
   * The authenticated user / client id, stamped onto every emitted event as `client_id` once known.
   * Unlike `session_id` (always present, per session), this is OPTIONAL: set it only after the user is
   * authenticated (e.g. via setClientId on login) so events before and after sign-in correlate to the
   * account server-side. It is an app-supplied ACCOUNT id — NOT a device fingerprint or a persistent
   * device id (the SDK never derives it). Omitted from the payload entirely while unset.
   */
  clientId?: string;
};

/**
 * Per-call collection options. `config` is layered OVER the instance config (later wins, via
 * mergeConfigs), so a caller tunes WHAT is collected/sent at the moment collection is triggered —
 * e.g. a lean default at login vs a "deep" profile for a higher-risk action. A profile is just a
 * ProbeConfig preset the app keeps and passes here; nothing more special than that.
 */
export type CollectOptions = {
  config?: ProbeConfig;
  /** Per-call consent gate, ANDed with the instance consent (setConsent). Subtractive only — it can
   * further restrict but never re-enable a probe the instance consent or config disabled. */
  consent?: ConsentGate;
  /** Session id for THIS collection only — overrides the instance session id without mutating it.
   * Use when a single collect() belongs to a different app session than the instance default. */
  sessionId?: string;
  /** Client/user id for THIS collection only — overrides the instance client id without mutating it. */
  clientId?: string;
};

// Bump ONLY when the shape of RawSignalEvent itself changes (e.g. renaming/removing session_id /
// event_type / collected_at, or changing `probes` from a flat Record<string, outcome> to something
// structurally different). Adding a new probe id into the same `probes` Record is NOT a
// schema-version-worthy change — that's exactly what an open Record is for; a schema_version:1
// payload from Day 1 and one six months later with 80 more probe keys present both parse the same
// way. If a native getter's return shape changes in a breaking way, change that probe's `id` instead
// (e.g. "os_integrity" -> "os_integrity_v2") so old and new shapes never collide under the same key
// downstream — that's a naming convention, not a reason to bump this constant.
const SCHEMA_VERSION = 1;

function randomSessionId(): string {
  // Fallback used only when the host app does not supply its own session id (see setSessionId /
  // DeviceIntelOptions.sessionId). Not cryptographically strong, and deliberately not a
  // device-persistent identifier — a reinstall-surviving id is explicitly out of scope. The `di_`
  // prefix marks an SDK-generated id vs an
  // app-supplied one so downstream can tell them apart.
  return `di_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
}

export type RawSignalEvent = {
  session_id: string;
  // Present ONLY once the app supplies an authenticated user/client id (see DeviceIntelOptions.clientId /
  // setClientId). Omitted entirely for unauthenticated collections. An app-supplied ACCOUNT id, never a
  // device-derived identifier — so it never conflicts with the "no persistent device id" decision.
  client_id?: string;
  event_type: "device_intel_collection";
  schema_version: number;
  collected_at: string;
  probes: Record<string, ProbeResult["outcome"]>;
};

export class DeviceIntel {
  private transport: Transport;
  private sessionId: string;
  private clientId?: string;
  private probeConfig: ProbeConfig;
  private consent: ConsentGate;

  constructor(options: DeviceIntelOptions = {}) {
    this.transport = new Transport(options.transport ?? {});
    this.probeConfig = options.config ?? DEFAULT_PROBE_CONFIG;
    this.consent = options.consent ?? ALLOW_ALL_CONSENT;
    this.sessionId = options.sessionId ?? randomSessionId();
    this.clientId = options.clientId;
  }

  /**
   * Set the session id stamped onto subsequent events — pass YOUR app's session id so device-intel
   * events correlate with the rest of your telemetry. Call whenever the app's session changes/rotates.
   */
  setSessionId(sessionId: string): void {
    this.sessionId = sessionId;
  }

  /**
   * Set (or clear) the authenticated user/client id stamped onto subsequent events as `client_id`.
   * Call after login with the account id; pass undefined on logout to stop stamping it. App-supplied
   * account id only — never a device-derived identifier.
   */
  setClientId(clientId: string | undefined): void {
    this.clientId = clientId;
  }

  /**
   * Regenerate an SDK-managed ephemeral session id. Only meaningful when the app is NOT supplying its
   * own id via setSessionId/constructor — if you manage the session id yourself, use setSessionId instead.
   */
  resetSession(): void {
    this.sessionId = randomSessionId();
  }

  /**
   * Replace the collection config at any entry point (app launch, screen enter, login). Takes effect
   * on the next collect() — the merge/resolve happens fresh each call, nothing is cached. The config
   * is a plain object from any source; this method does no I/O.
   */
  setConfig(config: ProbeConfig): void {
    this.probeConfig = config;
  }

  /**
   * Set the user's consent gate. Subtractive: a probe the gate rejects is forced off on
   * every subsequent collect(), and no config/profile can re-enable it. Call when consent changes.
   */
  setConsent(consent: ConsentGate): void {
    this.consent = consent;
  }

  collect(options: CollectOptions = {}): Promise<RawSignalEvent> {
    const resolved = this.resolveConfig(options);
    // Resolution order (low → high precedence): source defaults (baked into each Probe) < instance
    // config < this call's config. applyConfig decides which probes RUN; applyConsent then subtracts
    // anything the user hasn't consented to (final word); projectData (below) decides which FIELDS
    // survive into the collected payload.
    const probes = applyConsent(applyConfig(allProbes, resolved), this.effectiveConsent(options));
    return collectAll(probes).then((results): RawSignalEvent => {
      const probesById: Record<string, ProbeResult["outcome"]> = {};
      for (const result of results) {
        probesById[result.id] = projectOutcome(result.outcome, resolved.probes[result.id]?.fields);
      }
      const clientId = options.clientId ?? this.clientId;
      return {
        session_id: options.sessionId ?? this.sessionId,
        // Only stamp client_id when authenticated — omit the key entirely otherwise.
        ...(clientId !== undefined ? {client_id: clientId} : {}),
        event_type: "device_intel_collection",
        schema_version: SCHEMA_VERSION,
        collected_at: new Date().toISOString(),
        probes: probesById,
      };
    });
  }

  collectAndSend(options: CollectOptions & {path?: string} = {}): Promise<{event: RawSignalEvent; sent: boolean}> {
    const resolved = this.resolveConfig(options);
    // `event` is the fuller COLLECTED view (fields projection). The transmitted copy has `sendFields`
    // applied on top, so what's sent is always ⊆ what was collected — see ProbeOverride.sendFields.
    return this.collect(options).then((event) => {
      const toSend: RawSignalEvent = {
        ...event,
        probes: Object.fromEntries(
          Object.entries(event.probes).map(([id, outcome]) => [
            id,
            projectOutcome(outcome, resolved.probes[id]?.sendFields),
          ]),
        ),
      };
      return this.transport
        .send(options.path ?? "/v1/device-intel/events", toSend)
        .then((result) => ({event, sent: result.ok}));
    });
  }

  private resolveConfig(options: CollectOptions): ProbeConfig {
    return options.config ? mergeConfigs(this.probeConfig, options.config) : this.probeConfig;
  }

  private effectiveConsent(options: CollectOptions): ConsentGate {
    return options.consent ? bothConsent(this.consent, options.consent) : this.consent;
  }
}

// Trims a successful probe's data to a field selection (if any). Non-success outcomes pass through
// untouched. Used for both the collect-time `fields` and the send-time `sendFields` projection.
function projectOutcome(outcome: ProbeResult["outcome"], fields: FieldSelection | undefined): ProbeResult["outcome"] {
  if (outcome.status !== "success" || !fields) return outcome;
  return {status: "success", data: projectData(outcome.data, fields)};
}
