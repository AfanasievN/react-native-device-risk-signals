# Device Risk Signals ecosystem architecture

- **Status:** normative
- **Last updated:** 2026-09-09
- **Machine-readable companion:** [`device-risk-signals.json`](../device-risk-signals.json)
- **Accepted decision:** [ADR-0001](adr/0001-platform-sdk-monorepo.md)
- **Transaction lifecycle:** [ADR-0002](adr/0002-explicit-android-transaction-session.md)
- **Active loopback boundary:** [ADR-0003](adr/0003-active-loopback-probe-component.md)
- **Remaining work:** [Migration checklist](MIGRATION_ROADMAP.md)
- **Platform discovery:** [SEO and platform content plan](PLATFORM_SEO_PLAN.md)

This document defines how the repository is organized and operated. If a placeholder README or an
implementation detail disagrees with this document, this document and `device-risk-signals.json`
take precedence. Changes to package boundaries, dependency direction, naming, or release strategy
must update both files in the same pull request.

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

## Architectural principles

1. **One native implementation per platform.** Android collection lives in the Android SDK; iOS
   collection lives in the iOS SDK. Framework bindings do not fork or reimplement those checks.
2. **One semantic contract.** Probe ids, field meaning, omission rules, sensitivity, and collection
   outcomes are shared across every surface that can provide them.
3. **Platform capability is explicit.** SDKs expose only observations supported by their platform.
   Cross-platform bindings preserve omissions and never manufacture parity.
4. **Raw observations only.** No component calculates a risk score, returns a trust verdict, blocks
   a user, uploads an event, or creates a persistent device identifier.
5. **Thin bindings.** Bindings own framework lifecycle, value conversion, code generation, and
   error translation. They do not own detection logic.
6. **Independent consumption.** Native applications can install a native SDK without React Native,
   Flutter, Capacitor, or code belonging to another operating system.

## Repository layout

| Path | State | Responsibility |
| --- | --- | --- |
| `contract/` | Active | Generated, platform-neutral probe catalog and event schema |
| `android/`, `ios/`, `src/` | Active/transitional | Current React Native package implementation |
| `sdks/android/` | In development | Sixteen standalone collections plus explicit transaction sessions: identity, locale, timing, numeric vectors, audio properties, application, hardware, fonts, passive integrity, network, telephony, cached location, media/app audit, device posture, transaction snapshot, worker-only GPU |
| `sdks/android-active-probes/` | In development | Optional active Android component; the only component permitted loopback socket I/O, currently one localhost Frida scan |
| `sdks/android-active-probes/example/` | Development consumer | One-button native app demonstrating the active probe; declares host `INTERNET` itself |
| `sdks/ios/example/` | Development consumer | Native iOS app consuming the Swift package without React Native |
| `sdks/android/example/` | Development consumer | Native Android app consuming the partial SDK without React Native |
| `sdks/ios/` | In development | Standalone iOS Swift Package with optional Mac Catalyst support; currently the shared statistics helper, runtime timing, numeric consistency, locale, application metadata, telephony, audio latency and network |
| `sdks/web/` | Planned | Browser SDK |
| `bindings/react-native/` | Transitional placeholder | Future home of the existing npm binding |
| `bindings/flutter/` | Planned | Dart/Flutter adapter |
| `bindings/capacitor/` | Planned | Capacitor adapter |

`device-risk-signals.json` is the machine-readable component map. `dependsOn` records current
dependencies and `targetDependsOn` records dependencies that become real after extraction. Planned
directories intentionally contain no package manifests so they cannot be published accidentally.

## Source ownership during migration

| Concern | Current source of truth | Target owner |
| --- | --- | --- |
| Probe ids, privacy metadata, field lists | `contract/source/probe-catalog.source.json` | Versioned shared contract tooling |
| TypeScript event/native contract | `src/NativeDeviceIntel.ts` | Shared contract plus binding-specific generated types |
| Generated catalog/schema | `contract/` and `website/` | `contract/` with published documentation mirrors |
| Android identity, locale, timing, numeric vectors, audio, application, hardware, fonts, integrity, network, telephony, cached location, media/app audit, device posture, transaction observations and GPU | `sdks/android/` | `android-device-risk-signals` |
| Android active loopback observation | `sdks/android-active-probes/` | `android-active-probes-device-risk-signals` |
| Remaining Android providers | `android/` | `android-device-risk-signals` |
| iOS runtime timing, numeric vectors and shared statistics | `sdks/ios/` | `ios-device-risk-signals` |
| Remaining iOS providers | `ios/` | `ios-device-risk-signals` |
| React Native orchestration | Root `src/`, `android/`, `ios/` | `bindings/react-native/` |
| Web observations | Not implemented | `web-device-risk-signals` |

