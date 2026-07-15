import NativeDeviceIntel, {type GeolocationSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// geolocation — opportunistic, never prompts (see GeolocationSignals doc). The native side returns a
// last-known/coarse fix only when permission is already granted, and mock-provider flags regardless.
export const geolocationProbes: Probe[] = [
  {
    id: "geolocation",
    timeoutMs: 400,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getGeolocationSignals(),
  } satisfies Probe<GeolocationSignals>,
];
