import NativeDeviceIntel, {type AudioLatencySignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

/**
 * audio_latency — audio-pipeline fingerprint. SHIPS DISABLED (`enabled: () => false` at SOURCE) on
 * both platforms, same risk-callout #3 treatment as gpu_benchmark: enabled per-cohort via config
 * after validation, never runs until then. Cheap property reads (no engine lifecycle), but gated
 * anyway because it is a fingerprinting signal. Do NOT change this to `() => true`.
 */
export const audioLatencyProbes: Probe[] = [
  {
    id: "audio_latency",
    timeoutMs: 300,
    enabled: () => false,
    collect: () => NativeDeviceIntel.getAudioLatency(),
  } satisfies Probe<AudioLatencySignals>,
];
