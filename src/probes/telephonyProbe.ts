import NativeDeviceIntel, {type TelephonySignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// telephony — opportunistic only (never prompts). Fields degrade gracefully to null (IMEI always,
// carrier fields on iOS 16+) — the native side omits what it cannot read rather than faking it.
export const telephonyProbes: Probe[] = [
  {
    id: "telephony",
    timeoutMs: 300,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getTelephonySignals(),
  } satisfies Probe<TelephonySignals>,
];
