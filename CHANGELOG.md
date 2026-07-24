# Changelog

All notable public changes will be documented in this file.

## [Unreleased]

## [0.8.0] - 2026-07-23

### Added

- Added Magisk-hide-resistant Android tells: `magiskAbstractSocketFound` (32+ char random abstract
  socket in `/proc/net/unix`) and `magicMountModulesFound` (a `/system` file sharing the `/data`
  device number — a magic-mount module, observable even under DenyList).
- Broadened Android Frida evidence: `fridaThreadNamesFound` (worker-thread names), `fridaInjectorPipeFound`
  (`linjector` fd), `fridaListenerPortFound` (`/proc/net/tcp` LISTEN on 27042/27043), and
  `fridaHandshakeReject` (the D-Bus/Frida `AUTH` handshake on 27042 answers `REJECT`).
- Widened Android emulator coverage (Andy/Nox/VirtualBox/x86 artifact files, BlueStacks shared folder,
  `/proc/tty/drivers` goldfish) and added more Magisk artifact paths.
- Added iOS `parentPidUnexpected` (`getppid()!=1`), `jailbreakBypassDetected` (the "Shadow" tweak),
  `mainExecutableEncrypted` (Mach-O `LC_ENCRYPTION_INFO` cryptid), `openReverseEngineeringPorts`
  (27042/4444/22/44 loopback), and more injected-dylib names.
- Added iOS `lockdownModeEnabled` (Lockdown Mode, iOS 16+) to `device_security_posture`.

### Compatibility and privacy

- All new fields are optional and unavailable reads are omitted; missing stays distinct from `false`.
  New Android fields are Android-only and new iOS fields are iOS-only.
- No dependencies, JNI/NDK, network requests, persistent identifiers, `QUERY_ALL_PACKAGES`, runtime
  permission prompts, or Apple Required-Reason API declarations were added.

See the [v0.8.0 release notes](docs/releases/0.8.0.md) for field-level availability and the complete
verification record.

## [0.7.0] - 2026-07-23

### Added

- Strengthened the `os_integrity` probe with a stack-trace hook probe (`hookStackFrameFound` /
  `hookStackFrames`) that matches Xposed/LSPosed/EdXposed/Substrate bridge frames and a
  doubly-injected Zygote — a third method independent of the loaded-class and `/proc/self/maps`
  scans.
- Broadened Android root evidence: `suOnPath` (`which su`), a `su`-binary scan across the process
  `PATH` plus `/system_ext/bin` and `/cache`, additional known root/injection artifact paths, and
  new installed-package categories `dangerousAppFound` (patchers/ROM managers/IAP spoofers) and
  `rootCloakingAppFound` (root-hiding apps).
- Expanded emulator coverage to recognize the MuMu family and the `Build.HOST` value
  (`host:qemu` build marker, `buildbot_host` device-farm marker).
- Extended the iOS dyld-injection and jailbreak-symlink coverage (SSLKillSwitch2, 0Shadow, FlyJB,
  Cephei, Electra, AppSyncUnified, WeeLoader, libsparkapplist, and more `/var`/`/usr` symlinks).

### Compatibility and privacy

- All new fields are optional and unavailable reads are omitted; missing stays distinct from
  `false`. The new Android-only fields are omitted on iOS.
- The new package categories are queried only through the finite `<queries>` allow-list. No
  dependencies, JNI/NDK, network requests, persistent identifiers, `QUERY_ALL_PACKAGES`, runtime
  permission prompts, or Apple Required-Reason API declarations were added.

### Documentation

- Added copy-paste Android capture-permission guidance to the hosted integration guide, made the
  exact permission names discoverable from FAQ and privacy pages, and documented the example app's
  disabled-by-default two-stage transaction observation flow.

See the [v0.7.0 release notes](docs/releases/0.7.0.md) for field-level availability and the complete
verification record.

## [0.6.0] - 2026-07-22

### Added

- Added Android own-package install provenance: explicit installing and initiating package names,
  initiating-installer signing-certificate digests, Android 13+ package-source class, Android 14+
  update owner, and system/updated-system application flags. `installerPackage` remains as a
  backward-compatible alias.
- Added opt-in Android transaction observations for Android 15 screen-recording visibility, Android
  14 screenshot events, and direct obscured/partially-obscured touch flags with monotonic event times.

### Compatibility and privacy

- All fields are optional and unavailable platform/API/permission reads are omitted. Install-source
  values remain installer-supplied observations rather than Play Integrity verdicts.
