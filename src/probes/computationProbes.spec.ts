jest.mock("../NativeDeviceIntel", () => ({
  __esModule: true,
  default: {
    getRuntimeTimingSignals: jest.fn(async () => ({
      nativeClockSource: "elapsed_realtime_nanos",
      nativeSampleCount: 5,
      nativeTimerResolutionNs: 10,
      nativeIntervalMedianNs: 20,
      nativeIntervalP95Ns: 40,
      nativeIntervalMadNs: 5,
    })),
    getNumericConsistencySignals: jest.fn(async () => ({
      integerVectorResult: 3373885893,
      floatVector: [Math.sqrt(2), Math.sin(0.5), Math.cos(0.5), Math.log(2), Math.exp(0.25)],
      signedZeroPreserved: true,
      subnormalPreserved: true,
    })),
  },
}));

import {numericConsistencyProbes} from "./numericConsistencyProbe";
import {runtimeTimingProbes} from "./runtimeTimingProbe";
import NativeDeviceIntel, {type NativeRuntimeTimingSignals} from "../NativeDeviceIntel";

describe("active computation probes", () => {
  it("preserves omitted native timing measurements", async () => {
    const unavailable: NativeRuntimeTimingSignals = {
      nativeClockSource: "elapsed_realtime_nanos",
      nativeSampleCount: 0,
    };
    jest.mocked(NativeDeviceIntel.getRuntimeTimingSignals).mockResolvedValueOnce(unavailable);
    const result = await runtimeTimingProbes[0]?.collect();
    expect(result).toMatchObject(unavailable);
    for (const field of ["nativeTimerResolutionNs", "nativeIntervalMedianNs", "nativeIntervalP95Ns", "nativeIntervalMadNs"]) {
      expect(result).not.toHaveProperty(field);
    }
  });

  it("ship disabled until physical-device calibration", () => {
    expect(runtimeTimingProbes[0]?.enabled()).toBe(false);
    expect(numericConsistencyProbes[0]?.enabled()).toBe(false);
  });

  it("keeps JS and native numeric result agreement explainable", async () => {
    const result = await numericConsistencyProbes[0]?.collect();
    expect(result).toMatchObject({
      integerVectorMatches: true,
      integerMismatchCount: 0,
      floatSampleCount: 5,
      floatMismatchCount: 0,
      signedZeroPreserved: true,
      subnormalPreserved: true,
    });
  });

  it("returns bounded JS and native timing aggregates", async () => {
    const result = await runtimeTimingProbes[0]?.collect();
    expect(result).toEqual(
      expect.objectContaining({
        jsClockSource: expect.any(String),
        jsTimerSampleCount: expect.any(Number),
        jsTimerResolutionMs: expect.any(Number),
        eventLoopSampleCount: expect.any(Number),
        eventLoopP50Ms: expect.any(Number),
        nativeClockSource: "elapsed_realtime_nanos",
        nativeTimerResolutionNs: 10,
        bridgeRoundTripMs: expect.any(Number),
      }),
    );
  });
});
