import {type TurboModule, TurboModuleRegistry} from "react-native";

// ─────────────────────────────────────────────────────────────────────────────────────────────
// CONTRACT NOTES (read before editing)
//
// This file is the TurboModule codegen spec (referenced by codegenConfig in package.json). It is
// the single source of truth for the TS <-> Kotlin <-> Obj-C boundary — the Android
// `DeviceIntelModule` and iOS `DeviceIntel.mm` MUST implement every method here, and the raw-field
// keys each native side puts into its result map MUST match the property names below verbatim.
//
// Every signal below is RAW: a native method returns an observation, never a verdict. There is no
// on-device "isRooted"/"isFraud" boolean — the risk backend fuses these into a score. This is a
// deliberate design choice: shipping a verdict client-side hands an attacker the exact boolean to
// patch out and throws away the entropy
// of the individual signals.
//
// Optional (`?:`) fields mean "may be genuinely unavailable on this OS/device/permission state" —
// codegen makes them nullable, and the native side OMITS the key rather than inventing a value. A
// missing key downstream reads as "could not observe", which is itself information. Required
// booleans default to `false` when a check could not run, and pair with a `*Performed`/`*Available`
// flag where "false because it didn't run" must be distinguished from "false because negative".
// ─────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Full Android Build fingerprint — the raw `android.os.Build` surface (all permission-free reads).
 * Android-only; nested under DeviceIdentity.androidBuild and omitted on iOS. High fingerprint entropy
 * and strong consistency/emulator tells (e.g. fingerprint/tags "test-keys", odd socModel vs claimed
 * model, missing securityPatch). Every field optional — API-level-gated ones are omitted where
 * unavailable.
 */
export type AndroidBuildInfo = {
  board?: string;
  bootloader?: string;
  device?: string;
  display?: string;
  fingerprint?: string;
  hardware?: string;
  host?: string;
  id?: string;
  product?: string;
  tags?: string; // "release-keys" vs "test-keys" — a classic custom-ROM/emulator tell.
  buildType?: string; // Build.TYPE: "user" / "userdebug" / "eng".
  supportedAbis?: string[];
  sdkInt?: number; // Build.VERSION.SDK_INT.
  codename?: string; // Build.VERSION.CODENAME.
  incremental?: string; // Build.VERSION.INCREMENTAL.
  securityPatch?: string; // Build.VERSION.SECURITY_PATCH (API 23+).
  baseOs?: string; // Build.VERSION.BASE_OS (API 23+).
  socManufacturer?: string; // Build.SOC_MANUFACTURER (API 31+).
  socModel?: string; // Build.SOC_MODEL (API 31+).
};

export type DeviceIdentity = {
  manufacturer: string;
  model: string;
  brand: string;
  systemName: string;
  systemVersion: string;
  isTablet: boolean;
  // OS / kernel fingerprint (both platforms; optional). Android: android.system.Os.uname() +
  // Build.DISPLAY. iOS: sysctl kern.osversion/version/osrelease/ostype (NONE are Required-Reason APIs;
  // only kern.boottime is, and that is deliberately not read here).
  osBuild?: string; // Android Build.DISPLAY / iOS kern.osversion (e.g. "21A342").
  kernelVersion?: string; // uname version / kern.version — high-entropy kernel build string.
  kernelOsRelease?: string; // uname release / kern.osrelease.
  kernelOsType?: string; // uname sysname / kern.ostype (e.g. "Linux" / "Darwin").
  androidBuild?: AndroidBuildInfo; // Android-only; omitted on iOS.
};

/**
 * application — the host app's own identity, install provenance, and process/permission state.
 * Repackaging & version-consistency signal (a cloned/tampered build, a version that doesn't match the
 * backend, a sideloaded installer, an unexpected permission grant, a monolithic APK where a Play split
 * bundle is expected). All reads are of the app's OWN package only — permission-free, no
 * QUERY_ALL_PACKAGES.
 */
