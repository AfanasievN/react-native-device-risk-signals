import NativeDeviceIntel, {
  type ForkJailbreakSignal,
  type FridaScanSignals,
  type OsIntegritySignals,
} from "../NativeDeviceIntel";
import {androidOnly, iosOnly} from "./platformProbe";
import type {Probe} from "./types";

/**
 * os_integrity is a CATEGORY of independent detection methods, not one check — so it exports a
 * Probe[] with one entry per method that has a distinct runtime profile:
 *
 *  - `os_integrity`            — fast, synchronous, permission-free bundle (root/jb files & props,
 *                                hook/injection scans, emulator heuristics). Both platforms.
 *  - `os_integrity_frida_scan` — ACTIVE socket probe (blocking connect to frida's default port).
 *                                Own longer timeout, own kill-switch. Android-only (see type docs).
 *  - `os_integrity_fork_test`  — fork()-based jailbreak check. SHIPS DISABLED (enabled: () => false)
 *                                until QA clears the crash/zombie risk on real devices. iOS-only.
 *
 * Every method returns raw fields; the server fuses them into a score — there is no on-device
 * verdict here (see NativeDeviceIntel.ts contract notes).
 */
export const osIntegrityProbes: Probe[] = [
  {
    id: "os_integrity",
    timeoutMs: 400,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getOsIntegrity(),
  } satisfies Probe<OsIntegritySignals>,

  // Active socket scan: blocking connect, so a generous timeout and off-main-thread native impl.
  // Android-only — androidOnly() forces it skipped on iOS (frida on jb iOS is caught by the dyld
  // image scan inside the fast bundle instead).
  ...androidOnly([
    {
      id: "os_integrity_frida_scan",
      timeoutMs: 1000,
      enabled: () => true,
      collect: () => NativeDeviceIntel.getFridaScanSignals(),
    } satisfies Probe<FridaScanSignals>,
  ]),

  // fork()-based jailbreak check. `enabled: () => false` at SOURCE — it ships dark and is turned on
  // per-cohort via remote config only after QA confirms it never crashes / leaks a zombie process on
  // real jailbroken and normal devices. iosOnly() additionally forces it off on Android. This is a
  // STABILITY gate (distinct from the GPU/audio policy kill-switch), but it rides the same
  // remote-config mechanism to flip on. Do NOT change this to `() => true` — see NativeDeviceIntel.ts.
  ...iosOnly([
    {
      id: "os_integrity_fork_test",
      timeoutMs: 300,
      enabled: () => false,
      collect: () => NativeDeviceIntel.getForkJailbreakSignal(),
    } satisfies Probe<ForkJailbreakSignal>,
  ]),
];
