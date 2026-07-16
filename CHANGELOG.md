# Changelog

All notable public changes will be documented in this file.

## [Unreleased]

- Expanded application provenance with Android SDK/signing observations and iOS receipt/build
  metadata.
- Strengthened OS-integrity observations for tracing, test keys, suspicious mounts, Zygisk,
  executable mappings, environment-variable names, and evidence counts.
- Added the default-on `device_security_posture` probe and the calibration-gated,
  default-off `transaction_safety` probe without adding permission prompts.
- Added `deriveConsistencySignals()` for raw country, timezone, bundle, and version equality
  observations without scoring or verdicts.

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

[Unreleased]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/AfanasievN/react-native-device-risk-signals/compare/v0.1.1...v0.2.0
[0.1.1]: https://github.com/AfanasievN/react-native-device-risk-signals/releases/tag/v0.1.1
[0.1.0]: https://github.com/AfanasievN/react-native-device-risk-signals/releases/tag/v0.1.0
