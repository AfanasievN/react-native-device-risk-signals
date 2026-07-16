import type {Probe} from "../probes/types";
import {assertDefined} from "../testing/assertDefined";
import {applyConfig, DEFAULT_PROBE_CONFIG, mergeConfigs, validateProbeConfig} from "./probeConfig";

function fakeProbe(overrides: Partial<Probe> = {}): Probe {
  return {
    id: "some_probe",
    timeoutMs: 200,
    enabled: () => true,
    collect: () => Promise.resolve(undefined),
    ...overrides,
  };
}

describe("applyConfig", () => {
  it("returns probes with unchanged behavior when config is empty", () => {
    const probe = fakeProbe();
    const merged = assertDefined(applyConfig([probe], DEFAULT_PROBE_CONFIG)[0]);
    expect(merged.enabled()).toBe(true);
    expect(merged.timeoutMs).toBe(200);
  });

  it("ignores unknown probe ids in the remote payload", () => {
    const probe = fakeProbe({id: "known_probe"});
    const config = {probes: {unrelated_probe: {enabled: false}}};
    const merged = assertDefined(applyConfig([probe], config)[0]);
    expect(merged.enabled()).toBe(true);
  });

  it("leaves timeoutMs untouched when only enabled is overridden", () => {
    const probe = fakeProbe({id: "p", timeoutMs: 300});
    const config = {probes: {p: {enabled: false}}};
    const merged = assertDefined(applyConfig([probe], config)[0]);
    expect(merged.enabled()).toBe(false);
    expect(merged.timeoutMs).toBe(300);
  });

  it("leaves enabled untouched when only timeoutMs is overridden", () => {
    const probe = fakeProbe({id: "p", enabled: () => true});
    const config = {probes: {p: {timeoutMs: 5000}}};
    const merged = assertDefined(applyConfig([probe], config)[0]);
    expect(merged.enabled()).toBe(true);
    expect(merged.timeoutMs).toBe(5000);
  });

  // The two cases below guard rollout and kill-switch behavior for gpu_benchmark/audio_latency:
  // the kill switch must work in both directions, not just "config can turn things off".
  it("kill-switch direction: config disables a source-enabled probe", () => {
    const probe = fakeProbe({id: "gpu_benchmark", enabled: () => true});
    const config = {probes: {gpu_benchmark: {enabled: false}}};
    const merged = assertDefined(applyConfig([probe], config)[0]);
    expect(merged.enabled()).toBe(false);
  });

  it("rollout direction: config enables a source-disabled probe", () => {
    const probe = fakeProbe({id: "gpu_benchmark", enabled: () => false});
    const config = {probes: {gpu_benchmark: {enabled: true}}};
    const merged = assertDefined(applyConfig([probe], config)[0]);
    expect(merged.enabled()).toBe(true);
  });

  it("no-ops without throwing when config has an override for a probe id absent from the input array", () => {
    const config = {probes: {nonexistent: {enabled: true}}};
    expect(() => applyConfig([], config)).not.toThrow();
    expect(applyConfig([], config)).toEqual([]);
  });
});

describe("mergeConfigs", () => {
  it("later layer wins per-field without wiping earlier fields of the same probe", () => {
    const merged = mergeConfigs({probes: {p: {enabled: true, timeoutMs: 100}}}, {probes: {p: {enabled: false}}});
    expect(assertDefined(merged.probes.p)).toEqual({enabled: false, timeoutMs: 100});
  });

  it("combines probe ids from every layer", () => {
    const merged = mergeConfigs({probes: {a: {enabled: false}}}, {probes: {b: {timeoutMs: 5}}});
    expect(Object.keys(merged.probes).sort()).toEqual(["a", "b"]);
  });

  it("replaces a fields selection wholesale (not deep-merged)", () => {
    const merged = mergeConfigs({probes: {p: {fields: {include: ["x"]}}}}, {probes: {p: {fields: {exclude: ["y"]}}}});
    expect(assertDefined(merged.probes.p).fields).toEqual({exclude: ["y"]});
  });

  it("is a no-op identity for a single config", () => {
    const cfg = {probes: {p: {enabled: false}}};
    expect(mergeConfigs(cfg)).toEqual(cfg);
  });
});

describe("validateProbeConfig", () => {
  it("reports unknown probe ids with a stable machine-readable code", () => {
    expect(validateProbeConfig({probes: {os_integritty: {enabled: false}}})).toContainEqual({
      code: "unknown_probe",
      path: "probes.os_integritty",
      message: 'Unknown probe id "os_integritty".',
    });
  });

  it("reports invalid timeout values", () => {
    expect(validateProbeConfig({probes: {network: {timeoutMs: 0}}})).toContainEqual({
      code: "invalid_timeout",
      path: "probes.network.timeoutMs",
      message: "timeoutMs must be a positive finite number.",
    });
  });

  it("reports fields that do not exist in the selected probe", () => {
    expect(
      validateProbeConfig({probes: {network: {fields: {include: ["isVpnActive", "vpnProviderName"]}}}}),
    ).toContainEqual({
      code: "unknown_field",
      path: "probes.network.fields.include[1]",
      message: 'Unknown field "vpnProviderName" for probe "network".',
    });
  });
});