export type ApplicationSignals = {
  appVersion?: string; // versionName / CFBundleShortVersionString.
  appBuild?: string; // versionCode / CFBundleVersion.
  bundleId?: string; // packageName / CFBundleIdentifier.
  appName?: string; // application label / CFBundleDisplayName.
  // Android install source: "com.android.vending" = Play Store; a null / unknown / sideload installer
  // is a strong risk signal. iOS omitted (no equivalent API).
  installerPackage?: string;
  isForeground?: boolean; // app currently in the foreground (Android RunningAppProcessInfo importance).
  processUptimeMs?: number; // ms since this app process started (Android) — a launched-for-automation tell.
  // The host app's OWN requested permissions that are currently granted (Android; reads only our own
  // package). An unusual grant set is a consistency/automation tell. iOS omitted (no enumerable list).
  grantedPermissions?: string[];
  // Android split-APK (App Bundle) delivery. A repackaged/cloned build is typically a single monolithic
  // APK, so absence of the expected splits is a tamper tell. iOS omitted.
  isSplitApks?: boolean;
  splitNames?: string[];
  // Android install-provenance timestamps (PackageManager; permission-free, own package only). A very
  // recent firstInstallTimeMs or a lastUpdateTimeMs
  // that doesn't line up with the expected Play rollout are fraud/tamper tells — the on-device analog
  // of Amplitude's start_version. iOS omits (no public install-time API).
  firstInstallTimeMs?: number; // epoch ms of first install.
  lastUpdateTimeMs?: number; // epoch ms of the last update (== firstInstallTimeMs if never updated).
};

/**
 * hardware — permission-free device-class + fingerprint entropy: screen geometry, CPU, RAM, battery,
 * and brightness (the
 * installed-fonts digest is its own `fonts` probe — see FontsSignals). High fingerprint value AND
 * emulator/device-farm tells (round/identical RAM,
 * low core count, pinned battery, non-standard resolution recurring across a fleet). All reads are
 * permission-free and touch NO Apple Required-Reason API. Kept OFF iOS for policy reasons: disk/storage
 * size (iOS disk = Required-Reason API — so storage is collected on ANDROID ONLY, omitted on iOS),
 * device boot time on iOS (Required-Reason — Android uptime stands in where it is free), and any
 * persistent device id (vendorId/ANDROID_ID — never collected on either platform). See docs.
 */
export type HardwareSignals = {
  // Screen (both) — classic, high-entropy fingerprint.
  screenWidthPx?: number; // logical points × scale.
  screenHeightPx?: number;
  screenDensity?: number; // logical density / UIScreen.scale.
  screenDpi?: number; // Android DisplayMetrics.densityDpi.
  screenPhysicalWidthPx?: number; // Android getRealMetrics / iOS nativeBounds.
  screenPhysicalHeightPx?: number;
  screenPhysicalDensity?: number;
  screenBrightness?: number; // 0..1 (iOS perceptual; Android raw setting/255 — server can normalize).
  screenOrientation?: string; // "portrait" | "landscape" — Android Configuration.orientation. iOS omitted (main-thread UIKit).
  // CPU.
  processorCount?: number; // logical cores — device-class + emulator tell.
  cpuArchitecture?: string; // Android os.arch (arm64-v8a/x86/...) — emulator tell. iOS omitted (arm64 universal).
  cpuMaxFrequencyMhz?: number; // Android /sys cpufreq max (device-class tell). iOS omitted (no reliable public API).
  // Memory.
  totalMemoryBytes?: number; // physical RAM (Android ActivityManager.totalMem / iOS hw.memsize).
  freeMemoryBytes?: number; // available RAM (Android availMem / iOS mach vm free).
  isLowMemory?: boolean; // Android ActivityManager.MemoryInfo.lowMemory.
  // Battery — a device-farm tell (pinned 100% / always charging / fixed temp).
  batteryLevel?: number; // 0..1.
  batteryState?: string; // "charging" | "unplugged" | "full" | "unknown"
  batteryTemperatureC?: number; // Android only.
  // Storage — ANDROID ONLY (StatFs; permission-free, not a restricted API). Omitted on iOS because
  // iOS disk-space IS an Apple Required-Reason API and this SDK deliberately touches none. Round /
  // identical total storage recurring across a fleet is a device-farm/emulator tell.
  storageTotalBytes?: number;
  storageFreeBytes?: number;
  // Uptime — Android ONLY. iOS boot time is a Required-Reason API (deferred by policy).
  uptimeMs?: number;
};

