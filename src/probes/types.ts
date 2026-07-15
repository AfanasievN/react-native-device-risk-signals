export type ProbeOutcome<T> =
  | {status: "success"; data: T}
  | {status: "error"; error: string}
  | {status: "timeout"}
  | {status: "skipped"; reason: string};

export type Probe<T = unknown> = {
  /** Stable id — becomes the key under `probes` in the collected payload, and the key used to
   * address this probe in the collection config (see src/config/probeConfig.ts). */
  id: string;
  /** How long this probe is allowed to run before it's abandoned. Does not cancel the underlying
   * native call — a slow native promise may still resolve later and is simply ignored. */
  timeoutMs: number;
  /** Whether this probe is currently enabled. A function (not a plain boolean) so config can override
   * the source-level default on every call without needing to mutate the Probe object — see
   * applyConfig, which wraps this with a config-aware closure. */
  enabled: () => boolean;
  collect: () => Promise<T>;
};

export type ProbeResult = {
  id: string;
  outcome: ProbeOutcome<unknown>;
  durationMs: number;
};
