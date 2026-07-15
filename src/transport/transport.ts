export type TransportConfig = {
  /** HTTPS base URL for event ingestion. Leave unset to collect without sending. */
  baseUrl?: string;
  /**
   * Max ms to wait for the ingest response before aborting. Without it a hung/unresponsive server
   * (TCP connected, no reply — a stalled/overloaded backend) would keep send() — and any UI awaiting
   * collectAndSend() — pending indefinitely (RN's default HTTP timeout can be very large). Default 10s.
   */
  timeoutMs?: number;
};

export type SendResult = {ok: boolean; status?: number; reason?: string};

const DEFAULT_TIMEOUT_MS = 10000;

export class Transport {
  constructor(private config: TransportConfig) {}

  send(path: string, payload: unknown): Promise<SendResult> {
    if (!this.config.baseUrl) {
      // No endpoint configured yet (expected in dev). The caller learns this from `reason` on the
      // returned SendResult — we don't console.warn (the repo lints `noConsole` as an error, and a
      // reusable package shouldn't log directly; routing through a logger is the host app's choice).
      return Promise.resolve({ok: false, reason: "no_endpoint_configured"});
    }

    // Bound the request so a non-responsive server can't stall send() (and any UI awaiting
    // collectAndSend()) forever. An abort surfaces as an AbortError → reported as reason:"timeout".
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.config.timeoutMs ?? DEFAULT_TIMEOUT_MS);
    return fetch(`${this.config.baseUrl}${path}`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(payload),
      // @types/node's AbortSignal isn't structurally identical to RN's fetch signal type — bridge it.
      signal: controller.signal as unknown as NonNullable<Parameters<typeof fetch>[1]>["signal"],
    })
      .then((response): SendResult => ({ok: response.ok, status: response.status}))
      .catch((error): SendResult => {
        const aborted = error instanceof Error && error.name === "AbortError";
        return {ok: false, reason: aborted ? "timeout" : error instanceof Error ? error.message : String(error)};
      })
      .finally(() => clearTimeout(timer));
  }
}
