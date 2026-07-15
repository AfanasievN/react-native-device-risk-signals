import NativeDeviceIntel, {type HardwareSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// hardware — permission-free device-class + fingerprint entropy (screen/cpu/memory/battery/brightness/
// storage). Enabled by default; no policy issue, no Required-Reason API. All reads here are fast; the one
// heavy read (installed-fonts enumeration + hash) was split into its own `fonts` probe (see fontsProbe.ts)
// so it can never blow this budget or take the fast fields down with it. See NativeDeviceIntel.ts.
export const hardwareProbes: Probe[] = [
  {
    id: "hardware",
    timeoutMs: 500,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getHardwareSignals(),
  } satisfies Probe<HardwareSignals>,
];
