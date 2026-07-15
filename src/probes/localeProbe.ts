import NativeDeviceIntel, {type LocaleSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// locale — trivial, permission-free, high-entropy (keyboard/timezone/currency mismatch vs claimed
// identity is a classic fraud tell).
export const localeProbes: Probe[] = [
  {
    id: "locale",
    timeoutMs: 200,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getLocaleSignals(),
  } satisfies Probe<LocaleSignals>,
];
