import NativeDeviceIntel, {type MediaBluetoothAppsSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// media_bluetooth_apps — audio route, screen-capture, installed/openable flagged apps, accessibility
// enumeration. Slightly longer timeout: Android package enumeration + bonded-device reads are a few
// binder round-trips. One TS category; native organizes it per-platform (see NativeDeviceIntel.ts).
export const mediaBluetoothAppsProbes: Probe[] = [
  {
    id: "media_bluetooth_apps",
    timeoutMs: 600,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getMediaBluetoothAppsSignals(),
  } satisfies Probe<MediaBluetoothAppsSignals>,
];