/**
 * fonts — the installed-fonts fingerprint, split out of `hardware` into its OWN probe with a generous
 * timeout. Font enumeration + SHA-256 is the one heavy device read; isolating it means a slow low-end
 * device can time THIS out without taking the fast hardware fields down with it (that is exactly why it
 * was split — see the hardware probe). Permission-free, no Required-Reason API. NB: Android hashes font
 * FILE names, iOS hashes family/PostScript names — so a digest is comparable only WITHIN one platform.
 */
export type FontsSignals = {
  fontsDigest?: string; // SHA-256 hex of the sorted installed font names.
};

/**
 * os_integrity — the fast, synchronous, permission-free bundle: root/jailbreak file & property
 * checks, hook/injection scans, and emulator heuristics. Multi-method by design; each field is one
 * independent observation. Android and iOS each fill the subset that applies to them and omit the
 * rest (e.g. `magiskMountsFound` is Android-only; `canOpenJailbreakScheme` is iOS-only).
 */
export type OsIntegritySignals = {
  // Baseline (both platforms; present since the skeleton — kept required for backward-compat).
  isEmulator: boolean;
  isDebuggerAttached: boolean;
  developerModeEnabled: boolean; // Android: Settings.Global.DEVELOPMENT_SETTINGS_ENABLED (the
  // Developer-Options master toggle). iOS: no public API — always false.
  usbDebuggingEnabled?: boolean; // Android-only: Settings.Global.ADB_ENABLED (USB debugging), a
  // DISTINCT signal from developerModeEnabled — Developer Options can be on with ADB off. Omitted on iOS.

  // Root / jailbreak — file, binary and package/app evidence.
  suBinaryFound?: boolean; // Android: `su` on any PATH dir. iOS: jailbreak shells (bash/sh/ssh) present.
  rootManagementAppFound?: boolean; // Android: Magisk/SuperSU pkg. iOS: Cydia/Sileo/etc. openable.
  suspiciousFilePathsFound?: boolean; // Known root/jb file paths readable via access().
  suspiciousFilePaths?: string[]; // The specific paths that were found (raw, for server triage).
  writableSystemPathFound?: boolean; // Android: /system et al writable. iOS: wrote a file outside sandbox.
  dangerousPropsPresent?: boolean; // Android: ro.debuggable=1 / ro.secure=0. iOS: n/a (omitted).
  canOpenJailbreakScheme?: boolean; // iOS: canOpenURL cydia:// / sileo:// / etc. Android: n/a.
  symbolicLinksSuspicious?: boolean; // iOS: /Applications, /var/stash etc. are symlinks (jb telltale).

  // Hook / injection frameworks (Frida non-port evidence, Substrate, Xposed, cycript...).
  injectedLibrariesFound?: boolean; // Android: /proc/self/maps has an unexpected .so. iOS: dyld injected image.
  injectedLibraryNames?: string[]; // Raw names of the suspicious mapped libraries / dyld images.
  hookFrameworkFound?: boolean; // Aggregate: any of the above name-matched a known hook framework.

  // Android integrity properties (all omitted on iOS).
  magiskMountsFound?: boolean; // /proc/self/mountinfo shows a Magisk-style bind mount.
  verifiedBootState?: string; // ro.boot.verifiedbootstate: "green" | "yellow" | "orange" | ...
  bootloaderLocked?: boolean; // ro.boot.flash.locked == "1".
  selinuxEnforcing?: boolean; // getenforce / ro.boot.selinux.
  ldPreloadSet?: boolean; // LD_PRELOAD env var non-empty (portable Frida/gadget preload tell).
  ldPreloadValue?: string; // Raw LD_PRELOAD contents when set.
  hiddenApiPolicy?: string; // settings global hidden_api_policy (reflection-unlock tell).

  // Emulator / device-farm heuristics (Android-oriented; iOS reports isEmulator via simulator only).
  emulatorFingerprintMatch?: boolean; // Build.FINGERPRINT/MODEL/etc. matches a known emulator sig.
  emulatorFilesFound?: boolean; // /dev/qemu_pipe, /dev/socket/qemud, genyd, etc.
  sensorCount?: number; // Very low / zero sensor count is a strong emulator tell.
  abi?: string; // Primary ABI; x86/x86_64 on a physical phone can be suspicious.

  // iOS misc raw.
  dyldImageCount?: number; // Total loaded dyld images (raw; abnormal counts correlate with tweaks).
};

