import {Platform} from "react-native";
import type {Probe} from "./types";

/**
 * Platform-unavailability convention: when a probe has no native counterpart on a platform (for
 * example, os_integrity_fork_test is iOS-only), force it disabled on the other platform at the
 * point the category is assembled — reusing the existing `{status:"skipped", reason:"disabled"}`
 * path in registry.ts rather than inventing a second skip-reason string.
 *
 * This is deliberately NOT used for "category exists on both platforms but one specific field is
 * unavailable on this device/OS version" — that case is the native module's job: return a typed
 * not-available value inline in its normal success payload, don't reject and don't route through
 * this helper.
 */
export function iosOnly(probes: Probe[]): Probe[] {
  return Platform.OS === "ios" ? probes : probes.map((p) => ({...p, enabled: () => false}));
}

export function androidOnly(probes: Probe[]): Probe[] {
  return Platform.OS === "android" ? probes : probes.map((p) => ({...p, enabled: () => false}));
}