- `transaction_safety` remains disabled by default and begins UI observation only after its first
  enabled collection. The Android library manifest still declares no permissions; capture callbacks
  run only when the host application explicitly declares the corresponding install-time permission.
- No dependencies, network requests, persistent identifiers, `QUERY_ALL_PACKAGES`, runtime permission
  prompts, or Apple Required-Reason API declarations were added.

See the [v0.6.0 release notes](docs/releases/0.6.0.md) for field-level availability, integration
guidance, and the complete verification record.

## [0.5.1] - 2026-07-21

### Improved

- Made the documentation site the canonical package and repository homepage, clarified the
  zero-runtime-dependency/no-vendor-backend positioning, and added direct paths from the README to
  integration Q&A, adoption stories, and compatibility reporting.
- Added a transparent physical-device compatibility matrix that separates automated build coverage
  from community and maintainer hardware reports.
- Added dedicated GitHub issue forms for sanitized physical-device reports and raw signal proposals,
  plus scoped contributor guidance and starter issues.
- Added community calls to action to the documentation landing page and removed decorative patterns
  that competed with the product screenshot and primary integration action.
- Extended documentation verification to protect the new Q&A and compatibility-report entry points.

### Fixed

- Restructured Android active-network reads so permission-aware lint recognizes the existing
  `ACCESS_NETWORK_STATE` gate without changing observation semantics or requesting a permission.

### Compatibility and privacy

- No public signal API, event schema, default probe set, or minimum platform version changed.
- No runtime dependencies, permissions, network requests, telemetry, persistent identifiers,
  `QUERY_ALL_PACKAGES`, or Apple Required-Reason API declarations were added.

See the [v0.5.1 release notes](docs/releases/0.5.1.md) for the complete community and documentation
updates.

## [0.5.0] - 2026-07-21

### Added

- Added Android battery-health, voltage, technology, physical-presence, low-battery, power-source,
  cycle-count, and charge-time observations, plus NFC availability and adapter state.
- Added Android active-network transports, DNS servers, Private DNS state and strict hostname, MTU,
  Internet validation, and captive-portal observations when the host already has
  `ACCESS_NETWORK_STATE`.
- Added Android platform build time, iOS embedded-provisioning-profile presence, and raw display
  topology on both platforms.
### Improved

- Corrected iOS mirroring semantics to use `UIScreen.mirroredScreen`; an attached extended display
  is no longer reported as screen mirroring.
- Stopped reporting `usbDebuggingEnabled: false` from the ordinary-app Android fallback. The field
  is now omitted because modern Android does not expose a trustworthy third-party ADB-state read.
- Tightened Android network omission semantics: missing permission and failed platform reads no
  longer look like observed offline or negative states.
- Added pure Kotlin classifiers and policy helpers with focused unit tests, plus contract/catalog,
  platform-gating, dependency, permission, and Apple privacy-manifest regression coverage.

### Compatibility and privacy

- This is a backward-compatible additive release for React Native 0.76+ with the New Architecture.
- All 23 newly collected observations are optional. `getTaskAllowEntitlement` is reserved in the
  optional contract but deliberately omitted until Apple exposes a supported public iOS API.
- No runtime or native binary dependencies, Android permissions, network requests, persistent
  identifiers, `QUERY_ALL_PACKAGES`, or Apple Required-Reason API declarations were added.

See the [v0.5.0 release notes](docs/releases/0.5.0.md) for upgrade guidance and the complete field
overview.

## [0.4.0] - 2026-07-21

### Added

- Added system location-services state on Android and iOS, plus iOS 15+ cached-location source
  observations for software simulation and external accessories.
- Added Android debugger-wait state, raw dangerous system-property matches, and loadable
  Xposed/Substrate/LSPosed class names without initializing third-party classes.
- Added low-power mode and current-process resident memory on both platforms, plus Android low-RAM
  device class and VM heap ceiling.
- Added Android external-storage installation state and iOS App-on-Mac/Mac Catalyst execution
  environment observations.
### Improved

- Expanded raw Android artifact coverage for KernelSU, APatch, resetprop, and Frida while preserving
  concrete paths and properties instead of producing a root verdict.
- Expanded iOS rootless-jailbreak and injection coverage for Dopamine, palera1n, TrollStore,
  ElleKit, Frida, and `DYLD_FRAMEWORK_PATH` artifacts.
- Added pure Kotlin classifiers and resident-memory parsing helpers with focused unit tests, plus
  contract/catalog regression coverage for every new optional field.