/**
 * os_integrity_frida_scan — the ACTIVE tamper probe. Separated from the fast bundle because it does
 * blocking socket I/O and must run off the main thread with its own (longer) timeout and its own
 * kill-switch. Android does the real scan; iOS returns `scanPerformed: false` (frida on jailbroken
 * iOS is covered by the dyld image scan in the fast bundle instead — see plan).
 */
export type FridaScanSignals = {
  scanPerformed: boolean; // false on iOS (stub) or when the probe was skipped.
  defaultPortOpen?: boolean; // TCP connect to 127.0.0.1:27042 (frida-server default) succeeded.
  scannedPort?: number; // The port that was probed (27042), echoed for traceability.
};

/**
 * os_integrity_fork_test — the single genuinely risky native call in the whole SDK: a fork()-based
 * jailbreak check (a sandboxed app cannot fork; success ⇒ sandbox escaped). SHIPS DISABLED
 * (`enabled: () => false` at source) until QA on real jailbroken AND normal devices confirms it
 * never crashes or leaks a zombie process. Its own flag, separate from the GPU/audio kill-switch —
 * the risk here is stability, not App Store policy. iOS-only; Android returns `testPerformed: false`.
 */
export type ForkJailbreakSignal = {
  testPerformed: boolean; // false on Android (stub) or when disabled.
  forkSucceeded?: boolean; // true ⇒ fork() returned a child pid ⇒ not sandboxed ⇒ jailbroken.
};

/**
 * network — connectivity, VPN/proxy, and interface topology. No SSID on Android (ACCESS_FINE_LOCATION
 * is `tools:node="remove"` in the app manifest by existing decision — documented, not a bug), and no
 * SSID on iOS without the Wi-Fi-info entitlement; those fields are therefore expected-null. Public-IP
 * discovery is intentionally NOT here (it needs an outbound call to a configurable echo endpoint —
 * that belongs in the JS/transport layer, not a permission-free native read).
 */
export type NetworkSignals = {
  isConnected?: boolean;
  connectionType?: string; // "wifi" | "cellular" | "ethernet" | "vpn" | "none" | "other"
  isMetered?: boolean;
  isVpnActive?: boolean; // TRANSPORT_VPN (Android) or a tun/utun/ppp interface present.
  isProxyConfigured?: boolean; // A system HTTP proxy is set.
  proxyHost?: string;
  proxyPort?: number;
  wifiSsid?: string; // Expected null on both platforms (see type doc) — kept for contract stability.
  wifiBssid?: string;
  interfaceNames?: string[]; // NetworkInterface / getifaddrs names — reveals tun/tap/utun overlays.
  localIpAddresses?: string[]; // Non-loopback interface addresses.
  linkDownstreamKbps?: number; // Android NetworkCapabilities link speed estimate.
  linkUpstreamKbps?: number;
  // Traffic counters (Android only — TrafficStats; iOS has no public API, omitted). CUMULATIVE bytes
  // SINCE BOOT, not a rate — pair with hardware.uptimeMs (or a session delta) to derive an average.
  // Permission-free counters that can be normalized into average traffic rates.
  mobileRxBytes?: number; // cellular bytes received.
  mobileTxBytes?: number; // cellular bytes transmitted.
  wifiRxBytes?: number; // non-cellular (total − mobile) received.
  wifiTxBytes?: number; // non-cellular (total − mobile) transmitted.
};

/**
 * telephony — opportunistic only (we never request READ_PHONE_STATE or trigger a permission prompt).
 * IMEI is expected null (Android: READ_PRIVILEGED_PHONE_STATE is unavailable to 3rd parties;
 * iOS: never exposed). iOS CoreTelephony carrier fields are progressively nulled by Apple since
 * iOS 16 for non-carrier apps — documented degradation, not a promised field.
 */
export type TelephonySignals = {
  phoneType?: string; // "gsm" | "cdma" | "none"
  networkOperatorName?: string;
  simOperatorName?: string;
  networkCountryIso?: string;
  simCountryIso?: string;
  simState?: string;
  simCount?: number; // Active SIM count (dual-SIM tell).
  hasIccCard?: boolean;
  isNetworkRoaming?: boolean;
  dataState?: string;
  // iOS CoreTelephony (mostly null on iOS 16+); Android leaves these omitted.
  carrierMobileCountryCode?: string;
  carrierMobileNetworkCode?: string;
  carrierAllowsVoip?: boolean;
  imei?: string; // Expected null — present in the contract only to make the absence explicit.
};

