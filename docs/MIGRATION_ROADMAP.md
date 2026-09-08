# Device Risk Signals migration checklist

Last reviewed: 2026-09-08, including the media/app-audit and device-posture extraction.

This is the remaining-work checklist for the [ecosystem architecture](ECOSYSTEM_ARCHITECTURE.md).
It describes repository implementation, not a claim that local commits have been pushed, deployed,
or published. Component lifecycle and intended coordinates remain in
[`device-risk-signals.json`](../device-risk-signals.json). Update this checklist in the same change
that completes a migration item; the [extraction report](ANDROID_CORE_EXTRACTION_REPORT.md) records
historical test evidence.

## Current baseline

| Area | Implemented | Still missing |
| --- | --- | --- |
| Shared contract | Generated catalog and event schema in `contract/`, mirrored to Pages | Independent authoring/versioning and cross-SDK conformance fixtures |
| Android | Fourteen typed collections, native example, AAR build and CI checks | Transaction lifecycle, GPU, active-scan decision, device QA and Maven publication |
| iOS | Existing providers under `ios/` used by RN | Standalone SDK, package/consumer integration and release pipeline |
| React Native | Active npm package at the root; extracted Android methods delegate to core | Complete thin adapter, released SDK dependencies and relocation |
| Web | Project naming decision and placeholder directory | SDK implementation, capability catalog, browser tests and npm release |
| Flutter | Project naming decision and placeholder directory | Android/iOS adapter, Dart contract, examples and pub.dev release |
| Capacitor | Project naming decision and placeholder directory | Android/iOS/Web adapters, examples and npm release |
| Pages | Existing RN documentation and migration status notes | Ecosystem navigation, platform sections and URL migration |

Android currently exposes `collectDeviceIdentity`, `collectLocale`, `collectRuntimeTiming`,
`collectNumericConsistency`, `collectAudioLatency`, `collectApplication`, `collectHardware`,
`collectFonts`, `collectOsIntegrity`, `collectNetwork`, `collectTelephony`, `collectGeolocation`,
`collectMediaBluetoothApps`, and `collectDeviceSecurityPosture`.
This is fourteen standalone API calls, not full parity with
the 19-method React Native TurboModule contract. Some native methods are platform stubs or utilities;
do not use these counts as a migration percentage.

## 1. Finish Android extraction

Work in this order unless an implementation dependency justifies a change:

| Remaining work | Current source under `android/src/main/java/com/reactnativedeviceintel/` | Completion condition |
| --- | --- | --- |
| Transaction observations | `SecurityPostureProvider.kt`, `TransactionSafetyObserver.kt`, `TransactionObservationState.kt` | Public native lifecycle API using Android types; RN supplies its activity/lifecycle; callbacks and state cleaned up on detach/dispose |
| GPU benchmark | `GpuBenchmarkProvider.kt` | Core API with identical skip/results behavior, bounded work and GL resource cleanup; preserve disabled default |
| Legacy active Frida scan | `FridaScanProvider.kt` | Resolve the architecture/contract decision below before claiming extraction complete |

For each provider:

- [ ] Add typed model, serialization and failure/omission tests before implementation.
- [ ] Move existing helpers and tests; verify every old emitted field and artifact list survives.
- [ ] Route the RN method through core and the existing value converter; remove duplicate collection.
- [ ] Add an explicit native-consumer example where the platform supports it.
- [ ] Update privacy/catalog/Pages documentation when semantics or host requirements change.
- [ ] Run narrow RED/GREEN checks, then the repository verification ring and native builds.

`DeviceIntelModule`, `DeviceIntelPackage`, and `ReactNativeValueConverter` remain binding code.
Moving all `.kt` files into the SDK is not the goal. Thread dispatch, framework lifecycle, bridge
conversion and RN error translation stay at the binding boundary.

Network, telephony and cached-location extraction is implemented. Physical-device QA remains a
release gate. Track the inherited `locationAgeMs` 32-bit narrowing separately: an old cached fix
can overflow after roughly 24.86 days; fixing its representation needs regression and compatibility
review rather than a silent change during extraction.

Media/Bluetooth/finite app audit and point-in-time device security posture are also extracted.
Host-owned visibility and Bluetooth permissions, finite lists and existing fallbacks are unchanged.
The native posture call does not attach transaction observers or authenticate the user.

### Next slice: transaction lifecycle and GPU execution

The following is a source-review proposal, not a shipped API. Design a native
`TransactionObservationSession` with explicit main-thread `attach(activity)`, `detach()`, immutable
`snapshot()` and idempotent terminal `close()`. Construction must be inactive. The binding owns
`currentActivity`, framework lifecycle and error translation; the core owns Android observation.
Decide whether evidence continues across activity recreation or is reset for a new transaction.
Changing current observation/default/omission behavior requires regression tests and compatibility
notes, even if addressed alongside extraction.