During extraction, the root React Native package compiles Android core sources directly. This is a
temporary compatibility bridge, not the final dependency model. An opt-in mode already proves the
target shape: `-PdeviceRiskSignalsUseArtifacts=true` builds the binding against the components'
locally published AARs, which is also what makes their Kotlin `internal` declarations a real
boundary rather than an honor system. It is a verification path only, because an autolinked library
cannot add a repository to a consumer's build. Once the Maven and Swift packages are published and
tested, the binding will consume their released artifacts by default instead.

## Library naming

Every distributable uses the public pattern `<platform>-device-risk-signals`:

| Surface | Public library name | Install coordinate |
| --- | --- | --- |
| Android | `android-device-risk-signals` | `io.github.afanasievn:android-device-risk-signals` |
| Android (active probes) | `android-active-probes-device-risk-signals` | `io.github.afanasievn:android-active-probes-device-risk-signals` |
| iOS | `ios-device-risk-signals` | Swift package `ios-device-risk-signals`, product `IOSDeviceRiskSignals` |
| Web | `web-device-risk-signals` | npm `web-device-risk-signals` |
| React Native | `react-native-device-risk-signals` | npm `react-native-device-risk-signals` |
| Flutter | `flutter-device-risk-signals` | pub.dev `flutter_device_risk_signals` |
| Capacitor | `capacitor-device-risk-signals` | npm `capacitor-device-risk-signals` |

Flutter is the only spelling exception at installation time: Dart package names must be valid
`lowercase_with_underscores` identifiers. The product and documentation name still uses hyphens.

These names are the confirmed distributable identities; the umbrella repository name
`device-risk-signals` does not replace them. Future platforms follow the same naming pattern and
the component/ADR process. Documentation and SEO pages must lead with the matching platform package,
its audience and actual availability; unpublished registry names are intended coordinates, not
proof of reservation or installation availability.

## Dependency rules

1. The shared contract is additive and contains raw observations plus per-probe outcomes only.
2. Android, iOS, and Web SDKs do not depend on React Native, Flutter, or Capacitor.
3. Bindings depend on SDK public APIs and contain only conversion, lifecycle, and framework glue.
4. Network transport, authentication, retries, persistence, scoring, and blocking policy remain in
   the host application or backend.
5. Unsupported or unavailable fields are omitted; platform adapters must not invent `false`, zero,
   or empty values.

The allowed dependency graph is:

```text
contract <- android SDK <- framework bindings
contract <- iOS SDK    <- framework bindings
contract <- web SDK    <- Capacitor
```

Arrows mean “depends on.” Dependencies between bindings are forbidden. Android, iOS, and Web SDKs
are peers and must not import each other.

## Shared contract rules

- A probe keeps the same id and field meaning across packages.
- A platform may omit a field or an entire probe when the underlying data is unavailable.
- Every attempted probe resolves independently to `success`, `skipped`, `timeout`, or `error`.
- Adding an optional field or probe is additive. Renaming/removing a field or changing its meaning is
  breaking even when the serialized JSON type remains the same.
- `schema_version` changes only for an incompatible event-envelope change. It is not the npm, Maven,
  Swift, or pub.dev package version.
- Generated files in `contract/` and `website/` must be identical products of the existing sync
  command; they are never edited manually.

## How a probe change flows through the repository

1. Define the raw observation and privacy purpose in the shared catalog/contract.
2. Implement it once in the owning platform SDK. During migration, code still under `android/` or
   `ios/` must be structured so it can move without importing a framework API.
3. Add a thin adapter in each applicable binding and an explicit unsupported-platform gate where
   necessary.
4. Add native model/collector tests, binding contract tests, omission/error tests, and documentation.
5. Regenerate `contract/` and Pages data, then run the complete verification ring.

Sensitive or expensive probes remain disabled until their privacy impact, benchmark, and physical
device behavior are documented.

## Component lifecycle

`device-risk-signals.json` uses these states:

| State | Meaning |
| --- | --- |
| `planned` | Name and boundary are reserved; no installable implementation is promised |
| `in-development` | Source and tests exist, but the distribution is not published/stable |
| `active` | Published, supported, documented, and included in CI/release automation |

A component can become `active` only when it has a public API, consumer example, platform tests,
privacy documentation, package-content verification, release automation, and a successfully tested
installation from its intended registry. A local AAR or placeholder directory is not an active
release.

## Versioning and releases

Components use independent semantic versions because native SDKs and bindings can evolve at
different rates. Compatibility is declared by each binding through minimum compatible SDK versions;
versions are not forced to match across registries.

Current transition rules:

- `react-native-device-risk-signals` remains the only published component.
- Existing tags `vX.Y.Z` and the current GitHub Release workflow refer only to that npm package.
- Planned/in-development components must not be presented as installable registry packages.

