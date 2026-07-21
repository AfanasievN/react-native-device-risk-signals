import type {RawSignalEvent} from "./DeviceIntel";
import {deriveObservationMetrics} from "./observationMetrics";

function event(probes: RawSignalEvent["probes"]): RawSignalEvent {
  return {
    session_id: "session",
    event_type: "device_intel_collection",
    schema_version: 1,
    collected_at: "2026-07-20T00:00:00.000Z",
    probes,
  };
}

describe("deriveObservationMetrics", () => {
  it("derives bounded ratios and consistency from successful raw probes", () => {
    const metrics = deriveObservationMetrics(
      event({
        hardware: {
          status: "success",
          data: {
            screenWidthPx: 1080,
            screenHeightPx: 2400,
            screenPhysicalWidthPx: 1080,
            screenPhysicalHeightPx: 2400,
            processorCount: 8,
            cpuMaxFrequencyMhz: 2800,
            cpuArchitecture: "arm64-v8a",
            totalMemoryBytes: 8_000,
            freeMemoryBytes: 2_000,
            storageTotalBytes: 10_000,
            storageFreeBytes: 4_000,
            uptimeMs: 100_000,
          },
        },
        application: {status: "success", data: {processUptimeMs: 20_000}},
        os_integrity: {status: "success", data: {isEmulator: false, abi: "arm64-v8a"}},
        network: {status: "timeout"},
        fonts: {status: "skipped", reason: "disabled"},
      }),
    );

    expect(metrics).toMatchObject({
      screenAspectRatio: 2.2222,
      memoryPressureRatio: 0.75,
      storagePressureRatio: 0.6,
      cpuCapacityIndex: 22_400,
      screenGeometryConsistent: true,
      memoryValuesConsistent: true,
      storageValuesConsistent: true,
      processUptimeConsistent: true,
      abiArchitectureConsistent: true,
      successfulProbeCount: 3,
      timeoutProbeCount: 1,
      skippedProbeCount: 1,
    });
  });

  it("omits unsafe arithmetic when inputs are missing or invalid", () => {
    const metrics = deriveObservationMetrics(
      event({
        hardware: {
          status: "success",
          data: {screenWidthPx: 0, screenHeightPx: 2400, totalMemoryBytes: 0, freeMemoryBytes: 2_000},
        },
        application: {status: "error", error: "unavailable"},
      }),
    );

    expect(metrics.screenAspectRatio).toBeUndefined();
    expect(metrics.memoryPressureRatio).toBeUndefined();
    expect(metrics.processUptimeConsistent).toBeUndefined();
    expect(metrics.errorProbeCount).toBe(1);
  });

  it("BUG-R2: reports contradictions independently without producing a verdict", () => {
    const metrics = deriveObservationMetrics(
      event({
        hardware: {
          status: "success",
          data: {
            screenWidthPx: 100,
            screenHeightPx: 200,
            screenPhysicalWidthPx: 100,
            screenPhysicalHeightPx: 300,
            cpuArchitecture: "arm64-v8a",
            totalMemoryBytes: 1_000,
            freeMemoryBytes: 2_000,
            uptimeMs: 100,
          },
        },
        application: {status: "success", data: {processUptimeMs: 200}},
        os_integrity: {
          status: "success",
          data: {isEmulator: false, abi: "x86_64", emulatorVendorMarkers: ["nox"]},
        },
      }),
    );

    expect(metrics).toMatchObject({
      screenGeometryConsistent: false,
      memoryValuesConsistent: false,
      processUptimeConsistent: false,
      abiArchitectureConsistent: false,
      emulatorEvidenceConsistent: false,
    });
    expect(metrics.memoryPressureRatio).toBeUndefined();
    expect(metrics).not.toHaveProperty("riskScore");
  });
});