- [ ] Prevent queued UI work from attaching after timeout or disposal. The current observer's
  one-second wait does not cancel its posted block, and disposal has no terminal guard.
- [ ] Separate current screenshot coverage from historical evidence. Detach currently closes the
  registration without resetting the state's active flag; a later registration failure can leave
  stale availability and negative evidence.
- [ ] Omit unsupported partial-obscuration observations below API 29 instead of recording false
  after a touch. Keep absent-before-first-touch and observed-false states distinct.
- [ ] Deactivate detached touch wrappers even when a third-party callback wraps them. Preserve
  forwarding/return values without overwriting another callback or counting a touch twice.
- [ ] Test no activity, repeated attach, activity switch/destroy, close twice, late callbacks,
  registration failure, permissions, API 24/28/29/34/35 gates and collect/dispose races.
- [ ] Define a GPU execution API that owns a worker thread or documents/enforces an equivalent
  safe execution boundary. Current cleanup clears the calling thread's EGL binding instead of
  restoring a pre-existing context/surfaces; native callers must not lose their rendering context.
- [ ] Test EGL setup failures, shader/program failure cleanup, repeated/concurrent calls and
  optional result fields. Some shader failure paths rely on context destruction for cleanup;
  source review alone does not establish a persistent resource leak.
- [ ] Document the GPU loop's 50 ms target as a budget, not a hard deadline: setup and a single
  `glFinish()` can overrun it, and the JS timeout does not cancel native execution. Validate GL,
  camera/video coexistence and cleanup on physical devices before releasing this native API.

### Android release gates

- [ ] Document the final synchronous/lifecycle API, concurrency rules, cancellation/timeout ownership,
  cleanup obligations, supported Android versions and per-probe capabilities.
- [ ] Complete standalone Android lint and native-consumer checks in CI, plus package-content checks
  proving the AAR contains no RN/other-platform implementation or unintended dependency.
- [ ] Validate on representative physical devices: stock/OEM builds, permission-denied cases,
  inaccessible procfs, activity recreation, and expensive-probe latency/cleanup.
- [ ] Keep existing default/omission changes separate from mechanical extraction and document any
  source or event compatibility impact.
- [ ] Configure Maven publication, signing, sources/documentation artifacts and component release
  automation; verify installation from the intended registry in a clean native consumer.
- [ ] Publish platform documentation and a tested binding-to-SDK compatibility range before marking
  Android `active`. A locally built AAR does not satisfy this gate.

## 2. Resolve known contract and architecture gaps

- [ ] **Active localhost scan:** the legacy RN Frida probe connects/writes to localhost and is currently
  enabled by default. This conflicts with the no-network SDK boundary. Record an ADR and a compatible
  migration/release plan to remove it or move it outside the no-network SDK contract. Any alternative
  that changes the product boundary requires an explicit decision; extraction does not authorize it.
  Do not add a core socket API or an `INTERNET` declaration as an implicit migration step.
- [ ] Address the scan's single-read/partial-response behavior, ambiguous false flags and separate
  connect/read timeouts in that decision. A REJECT-like response does not authenticate a service.
  Audit iOS local-port collection against the same boundary before extracting its integrity code.
- [ ] **Unavailable values:** audit legacy false/empty fallbacks separately. Preserve current behavior
  during moves; fixing a fallback requires tests, contract/privacy updates and compatibility notes.
- [ ] **Timing compatibility:** four `NativeRuntimeTimingSignals` measurements became optional during
  extraction. Include consumer guards and the source-compatibility change in the next breaking
  release as recorded in the extraction report; do not silently publish it as a patch.
- [ ] **Package visibility:** retain finite lists and host-owned declarations. Document not-visible
  versus absent limitations; never add `QUERY_ALL_PACKAGES` or a full app/process inventory.

## 3. Make the shared contract independent

- [ ] Define a framework-neutral authoring source for probe ids, fields, types, platform support,
  sensitivity, defaults, permissions, omission rules and collection outcomes. Today authoring still
  lives in `src/probeCatalog.ts` and `src/NativeDeviceIntel.ts`.
- [ ] Define contract versioning and compatibility checks independently from package versions and
  `schema_version`; avoid changing the event envelope merely because a package moves.
- [ ] Add shared conformance fixtures for nested objects, string/number arrays, booleans, zeroes,
  absent fields, and `success`/`skipped`/`timeout`/`error` outcomes.
- [ ] Decide which SDK utilities construct collection outcomes/envelopes and which stay caller-owned.
  The current standalone Android facade returns raw models only; it does not implement RN's runner.
