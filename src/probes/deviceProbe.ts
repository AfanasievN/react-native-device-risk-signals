import NativeDeviceIntel, {type DeviceIdentity} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// Plural array export, even though this category is one probe today: category = file, category
// exports a Probe[] so categories that legitimately need multiple Probe entries (e.g. os_integrity) don't
// require a different shape than single-probe categories.
export const deviceIdentityProbes: Probe[] = [
  {
    id: "device_identity",
    timeoutMs: 200,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getDeviceIdentity(),
  } satisfies Probe<DeviceIdentity>,
];
