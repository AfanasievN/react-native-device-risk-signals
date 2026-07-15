// Guards probes/index.ts. No other test exercises the
// REAL allProbes composition (DeviceIntel.spec mocks ./probes wholesale; per-category specs import
// their array directly), so dropping a `...xProbes` spread would otherwise ship a whole category
// broken with green CI.
jest.mock("react-native", () => ({Platform: {OS: "android"}}));
jest.mock("../NativeDeviceIntel", () => ({
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
  },
}));

import {allProbes} from "./index";

describe("allProbes registry", () => {
  it("includes every registered probe category (guards the spread in index.ts)", () => {
    const ids = allProbes.map((p) => p.id);
    expect(ids).toEqual(
      expect.arrayContaining([
        "device_identity",
        "hardware",
        "fonts",
        "os_integrity",
        "os_integrity_frida_scan",
        "network",
        "telephony",
        "locale",
        "geolocation",
        "media_bluetooth_apps",
        "gpu_benchmark",
        "audio_latency",
        "application",
        "runtime",
      ]),
    );
  });

  it("has no duplicate probe ids", () => {
    const ids = allProbes.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});