- [ ] Generate or validate native/binding models against the same fixtures, while keeping RN
  TurboModule codegen declarations framework-specific.
- [ ] Preserve canonical catalog/schema URLs and identical Pages mirrors during this transition.

## 4. Extract the iOS SDK

- [ ] Add the `ios-device-risk-signals` package with product `IOSDeviceRiskSignals`, supported
  destinations, exported headers/types and a documented Objective-C/Swift consumption surface.
- [ ] Move existing Foundation-compatible providers from `ios/` in small tested groups; preserve
  absent values, cached location, system framework use and current platform gates.
- [ ] Extract timing/statistics, hardware/application/identity, integrity and other providers;
  resolve local-port behavior before moving socket operations.
- [ ] Define observer ownership and cleanup for screenshot/capture/transaction state; do not rely
  on RN types or global RN lifecycle inside the core.
- [ ] Keep `ios/DeviceIntel.mm` as bridge glue; verify both direct native consumption and RN's
  CocoaPods integration with the same implementation.
- [ ] Package existing privacy resources correctly, audit API usage and avoid introducing
  Required-Reason APIs, persistent identifiers, permission prompts or new runtime dependencies.
- [ ] Add native consumer, tests and builds for supported simulator/device/Catalyst destinations;
  perform physical-device QA and test clean package installation before activation.
- [ ] Establish a tested Swift Package distribution layout and independent release workflow before
  advertising an install URL. The current `sdks/ios/` directory is only a placeholder.

## 5. Complete the React Native binding migration

- [ ] Consume tested Android/iOS artifacts instead of compiling Android core source directories
  directly; verify packaging, autolinking, codegen and CocoaPods on supported RN versions.
- [ ] Move the root package into `bindings/react-native/` while keeping the public npm name
  `react-native-device-risk-signals` and documenting any intentional API incompatibility.
- [ ] Update example imports, scripts, file allow-lists, generated-output paths, CI and release
  automation together. Test a packed package from a clean consumer outside this checkout.
- [ ] Enable workspaces and component-prefixed tags only after the architecture's migration gates
  are satisfied. Existing `vX.Y.Z` tags still refer to the RN npm package.

## 6. Implement Web and additional bindings

- [ ] Web: define browser capabilities and privacy-conscious defaults, implement `web-device-risk-signals`
  without native/RN dependencies, test missing APIs/SSR imports and representative browsers, add a
  direct browser example and npm package verification. Omit unsupported mobile observations.
- [ ] Flutter: implement thin Android/iOS adapters over stable native APIs, Dart types and optional
  values, lifecycle/error mapping and a real example; publish as `flutter_device_risk_signals`.
- [ ] Capacitor: implement Android/iOS adapters plus a Web adapter consuming the Web SDK; verify
  platform selection, cleanup, omissions and a real native/browser example before npm release.
- [ ] Keep collection logic in platform SDKs; adapters must not depend on or copy another adapter.
  Native stabilization comes first; Web research can proceed independently once contract rules exist.

## 7. Finish GitHub Pages and release operations

- [ ] Implement the [platform SEO and discovery plan](PLATFORM_SEO_PLAN.md), using confirmed public
  package names, unique platform copy and accurate availability/installation CTAs. The plan includes
  ready English title/meta/H1/intro drafts and the Flutter pub.dev naming exception.
- [ ] Turn the homepage/navigation into an ecosystem entry point with accurate lifecycle statuses
  derived from `device-risk-signals.json`.
- [ ] Add Android, iOS, Web, RN, Flutter and Capacitor sections as their implementations become usable.
  Planned/unpublished packages must not have install commands that imply available registry releases.
- [ ] Provide per-package API/install/privacy/troubleshooting pages and independent compatibility
  and release information; keep shared probe tables canonical rather than copied by platform.
- [ ] Keep backend examples on the shared event contract; update AI prompts and discovery files to
  select the appropriate SDK/binding rather than assuming React Native.
- [ ] Expand Pages checks for manifest/status drift, links, accessibility, mobile layout and metadata.
- [ ] Before renaming the repository/Pages URL, complete the architecture's URL migration checklist:
  stable domain decision, route inventory, compatibility routes, canonical/schema URLs, metadata,
  sitemap/robots/llms files, controlled external links and deployed-site verification.
- [ ] Introduce independent component releases only with registry smoke tests and publication
  credentials configured outside the repository. Keep failed/partial publication recoverable.

The next implementation slice is **the explicit transaction observer lifecycle**, followed by GPU
execution and cleanup. Keep the active Frida architecture decision separate. Each slice should end
with typed core APIs, thin RN delegation, native-consumer checks, updated documentation and recorded
RED/GREEN evidence. The full monorepo migration remains incomplete until all relevant
platform, binding, contract, documentation and publication gates above are satisfied.
