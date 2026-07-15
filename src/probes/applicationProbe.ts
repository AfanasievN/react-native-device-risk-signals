import NativeDeviceIntel, {type ApplicationSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// application — the host app's own version/build/bundle id. Permission-free, trivial. A
// repackaging/version-consistency signal (cloned build, mismatched version). See NativeDeviceIntel.ts.
export const applicationProbes: Probe[] = [
  {
    id: "application",
    timeoutMs: 200,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getApplicationSignals(),
  } satisfies Probe<ApplicationSignals>,
];
