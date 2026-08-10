export type {ConsentGate} from "./config/consent";
export {ALLOW_ALL_CONSENT, applyConsent, bothConsent, consentFor} from "./config/consent";
export {projectData} from "./config/fieldProjection";
export type {FieldSelection, ProbeConfig, ProbeOverride} from "./config/probeConfig";
export type {ProbeConfigIssue, ProbeConfigIssueCode} from "./config/probeConfig";
export {
  applyConfig,
  DEFAULT_PROBE_CONFIG,
  mergeConfigs,
  ProbeConfigValidationError,
} from "./config/probeConfig";
export type {CollectOptions, DeviceIntelOptions, RawSignalEvent} from "./DeviceIntel";
export {DeviceIntel} from "./DeviceIntel";
export type {DeviceIdentity, OsIntegritySignals} from "./NativeDeviceIntel";
export {androidOnly, iosOnly} from "./probes/platformProbe";
export type {RuntimeSignals} from "./probes/runtimeProbe";
export type {Probe, ProbeOutcome, ProbeResult} from "./probes/types";
// Types are erased at build time, so re-exporting them here costs nothing. The catalog DATA lives
// behind "react-native-device-risk-signals/catalog" — see src/catalog.ts for why.
export type {ProbeDescriptor, ProbeId, ProbePlatform, ProbeSensitivity} from "./probeCatalog";
export {deriveConsistencySignals} from "./consistencySignals";
export type {ConsistencyExpectations, ConsistencySignals} from "./consistencySignals";
export {deriveObservationMetrics} from "./observationMetrics";
export type {ObservationMetrics} from "./observationMetrics";
export type {GpuBenchmarkSignals, NumericConsistencySignals, RuntimeTimingSignals} from "./NativeDeviceIntel";
