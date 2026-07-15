/**
 * Canonical, TS-side source of truth for the iOS URL schemes the native jailbreak detector probes
 * via `-[UIApplication canOpenURL:]`. iOS requires every scheme queried this way to be declared in
 * the host app's `Info.plist` under `LSApplicationQueriesSchemes`, or the call silently returns
 * `false`. `knownAppsSchemes.spec.ts` keeps the TypeScript registry and native arrays aligned so
 * drift fails CI instead of silently degrading detection in production.
 *
 * This list is limited to jailbreak-store schemes. RAT/remote-access app schemes should be added only
 * after confirming each tool actually ships
 * an iOS app with a public, declared URL scheme (do NOT add unverified scheme strings — that is the
 * exact bug above).
 *
 * NOTE: the native side does not import this file at runtime (a static library can't read app JS).
 * It is the drift-test anchor and the human-readable registry; `JailbreakDetector.m` hardcodes the
 * same list, and the spec keeps the two — and the Info.plist — in agreement.
 */
export const IOS_JAILBREAK_QUERY_SCHEMES: readonly string[] = [
  "cydia", // Cydia (classic jailbreak package manager)
  "sileo", // Sileo (modern jailbreak package manager)
  "zbra", // Zebra (package manager)
  "filza", // Filza (jailbreak file manager)
  "undecimus", // unc0ver jailbreak
  "activator", // Activator (Libactivator)
];

/**
 * VPN / anonymity app schemes probed by the app-audit (media_bluetooth_apps). The host application
 * must declare every queried scheme in Info.plist. `knownAppsSchemes.spec.ts` asserts this list is
 * equal to the array hardcoded in MediaBluetoothAppsProvider.m (the real canOpenURL consumer).
 *
 * Do NOT add a scheme that is not already in Info.plist without also adding it there — an undeclared
 * scheme makes canOpenURL silently return false (the dead-Cydia bug this whole registry guards against).
 */
export const IOS_APP_AUDIT_SCHEMES: readonly string[] = [
  "vpn-master",
  "tor-browser",
  "vpn-express-free-mobile-vpn",
  "free-vpn-by-free-vpn-org",
  "vyprvpn",
  "com.simplexsolutionsinc.vpnguard",
  "vpnunlimited",
  "cyberghost",
  "expressvpn",
  "nordvpn",
  "onionbrowser",
  "openvpn",
];
