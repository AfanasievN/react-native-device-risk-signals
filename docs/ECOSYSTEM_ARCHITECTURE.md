# Device Risk Signals ecosystem architecture

## Goal

Device Risk Signals is the platform-neutral project. `react-native-device-risk-signals` is its first
production binding, not the long-term owner of native signal logic.

```text
Shared raw-signal contract
├── Android SDK ─┬── Native Android apps
│               ├── React Native
│               ├── Flutter
│               └── Capacitor
├── iOS SDK ─────┬── Native iOS apps
│               ├── React Native
│               ├── Flutter
│               └── Capacitor
└── Web SDK ─────┬── Browser apps
                └── Capacitor
```

SDKs own platform collection and platform-native result models. Bindings only adapt those models to
their framework. No SDK may depend on a binding, and no binding should duplicate platform detection
logic after extraction is complete.

## Repository layout

| Path | State | Responsibility |
| --- | --- | --- |
| `contract/` | Active | Generated, platform-neutral probe catalog and event schema |
| `android/`, `ios/`, `src/` | Active/transitional | Current React Native package implementation |
| `sdks/android/` | In development | Standalone Kotlin/Android library; identity and locale extracted |
| `sdks/ios/` | Planned | Standalone iOS Swift Package with optional Mac Catalyst support |
| `sdks/web/` | Planned | Browser SDK |
| `bindings/react-native/` | Transitional placeholder | Future home of the existing npm binding |
| `bindings/flutter/` | Planned | Dart/Flutter adapter |
| `bindings/capacitor/` | Planned | Capacitor adapter |

`device-risk-signals.json` is the machine-readable component map. `dependsOn` records current
dependencies and `targetDependsOn` records dependencies that become real after extraction. Planned
directories intentionally contain no package manifests so they cannot be published accidentally.

## Library naming

Every distributable uses the public pattern `<platform>-device-risk-signals`:

| Surface | Public library name | Install coordinate |
| --- | --- | --- |
| Android | `android-device-risk-signals` | `io.github.afanasievn:android-device-risk-signals` |
| iOS | `ios-device-risk-signals` | Swift package `ios-device-risk-signals`, product `IOSDeviceRiskSignals` |
| Web | `web-device-risk-signals` | npm `web-device-risk-signals` |
| React Native | `react-native-device-risk-signals` | npm `react-native-device-risk-signals` |
| Flutter | `flutter-device-risk-signals` | pub.dev `flutter_device_risk_signals` |
| Capacitor | `capacitor-device-risk-signals` | npm `capacitor-device-risk-signals` |

Flutter is the only spelling exception at installation time: Dart package names must be valid
`lowercase_with_underscores` identifiers. The product and documentation name still uses hyphens.

## Dependency rules

1. The shared contract is additive and contains raw observations plus per-probe outcomes only.
2. Android, iOS, and Web SDKs do not depend on React Native, Flutter, or Capacitor.
3. Bindings depend on SDK public APIs and contain only conversion, lifecycle, and framework glue.
4. Network transport, authentication, retries, persistence, scoring, and blocking policy remain in
   the host application or backend.
5. Unsupported or unavailable fields are omitted; platform adapters must not invent `false`, zero,
   or empty values.

## Incremental migration

### Phase 0 — repository foundation (current)

- Establish the neutral product identity and component manifest.
- Publish shared contract artifacts from the existing TypeScript sources.
- Keep `react-native-device-risk-signals` at the root and fully compatible.

### Phase 1 — Android core

- Introduce Kotlin result models that contain no React Native types. **Started:** device identity,
  Android build, and locale models are available.
- Move property, artifact, hook, and provider logic into the Android SDK. **Started:** identity and
  locale collectors are extracted.
- Keep a small TurboModule adapter that converts SDK models to React Native maps. **Implemented:**
  the shared value converter is now the boundary for extracted probes.
- Add native Android consumer tests before publishing the Maven artifact.

### Phase 2 — iOS core

- Move Foundation-compatible providers behind a public iOS SDK API.
- Keep Objective-C++ React Native code as a thin adapter.
- Add native Swift/Objective-C consumer tests before publishing the Swift package.

### Phase 3 — React Native relocation

- Point the existing npm package at both native SDK artifacts.
- Move its package sources to `bindings/react-native/` without changing the npm name or public API.
- Only then enable package-manager workspaces and update release automation.

### Phase 4 — Web and additional bindings

- Implement a separate Web capability catalog with privacy-first defaults.
- Build Flutter and Capacitor adapters from stable native SDK APIs.
- Version each distributable independently while versioning the shared contract explicitly.

## Repository and documentation rename

Renaming the GitHub repository and Pages URL is intentionally deferred. Project Pages URLs do not
have the same redirect guarantees as normal repository links. Rename only after the new package
layout, release workflows, documentation links, and preferably a custom documentation domain are
ready to change together.
