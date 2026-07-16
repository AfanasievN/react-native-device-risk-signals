jest.mock("react-native", () => ({Platform: {OS: "android"}}));
jest.mock("./NativeDeviceIntel", () => ({
  __esModule: true,
  default: {
    getDeviceIdentity: jest.fn(),
    getHardwareSignals: jest.fn(),
    getFontsFingerprint: jest.fn(),
    getOsIntegrity: jest.fn(),
    getFridaScanSignals: jest.fn(),
    getForkJailbreakSignal: jest.fn(),
    getNetworkSignals: jest.fn(),
    getTelephonySignals: jest.fn(),
    getLocaleSignals: jest.fn(),
    getGeolocationSignals: jest.fn(),
    getMediaBluetoothAppsSignals: jest.fn(),
    getGpuBenchmark: jest.fn(),
    getAudioLatency: jest.fn(),
    getApplicationSignals: jest.fn(),
    getDeviceSecurityPosture: jest.fn(),
    getTransactionSafetySignals: jest.fn(),
  },
}));

import {PROBE_CATALOG} from "./probeCatalog";
import {allProbes} from "./probes";

describe("PROBE_CATALOG", () => {
  it("describes every registered probe exactly once", () => {
    expect(PROBE_CATALOG.map(({id}) => id).sort()).toEqual(allProbes.map(({id}) => id).sort());
  });

  it("has machine-readable privacy metadata and unique selectable fields", () => {
    for (const descriptor of PROBE_CATALOG) {
      expect(descriptor.platforms.length).toBeGreaterThan(0);
      expect(descriptor.dataCategories.length).toBeGreaterThan(0);
      expect(descriptor.purpose.length).toBeGreaterThan(0);
      expect(new Set(descriptor.fields).size).toBe(descriptor.fields.length);
    }
  });

  it("marks higher-risk probes that ship disabled", () => {
    for (const id of ["gpu_benchmark", "audio_latency", "os_integrity_fork_test", "transaction_safety"]) {
      expect(PROBE_CATALOG.find((descriptor) => descriptor.id === id)?.enabledByDefault).toBe(false);
    }
  });
});