Target tag format after per-component release workflows exist:

```text
android-vX.Y.Z
ios-vX.Y.Z
web-vX.Y.Z
react-native-vX.Y.Z
flutter-vX.Y.Z
capacitor-vX.Y.Z
contract-vX.Y.Z
```

The switch from `vX.Y.Z` to component-prefixed tags is itself a release-process migration and must
land together with updated workflows and documentation. Do not create those tags before then.

## CI and repository operation

Every pull request runs the shared JavaScript/TypeScript contract checks, package verification,
Pages verification, compatibility matrix, and native example builds. Both Android components now run
their own tests, lint and release build in CI, and `npm run verify:android-aar` checks the built AARs
for foreign framework code, permissions, manifest components and cross-component classes. A binding is also tested as a real consumer of
its SDKs rather than only with mocked values.

Required checks grow with the repository:

| Component | Minimum verification |
| --- | --- |
| Shared contract | generation drift, schema/catalog validity, compatibility tests |
| Android SDK | JVM unit tests, instrumented GPU suite compilation, Android lint with warnings as errors, release AAR, package-content gate, native consumer build |
| Android active probes | JVM socket tests against a local server, Android lint with warnings as errors, release AAR, package-content gate proving loopback-only content and no permission |
| iOS SDK | XCTest on an iOS Simulator destination, builds for device and Mac Catalyst, native consumer build |
| Web SDK | unit tests, typecheck, browser compatibility and package-content checks |
| Bindings | framework tests, native integration builds, package-content checks |

The root is intentionally not an npm workspace yet. Workspaces and package-aware release tooling are
enabled only when moving the active React Native package no longer breaks npm installation,
autolinking, CocoaPods, codegen, Pages, or the existing release workflow.

## GitHub Pages and documentation model

GitHub Pages must evolve from documentation for one React Native package into the documentation
portal for the whole Device Risk Signals ecosystem. The website is part of the public contract, not
an optional marketing mirror.

The target information architecture is:

```text
/
├── getting-started/
├── signals/
├── contract/
├── android/
├── ios/
├── web/
├── react-native/
├── flutter/
├── capacitor/
├── backend/
├── guides/
├── compatibility/
└── releases/
```

Documentation rules:

1. The home page presents `Device Risk Signals` as the ecosystem and identifies every component as
   `active`, `in development`, or `planned` from `device-risk-signals.json`.
2. Install commands are shown only for published components. Planned package coordinates must be
   labeled as reserved/intended and must never look installable.
3. Every active component gets its own installation, API, compatibility, privacy, troubleshooting,
   migration, and release-notes section.
4. The Probe Catalog and raw-event schema remain shared canonical resources. Platform/package pages
   filter or link to that contract rather than maintaining copied field tables.
5. Existing backend guides continue to consume the shared event envelope and must not become tied to
   one frontend framework.
6. Package/version selectors must not imply that independently versioned packages share a version.
   Each page identifies the component and version it documents.
7. Historical release notes remain addressable. Breaking URL changes require redirects where the
   hosting platform supports them and a checked migration map where it does not.

Before renaming the GitHub repository or changing the Pages base URL:

- Prefer establishing a custom documentation domain so package READMEs and search results use a
  stable canonical URL.
- Inventory every internal link, npm/Maven/pub/Swift metadata URL, badge, sitemap entry, schema `$id`,
  README, social preview, and external backlink controlled by the project.
- Publish redirects or compatibility landing pages for the old routes where possible.
- Update canonical tags, Open Graph metadata, structured data, `robots.txt`, sitemap, `llms.txt`, and
  other AI-agent discovery files together.
- Keep the raw schema/catalog URLs stable or release a documented schema identifier migration.
- Verify the deployed site, not only local files, before removing old URLs.

The Pages workflow must eventually derive component navigation, names, status, and registry links
from `device-risk-signals.json`; verify that all active probes/fields are documented; reject stale
package coordinates; and run link, SEO, structured-data, accessibility, and mobile-layout checks.
Until that generator exists, changes to component names, lifecycle, install coordinates, contract
fields, permissions, or compatibility must update GitHub Pages manually in the same pull request.

## Change governance

- A new probe follows the probe proposal and privacy review process.
- A new component, reverse dependency, package rename, or shared-contract breaking change requires
  an ADR and an update to this document plus `device-risk-signals.json`.
- Pull requests must state affected components, contract impact, privacy impact, compatibility
  impact, and verification performed.
- Generated output, local Gradle caches, AARs, credentials, customer data, and app inventories are
  never committed.

## Incremental migration

### Phase 0 — repository foundation (completed)

- Establish the neutral product identity and component manifest.
- Publish shared contract artifacts from the existing TypeScript sources.
- Keep `react-native-device-risk-signals` at the root and fully compatible.

