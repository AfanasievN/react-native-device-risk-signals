import {applyConfig} from "../config/probeConfig";
import NativeDeviceIntel from "../NativeDeviceIntel";
import {assertDefined} from "../testing/assertDefined";
import {audioLatencyProbes} from "./audioLatencyProbe";
import {gpuBenchmarkProbes} from "./gpuBenchmarkProbe";
import {collectAll} from "./registry";
import type {Probe} from "./types";

jest.mock("../NativeDeviceIntel", () => ({
  __esModule: true,
  default: {
    getGpuBenchmark: jest.fn(() => Promise.resolve({benchmarkPerformed: true})),
    getAudioLatency: jest.fn(() => Promise.resolve({measured: true})),
  },
}));

function only(probes: Probe[]): Probe {
  expect(probes).toHaveLength(1);
  return assertDefined(probes[0]);
}

describe("gpu_benchmark / audio_latency ship DISABLED (risk-callout #3)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("gpu_benchmark is present in the registry but disabled at source", () => {
    const probe = only(gpuBenchmarkProbes);
    expect(probe.id).toBe("gpu_benchmark");
    expect(probe.enabled()).toBe(false);
  });

  it("audio_latency is present in the registry but disabled at source", () => {
    const probe = only(audioLatencyProbes);
    expect(probe.id).toBe("audio_latency");
    expect(probe.enabled()).toBe(false);
  });

  // Definition-of-Done: the kill-switch PREVENTS the native call — it does not run it and discard the
  // result. registry.collectAll short-circuits a disabled probe to {status:"skipped"} without ever
  // calling collect(), so the native EGL/Metal/AVAudioSession work never executes.
  it("collectAll never invokes the native gpu/audio methods while disabled", async () => {
    const results = await collectAll([...gpuBenchmarkProbes, ...audioLatencyProbes]);
    for (const result of results) {
      expect(result.outcome).toEqual({status: "skipped", reason: "disabled"});
    }
    expect(NativeDeviceIntel.getGpuBenchmark).not.toHaveBeenCalled();
    expect(NativeDeviceIntel.getAudioLatency).not.toHaveBeenCalled();
  });

  it("config can flip them on — then collectAll DOES invoke the native methods (kill-switch works both ways)", async () => {
    const enabled = applyConfig([...gpuBenchmarkProbes, ...audioLatencyProbes], {
      probes: {gpu_benchmark: {enabled: true}, audio_latency: {enabled: true}},
    });
    await collectAll(enabled);
    expect(NativeDeviceIntel.getGpuBenchmark).toHaveBeenCalledTimes(1);
    expect(NativeDeviceIntel.getAudioLatency).toHaveBeenCalledTimes(1);
  });
});
