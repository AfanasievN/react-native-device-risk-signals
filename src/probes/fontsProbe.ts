import NativeDeviceIntel, {type FontsSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// fonts — installed-fonts SHA-256 fingerprint, split out of `hardware` into its own probe. Font
// enumeration + hashing is the one heavy device read; a GENEROUS, isolated timeout means a slow low-end
// device can miss the digest without dropping the fast hardware fields (they no longer share a timeout).
// Enabled by default; permission-free, no Required-Reason API. See NativeDeviceIntel.ts (FontsSignals).
export const fontsProbes: Probe[] = [
  {
    id: "fonts",
    timeoutMs: 1500,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getFontsFingerprint(),
  } satisfies Probe<FontsSignals>,
];
