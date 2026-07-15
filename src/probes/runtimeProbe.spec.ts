jest.mock("react-native", () => ({
  Platform: {OS: "android", constants: {reactNativeVersion: {major: 0, minor: 82, patch: 1}}},
}));

import {assertDefined} from "../testing/assertDefined";
import {runtimeProbes} from "./runtimeProbe";

type RuntimeGlobals = {
  HermesInternal?: unknown;
  __turboModuleProxy?: unknown;
  nativeFabricUIManager?: unknown;
  RN$Bridgeless?: boolean;
};

describe("runtime probe", () => {
  const probe = assertDefined(runtimeProbes[0]);
  const g = globalThis as RuntimeGlobals;

  afterEach(() => {
    g.HermesInternal = undefined;
    g.__turboModuleProxy = undefined;
    g.nativeFabricUIManager = undefined;
    g.RN$Bridgeless = undefined;
  });

  it("is a JS-only probe: enabled, no native dependency, reports platform + RN version", async () => {
    expect(probe.id).toBe("runtime");
    expect(probe.enabled()).toBe(true);
    const result = (await probe.collect()) as Record<string, unknown>;
    expect(result.platformOs).toBe("android");
    expect(result.reactNativeVersion).toBe("0.82.1");
  });

  it("detects Hermes + new-arch flags from JS globals", async () => {
    g.HermesInternal = {getRuntimeProperties: () => ({"OSS Release Version": "for RN 0.82.1"})};
    g.__turboModuleProxy = {};
    g.nativeFabricUIManager = {};
    const result = (await probe.collect()) as Record<string, unknown>;
    expect(result.isHermes).toBe(true);
    expect(result.jsEngine).toBe("hermes");
    expect(result.hermesVersion).toBe("for RN 0.82.1");
    expect(result.isTurboModule).toBe(true);
    expect(result.isFabric).toBe(true);
  });

  it("reports an unknown engine when Hermes is absent", async () => {
    const result = (await probe.collect()) as Record<string, unknown>;
    expect(result.isHermes).toBe(false);
    expect(result.jsEngine).toBe("unknown");
    expect(result.hermesVersion).toBeUndefined();
  });

  it("bridgeless implies TurboModules even when __turboModuleProxy is absent (RN 0.82 default)", async () => {
    g.RN$Bridgeless = true; // no __turboModuleProxy set — exactly the on-device case
    const result = (await probe.collect()) as Record<string, unknown>;
    expect(result.isBridgeless).toBe(true);
    expect(result.isTurboModule).toBe(true);
  });

  it("reports non-bridgeless / TurboModules off when neither global is set", async () => {
    const result = (await probe.collect()) as Record<string, unknown>;
    expect(result.isBridgeless).toBe(false);
    expect(result.isTurboModule).toBe(false);
  });
});
