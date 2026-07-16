import type {Probe, ProbeOutcome, ProbeResult} from "./types";

function runWithTimeout<T>(probe: Probe<T>): Promise<ProbeOutcome<T>> {
  return new Promise((resolve) => {
    let settled = false;
    const timer = setTimeout(() => {
      settled = true;
      resolve({status: "timeout"});
    }, probe.timeoutMs);

    Promise.resolve()
      .then(() => probe.collect())
      .then((data) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        resolve({status: "success", data});
      })
      .catch((error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        resolve({status: "error", error: error instanceof Error ? error.message : String(error)});
      });
  });
}

/**
 * Runs every enabled probe in parallel. A single probe that throws, hangs past its timeout, or is
 * disabled never blocks or fails the others — the framework only ever produces a per-probe outcome,
 * never a thrown error from `collectAll` itself.
 */
export function collectAll(probes: Probe[]): Promise<ProbeResult[]> {
  return Promise.all(
    probes.map((probe): Promise<ProbeResult> => {
      const startedAt = Date.now();
      if (!probe.enabled()) {
        return Promise.resolve({
          id: probe.id,
          outcome: {status: "skipped", reason: "disabled"} as ProbeOutcome<unknown>,
          durationMs: 0,
        });
      }
      return runWithTimeout(probe).then((outcome) => ({id: probe.id, outcome, durationMs: Date.now() - startedAt}));
    }),
  );
}