- Clarified omission semantics throughout the public contract, Probe Catalog, Data Dictionary, and
  README. Unreadable or unavailable observations are omitted rather than replaced with `false` or
  zero.

### Compatibility and privacy

- This is a backward-compatible additive release for React Native 0.76+ with the New Architecture.
- No runtime dependencies, native binary dependencies, permissions, network requests, persistent
  identifiers, `QUERY_ALL_PACKAGES`, or Apple Required-Reason API declarations were added.
- `mockLocationAppsFound` remains optional for source compatibility but is intentionally not
  populated because a safe complete implementation would require broad installed-app visibility.

See the [v0.4.0 release notes](docs/releases/0.4.0.md) for upgrade guidance and the complete field
overview.

## [0.3.0] - 2026-07-21

### Added

- Added `deriveObservationMetrics()` for explainable ratios, consistency checks, and probe outcome
  counts derived entirely from an already-collected event, without scoring or verdicts.
- Added the calibration-gated, default-off `runtime_timing` probe with JavaScript event-loop,
  bridge round-trip, and native monotonic-clock aggregates on Android and iOS.
- Added the calibration-gated, default-off `numeric_consistency` probe for deterministic integer and
  floating-point observations across JavaScript and native runtimes.
- Added robust GPU timing aggregates: median, p95, median absolute deviation, coefficient of
  variation, and warm-up slope.
- Added Android native unit tests to CI for emulator classification and computation helpers.

### Improved

- Expanded Android emulator evidence for Genymotion, BlueStacks, Nox, MEmu, LDPlayer, Andy,
  Droid4X, and KoPlayer, plus Firebase Test Lab and Android test-harness markers.
- Expanded iOS simulator and device-farm evidence with simulator-environment and XCTest markers.
- Exposed explainable emulator evidence arrays and the list of checks performed instead of reducing
  observations to a single opaque flag.
- Expanded application provenance with Android SDK/signing observations and iOS receipt/build
  metadata.
- Strengthened OS-integrity observations for tracing, test keys, suspicious mounts, Zygisk,
  executable mappings, environment-variable names, and evidence counts.
- Added the default-on `device_security_posture` probe and the calibration-gated,
  default-off `transaction_safety` probe without adding permission prompts.
- Added `deriveConsistencySignals()` for raw country, timezone, bundle, and version equality
  observations without scoring or verdicts.
- Updated the Probe Catalog, Data Dictionary, benchmark guidance, README examples, and GitHub Pages
  documentation for the new fields and probes.

## [0.2.0] - 2026-07-16

- **Breaking:** Removed the built-in transport, `collectAndSend()`, `TransportConfig`, wire envelope,
  and `sendFields`. Applications now collect locally and own all delivery behavior.
- **Breaking:** Raised the supported React Native floor from 0.71 to 0.76 and documented the package
  as New Architecture-only.
- Added a machine-readable `PROBE_CATALOG`, strict configuration validation, a Data Dictionary, and
  reproducible benchmark documentation.
- Added CI compatibility checks for React Native 0.76.9, 0.81.6, and 0.86.0.
- Added native Android/iOS builds, Kotlin and Objective-C/C++ CodeQL analysis, and a verifier that
  checks all 15 TurboModule methods across TypeScript, Kotlin, and Objective-C++.
- Fixed probe timeout timers remaining scheduled after completion and isolated probes that throw
  synchronously before returning a Promise.
- Added end-to-end collection timing to the Signal Bench example.
- Replaced JavaScript `Math.random()` session identifiers with native CSPRNG-backed UUIDs on
  Android and iOS.
- Updated the demo's `concurrent-ruby` dependency to `1.3.7` to resolve Dependabot advisories.

## [0.1.1] - 2026-07-15

- Added npm version and CI status badges to the README.
- Enabled automated npm releases through GitHub Actions Trusted Publishing.

## [0.1.0] - 2026-07-15

- Published the standalone privacy-conscious React Native TurboModule.
- Added Android and iOS signal providers with independent probe outcomes.
- Added the Signal Bench example app, screenshots, and a sanitized real response.
- Added compiled npm entrypoints, package verification, CI, and trusted publishing automation.

[Unreleased]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.8.0...HEAD
[0.8.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.5.1...v0.6.0
[0.5.1]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/AfanasievN/react-native-device-risk-signals/releases/tag/v0.1.1
[0.1.0]: https://github.com/AfanasievN/react-native-device-risk-signals/releases/tag/v0.1.0