/**
 * locale — trivial, permission-free, and high-entropy for fraud (mismatched keyboard/timezone/currency
 * vs claimed identity). Includes installed keyboard/input-method languages, which are a strong
 * device-farm tell and need no permission on either platform.
 */
export type LocaleSignals = {
  language?: string;
  languages?: string[]; // Full preferred-language ordering.
  country?: string;
  timezoneId?: string;
  timezoneOffsetMinutes?: number;
  uses24HourClock?: boolean;
  currencyCode?: string;
  calendar?: string;
  decimalSeparator?: string;
  groupingSeparator?: string;
  measurementSystem?: string; // "metric" | "us" | "uk" (iOS-rich; Android best-effort).
  firstDayOfWeek?: number;
  keyboardLanguages?: string[]; // iOS: enabled keyboards. Android: enabled input-method locales.
};

/**
 * geolocation — OPPORTUNISTIC only. Never triggers a permission prompt: on Android we read a
 * last-known fix only if COARSE is already granted; on iOS we read CLLocationManager state without
 * ever calling requestAuthorization. Coarse precision is fine — the fraud value is mock-location
 * detection and coordinate-vs-claimed-address mismatch, not pinpoint accuracy.
 */
export type GeolocationSignals = {
  authorizationStatus?: string; // ios: notDetermined/denied/restricted/authorizedWhenInUse/authorizedAlways; android: granted/denied
  hasCoarsePermission?: boolean;
  latitude?: number; // last-known / coarse only; omitted when not authorized or no fix.
  longitude?: number;
  accuracyMeters?: number;
  altitudeMeters?: number;
  isFromMockProvider?: boolean; // android: Location.isMock/isFromMockProvider — a direct spoofing tell.
  mockLocationAppsFound?: boolean; // android: an installed app holding mock-location capability.
  provider?: string; // android: gps/network/fused.
  locationAgeMs?: number; // staleness of the fix.
  gnssSupported?: boolean; // hardware has GNSS at all (emulator tell when false on a "phone").
};

/**
 * media_bluetooth_apps — audio route, screen-capture/mirroring, installed/openable flagged apps, and
 * accessibility-service enumeration (a device-farm automation tell no vendor catalogued). iOS
 * Bluetooth is intentionally EXCLUDED (it would require a new NSBluetoothAlwaysUsageDescription
 * prompt); Android Bluetooth uses already-granted permissions.
 */
export type MediaBluetoothAppsSignals = {
  audioOutputRoute?: string; // ios: speaker/headphones/bluetooth/car; android: primary output device type.
  isMusicActive?: boolean;
  isOtherAudioPlaying?: boolean; // ios.
  isScreenCaptured?: boolean; // ios: UIScreen.isCaptured. Android omitted (no point-in-time API < API 35).
  isScreenMirrored?: boolean; // ios: an additional (non-builtin) UIScreen is attached.
  bluetoothBondedDeviceCount?: number; // android only. Count only — the device NAMES are deliberately
  // NOT collected (they are PII: names of the user's other devices/peripherals). Compliance minimization.
  installedFlaggedApps?: string[]; // android: package ids present from KnownAppLists (root/hook/RAT).
  openableFlaggedSchemes?: string[]; // ios: URL schemes canOpenURL returned true for (from declared set).
  enabledAccessibilityServices?: string[]; // android: Settings.Secure ENABLED_ACCESSIBILITY_SERVICES.
  accessibilityRunning?: boolean; // ios: any assistive tech active (VoiceOver/SwitchControl/GuidedAccess/...).
  accessibilityFeatures?: string[]; // ios: which assistive features are on.
};

/**
 * gpu_benchmark — native GPU characteristics and timing without a WebView.
 * The high-entropy signal is the renderer/vendor/version strings (exact GPU model); the draw-call
 * throughput is a secondary timing signal. RISK-CALLOUT #3: Apple's Required-Reason / anti-fingerprint
 * policy applies regardless of consent — this probe SHIPS DISABLED (enabled: () => false at source)
 * on BOTH platforms and is turned on per-cohort via config ONLY after device-lab calibration ("approved
 * to build" ≠ "approved to enable"). It self-skips on emulators/simulator (timing there is noise).
 */
