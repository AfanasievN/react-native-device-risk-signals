import NativeDeviceIntel, {type GpuBenchmarkSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

/**
 * gpu_benchmark — GPU fingerprint. SHIPS DISABLED (`enabled: () => false` at SOURCE) on both
 * platforms. This is the risk-callout #3 kill-switch: the probe never runs until config flips it on
 * for a cohort, and can be killed instantly without a release. "Approved to build" ≠ "approved to
 * enable" — do NOT change this to `() => true`; enabling belongs to a config change AFTER device-lab
 * calibration (GPU driver variance on low/mid MediaTek/Unisoc devices makes raw numbers incomparable
 * until validated). See NativeDeviceIntel.ts.
 *
 * Definition-of-done for the kill-switch: because enabled() is false, registry.collectAll short-
 * circuits to {status:"skipped"} and NEVER calls collect() — the native EGL/Metal work does not run.
 * gpuBenchmarkProbe.spec.ts asserts exactly that.
 */
export const gpuBenchmarkProbes: Probe[] = [
  {
    id: "gpu_benchmark",
    timeoutMs: 400,
    enabled: () => false,
    collect: () => NativeDeviceIntel.getGpuBenchmark(),
  } satisfies Probe<GpuBenchmarkSignals>,
];
