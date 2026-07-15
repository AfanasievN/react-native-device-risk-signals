import NativeDeviceIntel, {type NetworkSignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

// network — connectivity/VPN/proxy/interface topology. Permission-free on both platforms. SSID is
// expected null (see NetworkSignals doc); the entropy here is VPN/proxy presence and the interface
// list (tun/utun/tap overlays), not the SSID.
export const networkProbes: Probe[] = [
  {
    id: "network",
    timeoutMs: 400,
    enabled: () => true,
    collect: () => NativeDeviceIntel.getNetworkSignals(),
  } satisfies Probe<NetworkSignals>,
];
