import type {Probe} from "./types";

// Platform.OS is read at MODULE LOAD time by androidOnly()/iosOnly() inside osProbe.ts, so each test
// sets the platform, then loads osProbe fresh via jest.isolateModules to re-run that gating.
let mockOsValue = "android";
jest.mock("react-native", () => ({
  Platform: {
    get OS() {
      return mockOsValue;
    },
  },
}));

const mockNative = {
  getDeviceIdentity: jest.fn(() => Promise.resolve({})),
  getOsIntegrity: jest.fn(() => Promise.resolve({isEmulator: false})),
  getFridaScanSignals: jest.fn(() => Promise.resolve({scanPerformed: true})),
  getForkJailbreakSignal: jest.fn(() => Promise.resolve({testPerformed: false})),
  getNetworkSignals: jest.fn(() => Promise.resolve({})),
  getTelephonySignals: jest.fn(() => Promise.resolve({})),
  getLocaleSignals: jest.fn(() => Promise.resolve({})),
};
jest.mock("../NativeDeviceIntel", () => ({__esModule: true, default: mockNative}));

function loadOsProbes(os: string): Probe[] {
  mockOsValue = os;
  let probes: Probe[] = [];
  jest.isolateModules(() => {
    probes = require("./osProbe").osIntegrityProbes;
  });
  return probes;
}

function byId(probes: Probe[], id: string): Probe | undefined {
  return probes.find((p) => p.id === id);
}

describe("osIntegrityProbes", () => {
  it("always includes the fast os_integrity bundle, enabled, on both platforms", () => {
    for (const os of ["android", "ios"]) {
      const probes = loadOsProbes(os);
      const fast = byId(probes, "os_integrity");
      expect(fast).toBeDefined();
      expect(fast?.enabled()).toBe(true);
    }
  });

  it("runs the active frida scan only on Android", () => {
    const android = byId(loadOsProbes("android"), "os_integrity_frida_scan");
    expect(android).toBeDefined();
    expect(android?.enabled()).toBe(true);

    const ios = byId(loadOsProbes("ios"), "os_integrity_frida_scan");
    // androidOnly keeps the entry present but forces it disabled off-platform.
    expect(ios?.enabled()).toBe(false);
  });

  it("ships the fork() jailbreak test DISABLED on every platform (stability gate)", () => {
    // On iOS it is source-disabled (() => false) until QA clears it; on Android iosOnly also disables it.
    expect(byId(loadOsProbes("ios"), "os_integrity_fork_test")?.enabled()).toBe(false);
    expect(byId(loadOsProbes("android"), "os_integrity_fork_test")?.enabled()).toBe(false);
  });

  it("wires os_integrity.collect() to the native getOsIntegrity()", async () => {
    const fast = byId(loadOsProbes("android"), "os_integrity");
    const data = await fast?.collect();
    expect(mockNative.getOsIntegrity).toHaveBeenCalled();
    expect(data).toEqual({isEmulator: false});
  });

  it("wires os_integrity_frida_scan.collect() to the native getFridaScanSignals()", async () => {
    const scan = byId(loadOsProbes("android"), "os_integrity_frida_scan");
    await scan?.collect();
    expect(mockNative.getFridaScanSignals).toHaveBeenCalled();
  });
});
