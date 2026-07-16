import NativeDeviceIntel, {
  type DeviceSecurityPostureSignals,
  type TransactionSafetySignals,
} from "../NativeDeviceIntel";
import type {Probe} from "./types";

export const securityPostureProbes: Probe[] = [
  {
    id: "device_security_posture",
    timeoutMs: 300,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getDeviceSecurityPosture(),
  } satisfies Probe<DeviceSecurityPostureSignals>,
  {
    id: "transaction_safety",
    timeoutMs: 600,
    enabled: () => false,
    collect: () => NativeDeviceIntel.getTransactionSafetySignals(),
  } satisfies Probe<TransactionSafetySignals>,
];