export type GpuBenchmarkSignals = {
  benchmarkPerformed: boolean; // false when skipped (emulator/simulator/unsupported/error).
  skippedReason?: string; // "emulator" | "unsupported" | "error"
  rendererName?: string; // GL_RENDERER (Android) / MTLDevice.name (iOS) — the GPU model string.
  vendorName?: string; // GL_VENDOR (Android).
  apiVersion?: string; // GL_VERSION (Android) / Metal GPU family (iOS).
  shadingLanguageVersion?: string; // GL_SHADING_LANGUAGE_VERSION (Android).
  maxTextureSize?: number; // GL_MAX_TEXTURE_SIZE (Android) — a device-class tell.
  drawCallsCompleted?: number; // draw calls finished within the fixed time budget.
  durationMs?: number; // actual wall time of the benchmark loop.
  gpuTimeMs?: number; // iOS: MTLCommandBuffer GPUEndTime − GPUStartTime for the workload.
};

/**
 * audio_latency — audio-pipeline fingerprint (AudioContext-equivalent, native). Cheap property reads
 * first (no engine lifecycle). SHIPS DISABLED alongside gpu_benchmark (risk-callout #3); enabled via
 * config after validation. Needs no permission on either platform.
 */
export type AudioLatencySignals = {
  measured: boolean; // false when the platform exposes nothing usable.
  outputLatencyMs?: number; // iOS AVAudioSession.outputLatency; Android derived (framesPerBuffer/sampleRate).
  inputLatencyMs?: number; // iOS AVAudioSession.inputLatency.
  ioBufferDurationMs?: number; // iOS AVAudioSession.ioBufferDuration.
  framesPerBuffer?: number; // Android PROPERTY_OUTPUT_FRAMES_PER_BUFFER.
  nativeSampleRate?: number; // Android PROPERTY_OUTPUT_SAMPLE_RATE / iOS AVAudioSession.sampleRate.
};

// NOTE: Bonjour discovery is intentionally omitted because it requires the host application to add
// NSLocalNetworkUsageDescription and display a user-facing permission prompt.

export interface Spec extends TurboModule {
  // Ephemeral session UUID generated by the platform CSPRNG.
  getRandomSessionId: () => string;
  // device_identity
  getDeviceIdentity: () => Promise<DeviceIdentity>;
  // hardware (screen/cpu/memory/battery/brightness/storage — permission-free fingerprint)
  getHardwareSignals: () => Promise<HardwareSignals>;
  // fonts (installed-fonts SHA-256 digest — its own probe; heavy read isolated from hardware)
  getFontsFingerprint: () => Promise<FontsSignals>;
  // os_integrity (fast bundle)
  getOsIntegrity: () => Promise<OsIntegritySignals>;
  // os_integrity_frida_scan (active socket probe; Android real, iOS stub)
  getFridaScanSignals: () => Promise<FridaScanSignals>;
  // os_integrity_fork_test (iOS fork() jailbreak probe; ships disabled; Android stub)
  getForkJailbreakSignal: () => Promise<ForkJailbreakSignal>;
  // network
  getNetworkSignals: () => Promise<NetworkSignals>;
  // telephony
  getTelephonySignals: () => Promise<TelephonySignals>;
  // locale
  getLocaleSignals: () => Promise<LocaleSignals>;
  // geolocation
  getGeolocationSignals: () => Promise<GeolocationSignals>;
  // media_bluetooth_apps
  getMediaBluetoothAppsSignals: () => Promise<MediaBluetoothAppsSignals>;
  // gpu_benchmark (ships disabled — risk-callout #3)
  getGpuBenchmark: () => Promise<GpuBenchmarkSignals>;
  // audio_latency (ships disabled — risk-callout #3)
  getAudioLatency: () => Promise<AudioLatencySignals>;
  // application (host app identity — version/build/bundleId)
  getApplicationSignals: () => Promise<ApplicationSignals>;
}

export default TurboModuleRegistry.getEnforcing<Spec>("DeviceIntel");
