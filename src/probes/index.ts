import {applicationProbes} from "./applicationProbe";
import {audioLatencyProbes} from "./audioLatencyProbe";
import {deviceIdentityProbes} from "./deviceProbe";
import {fontsProbes} from "./fontsProbe";
import {geolocationProbes} from "./geolocationProbe";
import {gpuBenchmarkProbes} from "./gpuBenchmarkProbe";
import {hardwareProbes} from "./hardwareProbe";
import {localeProbes} from "./localeProbe";
import {mediaBluetoothAppsProbes} from "./mediaBluetoothAppsProbe";
import {networkProbes} from "./networkProbe";
import {osIntegrityProbes} from "./osProbe";
import {runtimeProbes} from "./runtimeProbe";
import {securityPostureProbes} from "./securityPostureProbe";
import {telephonyProbes} from "./telephonyProbe";
import type {Probe} from "./types";

// Single seam DeviceIntel.ts imports from. Category = file; each category exports a Probe[] combined
// here via spread. Add new categories here after implementing both native platforms and tests.
export const allProbes: Probe[] = [
  ...deviceIdentityProbes,
  ...hardwareProbes,
  ...fontsProbes,
  ...osIntegrityProbes,
  ...networkProbes,
  ...telephonyProbes,
  ...localeProbes,
  ...geolocationProbes,
  ...mediaBluetoothAppsProbes,
  ...gpuBenchmarkProbes,
  ...audioLatencyProbes,
  ...applicationProbes,
  ...securityPostureProbes,
  ...runtimeProbes,
];
