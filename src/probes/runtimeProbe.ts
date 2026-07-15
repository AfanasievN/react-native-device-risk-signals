import {Platform} from "react-native";
import type {Probe} from "./types";

/**
 * runtime — JS/RN engine facts, collected PURELY in JS (no native round-trip, no Spec method). Cheap,
 * and a tamper/consistency signal: a bundle whose engine/version/renderer differs from what the app
 * ships (js_engine != "hermes", an unexpected reactNativeVersion, Fabric/TurboModules off when the
 * build enables them, __DEV__ true in a release build) is a strong "this isn't our unmodified binary"
 * tell. Works on both platforms and reads only JS globals.
 */
export type RuntimeSignals = {
  jsEngine?: string; // "hermes" | "unknown" (JSC/V8 are not directly detectable from JS).
  hermesVersion?: string; // Hermes "OSS Release Version" when running on Hermes.
  isHermes?: boolean;
  isFabric?: boolean; // new-architecture renderer active (global.nativeFabricUIManager present).
  isTurboModule?: boolean; // TurboModules active — global.__turboModuleProxy (legacy interop) OR bridgeless
  // (in bridgeless mode __turboModuleProxy is null but TurboModules are the only native-module mechanism).
  isBridgeless?: boolean; // RN new-arch bridgeless runtime (global.RN$Bridgeless) — most reliable "new
  // arch on" signal; a build shipping bridgeless that reports false is a tamper/repack tell.
  isDebugBuild?: boolean; // __DEV__ — a release build reporting true is suspicious.
  reactNativeVersion?: string; // e.g. "0.82.1".
  platformOs?: string; // "android" | "ios".
};

type RuntimeGlobals = {
  HermesInternal?: {getRuntimeProperties?: () => Record<string, string | undefined>} | null;
  __turboModuleProxy?: unknown;
  nativeFabricUIManager?: unknown;
  RN$Bridgeless?: boolean;
  __DEV__?: boolean;
};

type RnVersion = {major?: number; minor?: number; patch?: number};

function reactNativeVersion(): string | undefined {
  const version = (Platform.constants as {reactNativeVersion?: RnVersion} | undefined)?.reactNativeVersion;
  if (!version || version.major === undefined) return undefined;
  return [version.major, version.minor ?? 0, version.patch ?? 0].join(".");
}

function collectRuntime(): RuntimeSignals {
  const g = globalThis as unknown as RuntimeGlobals;
  const isHermes = g.HermesInternal != null;
  const hermesProps = isHermes ? g.HermesInternal?.getRuntimeProperties?.() : undefined;
  const isBridgeless = g.RN$Bridgeless === true;
  return {
    isHermes,
    jsEngine: isHermes ? "hermes" : "unknown",
    hermesVersion: hermesProps?.["OSS Release Version"],
    isFabric: g.nativeFabricUIManager != null,
    // In bridgeless mode __turboModuleProxy is null yet TurboModules are the only native-module path,
    // so bridgeless implies TurboModules active (see RN's TurboModuleRegistry.js).
    isTurboModule: g.__turboModuleProxy != null || isBridgeless,
    isBridgeless,
    isDebugBuild: typeof g.__DEV__ === "boolean" ? g.__DEV__ : undefined,
    reactNativeVersion: reactNativeVersion(),
    platformOs: Platform.OS,
  };
}

export const runtimeProbes: Probe[] = [
  {
    id: "runtime",
    timeoutMs: 100,
    enabled: () => true,
    collect: () => Promise.resolve(collectRuntime()),
  } satisfies Probe<RuntimeSignals>,
];
