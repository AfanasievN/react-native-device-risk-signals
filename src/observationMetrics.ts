import type {RawSignalEvent} from "./DeviceIntel";

export type ObservationMetrics = {
  screenAspectRatio?: number;
  memoryPressureRatio?: number;
  storagePressureRatio?: number;
  cpuCapacityIndex?: number;
  screenGeometryConsistent?: boolean;
  memoryValuesConsistent?: boolean;
  storageValuesConsistent?: boolean;
  processUptimeConsistent?: boolean;
  abiArchitectureConsistent?: boolean;
  emulatorEvidenceConsistent?: boolean;
  observedFieldCount: number;
  successfulProbeCount: number;
  skippedProbeCount: number;
  timeoutProbeCount: number;
  errorProbeCount: number;
};

type SignalData = Record<string, unknown>;

function data(event: RawSignalEvent, id: string): SignalData | undefined {
  const outcome = event.probes[id];
  return outcome?.status === "success" && typeof outcome.data === "object" && outcome.data !== null
    ? (outcome.data as SignalData)
    : undefined;
}

function finiteNumber(record: SignalData | undefined, key: string): number | undefined {
  const value = record?.[key];
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function nonEmptyString(record: SignalData | undefined, key: string): string | undefined {
  const value = record?.[key];
  return typeof value === "string" && value.trim().length > 0 ? value.trim().toLowerCase() : undefined;
}

function round(value: number): number {
  return Math.round(value * 10_000) / 10_000;
}

function pressure(free: number | undefined, total: number | undefined): number | undefined {
  if (free === undefined || total === undefined || total <= 0 || free < 0 || free > total) return undefined;
  return round(1 - free / total);
}

function normalizedArchitecture(value: string | undefined): string | undefined {
  if (!value) return undefined;
  if (value.includes("arm64") || value.includes("aarch64")) return "arm64";
  if (value.includes("armeabi") || value === "arm") return "arm";
  if (value.includes("x86_64") || value.includes("amd64")) return "x86_64";
  if (value.includes("x86")) return "x86";
  return value;
}

/** Pure, local arithmetic over an existing event. It performs no native reads and creates no id. */
export function deriveObservationMetrics(event: RawSignalEvent): ObservationMetrics {
  const hardware = data(event, "hardware");
  const application = data(event, "application");
  const integrity = data(event, "os_integrity");
  const width = finiteNumber(hardware, "screenWidthPx");
  const height = finiteNumber(hardware, "screenHeightPx");
  const physicalWidth = finiteNumber(hardware, "screenPhysicalWidthPx");
  const physicalHeight = finiteNumber(hardware, "screenPhysicalHeightPx");
  const freeMemory = finiteNumber(hardware, "freeMemoryBytes");
  const totalMemory = finiteNumber(hardware, "totalMemoryBytes");
  const freeStorage = finiteNumber(hardware, "storageFreeBytes");
  const totalStorage = finiteNumber(hardware, "storageTotalBytes");
  const processorCount = finiteNumber(hardware, "processorCount");
  const maxFrequency = finiteNumber(hardware, "cpuMaxFrequencyMhz");
  const uptime = finiteNumber(hardware, "uptimeMs");
  const processUptime = finiteNumber(application, "processUptimeMs");
  const result: ObservationMetrics = {
    observedFieldCount: 0,
    successfulProbeCount: 0,
    skippedProbeCount: 0,
    timeoutProbeCount: 0,
    errorProbeCount: 0,
  };

  for (const outcome of Object.values(event.probes)) {
    if (outcome.status === "success") {
      result.successfulProbeCount++;
      if (typeof outcome.data === "object" && outcome.data !== null) {
        result.observedFieldCount += Object.keys(outcome.data).length;
      }
    } else if (outcome.status === "skipped") result.skippedProbeCount++;
    else if (outcome.status === "timeout") result.timeoutProbeCount++;
    else result.errorProbeCount++;
  }

  if (width !== undefined && height !== undefined && width > 0 && height > 0) {
    result.screenAspectRatio = round(Math.max(width, height) / Math.min(width, height));
  }
  result.memoryPressureRatio = pressure(freeMemory, totalMemory);
  result.storagePressureRatio = pressure(freeStorage, totalStorage);
  if (processorCount !== undefined && processorCount > 0 && maxFrequency !== undefined && maxFrequency > 0) {
    result.cpuCapacityIndex = round(processorCount * maxFrequency);
  }
  if (width !== undefined && height !== undefined && physicalWidth !== undefined && physicalHeight !== undefined &&
    width > 0 && height > 0 && physicalWidth > 0 && physicalHeight > 0
  ) {
    const logicalRatio = Math.max(width, height) / Math.min(width, height);
    const physicalRatio = Math.max(physicalWidth, physicalHeight) / Math.min(physicalWidth, physicalHeight);
    result.screenGeometryConsistent = Math.abs(logicalRatio - physicalRatio) <= 0.02;
  }
  if (freeMemory !== undefined && totalMemory !== undefined) {
    result.memoryValuesConsistent = totalMemory > 0 && freeMemory >= 0 && freeMemory <= totalMemory;
  }
  if (freeStorage !== undefined && totalStorage !== undefined) {
    result.storageValuesConsistent = totalStorage > 0 && freeStorage >= 0 && freeStorage <= totalStorage;
  }
  if (uptime !== undefined && processUptime !== undefined) {
    result.processUptimeConsistent = uptime >= 0 && processUptime >= 0 && processUptime <= uptime;
  }
  const abi = normalizedArchitecture(nonEmptyString(integrity, "abi"));
  const cpuArchitecture = normalizedArchitecture(nonEmptyString(hardware, "cpuArchitecture"));
  if (abi !== undefined && cpuArchitecture !== undefined) result.abiArchitectureConsistent = abi === cpuArchitecture;

  const isEmulator = integrity?.isEmulator;
  const strongEvidence = ["emulatorFilePaths", "emulatorSystemPropertyMarkers", "emulatorCpuMarkers", "emulatorVendorMarkers"]
    .some((key) => Array.isArray(integrity?.[key]) && (integrity?.[key] as unknown[]).length > 0);
  if (typeof isEmulator === "boolean") result.emulatorEvidenceConsistent = isEmulator || !strongEvidence;

  return result;
}
