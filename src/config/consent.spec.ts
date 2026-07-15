import type {Probe} from "../probes/types";
import {assertDefined} from "../testing/assertDefined";
import {ALLOW_ALL_CONSENT, applyConsent, bothConsent, consentFor} from "./consent";

function fakeProbe(id: string, enabled: boolean): Probe {
  return {id, timeoutMs: 100, enabled: () => enabled, collect: () => Promise.resolve(undefined)};
}

describe("consent gating", () => {
  it("consentFor allows only the listed ids", () => {
    const gate = consentFor(["a", "b"]);
    expect(gate("a")).toBe(true);
    expect(gate("c")).toBe(false);
  });

  it("ALLOW_ALL_CONSENT allows everything", () => {
    expect(ALLOW_ALL_CONSENT("anything")).toBe(true);
  });

  it("applyConsent force-disables a probe the gate rejects", () => {
    const probe = assertDefined(applyConsent([fakeProbe("denied", true)], consentFor([]))[0]);
    expect(probe.enabled()).toBe(false);
  });

  it("consent is subtractive only — it can NEVER re-enable a source-disabled probe", () => {
    // Gate allows the id, but the probe is disabled at source: consent ANDs, so it stays disabled.
    const probe = assertDefined(applyConsent([fakeProbe("p", false)], consentFor(["p"]))[0]);
    expect(probe.enabled()).toBe(false);
  });

  it("keeps an allowed + source-enabled probe enabled", () => {
    const probe = assertDefined(applyConsent([fakeProbe("p", true)], consentFor(["p"]))[0]);
    expect(probe.enabled()).toBe(true);
  });

  it("bothConsent requires both gates to allow", () => {
    const gate = bothConsent(consentFor(["a", "b"]), consentFor(["b", "c"]));
    expect(gate("b")).toBe(true);
    expect(gate("a")).toBe(false);
    expect(gate("c")).toBe(false);
  });
});