### Phase 1 — Android core

- Introduce Kotlin result models that contain no React Native types. **Started:** device identity,
  Android build, locale, native runtime timing, numeric vector, audio property, application, hardware,
  font, OS integrity, network, telephony, cached geolocation, media/app audit and device posture models
  are available, along with transaction snapshot/session and GPU models.
- Move property, artifact, hook, and provider logic into the Android SDK. **Started:** identity and
  locale, native runtime timing, numeric vector, audio property, application, hardware, font, and passive OS integrity
  collectors are extracted, along with network, telephony, cached geolocation, media/Bluetooth/finite
  app audit and point-in-time device security posture. Transaction snapshots and explicit sessions
  now live in the core; RN retains lifecycle/UI dispatch and map conversion. GPU collection also
  lives in the core with UI-thread rejection and best-effort restoration of a changed EGL binding;
  hosts own worker dispatch and calibration. Network reads
  system state without sending requests. Telephony/location preserve host permission checks and
  cached-only location reads; the SDK and native example add no permission declarations or prompts.
  JavaScript timing, JS-to-native call duration, and JavaScript/native numeric comparisons remain in
  the React Native binding; other Android providers still need extraction. Audio latency is a
  property-derived buffer estimate, not a measured loopback observation.
  Application collection remains limited to the host package and preserves signing and installer
  fields, including the `installerPackage` alias. Hardware retains existing Android storage reads;
  no new permissions or observations are introduced. Font collection remains explicit, expensive,
  and high entropy in the standalone facade; the existing React Native probe default is unchanged.
  Passive integrity includes existing process-local Frida evidence and shared finite package lists.
  Package observations depend on host visibility; `false` does not prove absence. The core manifest
  adds no queries and the React Native manifest retains its existing queries.
  The legacy localhost TCP scan is no longer an unresolved exception: [ADR-0003](adr/0003-active-loopback-probe-component.md)
  moves it into the optional `sdks/android-active-probes/` component, whose contract permits loopback
  socket I/O only, and the React Native module delegates to it. The passive core and its example
  expose no socket I/O. No component declares `INTERNET`, but the active probe cannot open its socket
  unless the host application already declares it, and without that declaration every flag reads
  false; the component's own example declares it and says why. The iOS loopback port check in `ios/JailbreakDetector.m` is the same conflict
  and stays open until iOS extraction begins.
- Keep a small TurboModule adapter that converts SDK models to React Native maps. **Implemented:**
  the shared value converter is now the boundary for extracted probes.
- Add native Android consumer tests before publishing the Maven artifact. **Started:**
  `sdks/android/example/` depends directly on the SDK Gradle project and exposes sixteen explicit
  collection buttons plus start/read/stop transaction session controls without React Native.
  The GPU button uses a dedicated worker; its 50 ms loop target is not a hard deadline.
  An instrumented `androidTest` suite covers the real GPU EGL path — binding restoration, shared-display
  survival and UI-thread rejection — and CI compiles it, but only a device or emulator can run it.
  Its debug APK can be built alongside the release AAR;
  physical-device validation and the remaining publication gates still apply.
- Add an Android section to GitHub Pages before Maven publication, clearly labeled `in development`
  until the artifact is available from the documented coordinate.

### Phase 2 — iOS core

- Move Foundation-compatible providers behind a public iOS SDK API.
- Keep Objective-C++ React Native code as a thin adapter.
- Add native Swift/Objective-C consumer tests before publishing the Swift package.
- Add an iOS section to GitHub Pages before Swift Package publication, including supported
  destinations, privacy behavior, and native integration examples.

### Phase 3 — React Native relocation

- Point the existing npm package at both native SDK artifacts.
- Move its package sources to `bindings/react-native/` without changing the npm name or public API.
- Only then enable package-manager workspaces and update release automation.
- Move framework-specific Pages content under `/react-native/` while preserving the current public
  installation and guide URLs through redirects or compatibility pages.

### Phase 4 — Web and additional bindings

- Implement a separate Web capability catalog with privacy-first defaults.
- Build Flutter and Capacitor adapters from stable native SDK APIs.
- Version each distributable independently while versioning the shared contract explicitly.
- Add Web, Flutter, and Capacitor Pages sections as their implementations become testable; planned
  pages must describe roadmap/status without publishing fictional install commands.

## Repository and documentation rename

Renaming the GitHub repository and Pages URL is intentionally deferred. Project Pages URLs do not
have the same redirect guarantees as normal repository links. Rename only after the new package
layout, release workflows, documentation links, and preferably a custom documentation domain are
ready to change together. The GitHub Pages migration checklist above is a release blocker for that
rename, not a follow-up cleanup task.
