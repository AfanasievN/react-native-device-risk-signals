import NativeDeviceIntel from "../NativeDeviceIntel";
import {assertDefined} from "../testing/assertDefined";
import {applicationProbes} from "./applicationProbe";
import {deviceIdentityProbes} from "./deviceProbe";
import {fontsProbes} from "./fontsProbe";
import {geolocationProbes} from "./geolocationProbe";
import {hardwareProbes} from "./hardwareProbe";
import {localeProbes} from "./localeProbe";
import {mediaBluetoothAppsProbes} from "./mediaBluetoothAppsProbe";
import {networkProbes} from "./networkProbe";
import {telephonyProbes} from "./telephonyProbe";
import type {Probe} from "./types";

// The single-probe, non-platform-gated categories (device_identity / network / telephony / locale /
// geolocation / media_bluetooth_apps) share an identical shape, so one spec covers all their wirings
// rather than one near-duplicate file each. Platform-gated categories live in osProbe.spec.ts.
//
// The mock is defined INLINE in the factory (not captured from an outer const) because the static
// probe imports above are hoisted above any const initialization — capturing an outer var would hit
// its temporal dead zone. The jest.fns are reached back through the mocked default export.
jest.mock("../NativeDeviceIntel", () => ({
  __esModule: true,
  default: {
    getDeviceIdentity: jest.fn(() => Promise.resolve({model: "Pixel 7"})),
    getApplicationSignals: jest.fn(() => Promise.resolve({appVersion: "1.2.3"})),
    getHardwareSignals: jest.fn(() => Promise.resolve({processorCount: 8})),
    getFontsFingerprint: jest.fn(() => Promise.resolve({fontsDigest: "abc123"})),
    getNetworkSignals: jest.fn(() => Promise.resolve({connectionType: "wifi"})),
    getTelephonySignals: jest.fn(() => Promise.resolve({phoneType: "gsm"})),
    getLocaleSignals: jest.fn(() => Promise.resolve({language: "id"})),
    getGeolocationSignals: jest.fn(() => Promise.resolve({hasCoarsePermission: true})),
    getMediaBluetoothAppsSignals: jest.fn(() => Promise.resolve({isMusicActive: false})),
  },
}));

function only(probes: Probe[]): Probe {
  expect(probes).toHaveLength(1);
  return assertDefined(probes[0]);
}

describe("device_identity / network / telephony / locale probes", () => {
  it("device_identity probe is enabled and wired to getDeviceIdentity()", async () => {
    const probe = only(deviceIdentityProbes);
    expect(probe.id).toBe("device_identity");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({model: "Pixel 7"});
    expect(NativeDeviceIntel.getDeviceIdentity).toHaveBeenCalled();
  });

  it("application probe is enabled and wired to getApplicationSignals()", async () => {
    const probe = only(applicationProbes);
    expect(probe.id).toBe("application");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({appVersion: "1.2.3"});
    expect(NativeDeviceIntel.getApplicationSignals).toHaveBeenCalled();
  });

  it("hardware probe is enabled and wired to getHardwareSignals()", async () => {
    const probe = only(hardwareProbes);
    expect(probe.id).toBe("hardware");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({processorCount: 8});
    expect(NativeDeviceIntel.getHardwareSignals).toHaveBeenCalled();
  });

  it("fonts probe is enabled and wired to getFontsFingerprint()", async () => {
    const probe = only(fontsProbes);
    expect(probe.id).toBe("fonts");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({fontsDigest: "abc123"});
    expect(NativeDeviceIntel.getFontsFingerprint).toHaveBeenCalled();
  });

  it("network probe is enabled and wired to getNetworkSignals()", async () => {
    const probe = only(networkProbes);
    expect(probe.id).toBe("network");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({connectionType: "wifi"});
    expect(NativeDeviceIntel.getNetworkSignals).toHaveBeenCalled();
  });

  it("telephony probe is enabled and wired to getTelephonySignals()", async () => {
    const probe = only(telephonyProbes);
    expect(probe.id).toBe("telephony");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({phoneType: "gsm"});
    expect(NativeDeviceIntel.getTelephonySignals).toHaveBeenCalled();
  });

  it("locale probe is enabled and wired to getLocaleSignals()", async () => {
    const probe = only(localeProbes);
    expect(probe.id).toBe("locale");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({language: "id"});
    expect(NativeDeviceIntel.getLocaleSignals).toHaveBeenCalled();
  });

  it("geolocation probe is enabled and wired to getGeolocationSignals()", async () => {
    const probe = only(geolocationProbes);
    expect(probe.id).toBe("geolocation");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({hasCoarsePermission: true});
    expect(NativeDeviceIntel.getGeolocationSignals).toHaveBeenCalled();
  });

  it("media_bluetooth_apps probe is enabled and wired to getMediaBluetoothAppsSignals()", async () => {
    const probe = only(mediaBluetoothAppsProbes);
    expect(probe.id).toBe("media_bluetooth_apps");
    expect(probe.enabled()).toBe(true);
    expect(await probe.collect()).toEqual({isMusicActive: false});
    expect(NativeDeviceIntel.getMediaBluetoothAppsSignals).toHaveBeenCalled();
  });
});
