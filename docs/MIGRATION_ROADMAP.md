# Device Risk Signals migration checklist

Last reviewed: 2026-09-09, including the active-probe native demo and its host-permission finding.

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
| Android | Sixteen typed collections, explicit transaction sessions, worker-only GPU with an instrumented EGL suite, native example and CI checks | Transaction lifecycle and physical-device GL QA, Maven publication |
| Android active probes | Separate optional component with the loopback Frida scan, JVM socket tests, lint and package-content gates, a native one-button demo, consumed by the binding | Host-permission outcome modeling, probe-default decision, physical-device QA, iOS loopback resolution, Maven publication |
| iOS | Swift package with nine extracted collections tested on simulator/device/Catalyst and consumed by the pod; remaining providers under `ios/` | Foundation and UIKit provider extraction, observer ownership, Catalyst/device destinations, native consumer and release pipeline |
| React Native | Active npm package at the root; extracted Android methods delegate to core | Complete thin adapter, released SDK dependencies and relocation |
| Web | Project naming decision and placeholder directory | SDK implementation, capability catalog, browser tests and npm release |
| Flutter | Project naming decision and placeholder directory | Android/iOS adapter, Dart contract, examples and pub.dev release |
| Capacitor | Project naming decision and placeholder directory | Android/iOS/Web adapters, examples and npm release |
| Pages | Existing RN documentation and migration status notes | Ecosystem navigation, platform sections and URL migration |

Android currently exposes `collectDeviceIdentity`, `collectLocale`, `collectRuntimeTiming`,
`collectNumericConsistency`, `collectAudioLatency`, `collectApplication`, `collectHardware`,
`collectFonts`, `collectOsIntegrity`, `collectNetwork`, `collectTelephony`, `collectGeolocation`,
`collectMediaBluetoothApps`, `collectDeviceSecurityPosture`, `collectTransactionSafety`, and `collectGpuBenchmark`.
`createTransactionObservationSession()` supplies a separate explicit lifecycle API.
The sixteen collection calls are not full parity with
the 19-method React Native TurboModule contract. Some native methods are platform stubs or utilities;
do not use these counts as a migration percentage.

## 1. Finish Android extraction

Work in this order unless an implementation dependency justifies a change:

All providers are now extracted. `FridaScanProvider.kt` was the last one; it moved into the
optional `sdks/android-active-probes/` component rather than the no-network core, as recorded in
[ADR-0003](adr/0003-active-loopback-probe-component.md). What remains for Android is QA, defaults and
publication, not relocation.

When a provider is added in future, follow the same steps:

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

### Transaction lifecycle and GPU implemented; physical-device QA remains

The in-development SDK now implements `TransactionObservationSession` with explicit main-thread
`attach(activity)`, `detach()`, immutable thread-safe `snapshot()` and terminal/idempotent `close()`.
Construction is inactive. The binding owns `currentActivity`, lifecycle and dispatch; the core owns
Android observation. Evidence continues across detach/reattach; a new native session resets history.
This is implemented locally, not a published API. See [ADR-0002](adr/0002-explicit-android-transaction-session.md)
for compatibility changes and the difference between queue cancellation and interrupting running work.

- [x] Cancel queued UI attachment on timeout/interruption; disposal blocks new attachment and queues
  uncancelled close. Running platform calls are not forcibly canceled.
- [x] Separate current screenshot coverage from historical evidence and clear recording visibility
  on detach. Model regression tests preserve positive history while omitting unavailable negatives.
- [x] Omit unsupported partial-obscuration below API 29; preserve false after an observable clean touch.
- [x] Invalidate detached callback generations, including wrappers retained by a third party, and
  keep input forwarding unchanged. Pure lifecycle tests cover stale tokens and terminal close.
- [ ] Add instrumented/physical tests for no activity, repeated attach, activity switch/destroy,
  nested wrappers, registration failure, permissions, API 24/28/29/34/35 gates and collect/dispose
  races. Pure-state/queue regressions and successful compilation do not cover Android framework behavior.
- [x] Add `collectGpuBenchmark()` with UI-thread rejection and documented dedicated-worker ownership.
  Cleanup attempts to restore a changed EGL binding and never terminates the shared display.
- [x] Test shader/program failure ownership, independent cleanup order, unchanged-binding behavior
  and raw model omissions using pure fake-driver/model tests. Do not equate this with driver QA.
- [x] Document the 50 ms draw-loop target as a budget, not a deadline; driver/setup overruns and
  caller timeouts do not cancel native work.
- [x] Add an instrumented `androidTest` suite that runs the real EGL path: prior-binding restoration,
  no binding left when the caller had none, shared-display survival, repeated forced collects,
  UI-thread rejection on a real `Looper`, and sequential/concurrent worker calls. Executed on an
  API 35 arm64 emulator, where the forced path completed on emulated GL and the public facade
  returned the `emulator` skip. Emulated GL is not a driver, and CI has no device: it compiles the
  suite but does not run it.
- [ ] Exercise the failure modes an emulator cannot provoke on representative physical devices: a
  driver refusing restoration, a surfaceless or non-default-display caller, GL/camera/video
  coexistence, Activity teardown mid-benchmark, and FD/GPU-memory growth across runs. Restoration
  stays best-effort; dedicated workers must not own application rendering state.

### Android release gates

- [ ] Document the final synchronous/lifecycle API, concurrency rules, cancellation/timeout ownership,
  cleanup obligations, supported Android versions and per-probe capabilities.
- [x] Complete standalone Android lint and native-consumer checks in CI, plus package-content checks
  proving the AAR contains no RN/other-platform implementation or unintended dependency. Both
  components run `:lintRelease` with `warningsAsErrors`/`checkAllWarnings` plus test sources, and
  `npm run verify:android-aar` rejects a permission or manifest component, React Native or other
  foreign framework classes, cross-component classes and unreviewed AAR payload. Lint exemptions are
  per file and per issue in `sdks/android/lint.xml` with written justification; there is no baseline.
  The demo module under `sdks/android/example/` is still only checked at default lint severity.
- [ ] Validate on representative physical devices: stock/OEM builds, permission-denied cases,
  inaccessible procfs, activity recreation, and expensive-probe latency/cleanup.
- [ ] Keep existing default/omission changes separate from mechanical extraction and document any
  source or event compatibility impact.
- [x] Configure Maven publication for both Android components: `maven-publish`, a release
  publication with a sources jar and full POM metadata, published to a file repository inside each
  component's build output. CI publishes there and builds the binding against the result.
- [ ] Complete the registry half of publication: signing, a javadoc or Dokka artifact, Sonatype
  namespace verification, credentials, the staging/release flow, a real version instead of
  `0.1.0-SNAPSHOT`, and component release
  automation; verify installation from the intended registry in a clean native consumer.
- [ ] Publish platform documentation and a tested binding-to-SDK compatibility range before marking
  Android `active`. A locally built AAR does not satisfy this gate.

## 2. Resolve known contract and architecture gaps

- [x] **Active localhost scan:** resolved by [ADR-0003](adr/0003-active-loopback-probe-component.md).
  The scan moved into the optional `sdks/android-active-probes/` component, whose contract permits
  loopback socket I/O only; the core gained no socket API and no `INTERNET` declaration, and the
  binding delegates to the component. Emitted fields, types and the probe default are unchanged.
- [ ] Decide whether an active probe stays enabled by default. ADR-0003 deliberately flipped no
  default, so an active loopback probe currently ships on in React Native.
- [ ] Model "the host cannot open a socket" as its own outcome. Device evidence: an application that
  has not declared `INTERNET` is outside the `inet` group, so the scan reports `defaultPortOpen` and
  `fridaHandshakeReject` as false even with a listener on 127.0.0.1:27042, indistinguishable from
  nothing listening. The component must keep declaring no permission; the fix belongs in the result
  contract, not the manifest.
- [ ] Fix the scan's single-read/partial-response behavior and its ambiguous false flags, and split
  the shared connect/read timeout. Relocation preserved all three defects deliberately; each fix
  changes emitted meaning and needs tests, contract/privacy updates and breaking-release notes.
  A REJECT-like response does not authenticate a service.
- [ ] Resolve the iOS loopback port check in `ios/JailbreakDetector.m` (`openReverseEngineeringPorts`,
  ports 27042/4444/22/44) under the same component rule before extracting iOS integrity code.
- [ ] **Unavailable values:** audit legacy false/empty fallbacks separately. Preserve current behavior
  during moves; fixing a fallback requires tests, contract/privacy updates and compatibility notes.
- [ ] **Timing compatibility:** four `NativeRuntimeTimingSignals` measurements became optional during
  extraction. Include consumer guards and the source-compatibility change in the next breaking
  release as recorded in the extraction report; do not silently publish it as a patch.
- [ ] **Package visibility:** retain finite lists and host-owned declarations. Document not-visible
  versus absent limitations; never add `QUERY_ALL_PACKAGES` or a full app/process inventory.

## 3. Make the shared contract independent

- [x] Define a framework-neutral authoring source for probe ids, fields, types, platform support,
  sensitivity, defaults, permissions and omission rules. Metadata lives in
  `contract/source/probe-catalog.source.json` and field types in
  `contract/source/signal-types.source.json`, both per
  [ADR-0004](adr/0004-neutral-contract-authoring.md). Generation no longer parses TypeScript.
  Collection outcomes are pinned by fixtures rather than by an authored source.
- [ ] Reverse the direction of truth for types: generate the TypeScript signal declarations from the
  neutral source instead of hand-writing them and guarding against drift with
  `npm run verify:signal-types`. React Native codegen consumes `src/NativeDeviceIntel.ts`, so this
  needs its own slice.
- [ ] Fix three lossy encodings the move exposed: `device_identity.androidBuild` publishes the bare
  TypeScript alias `AndroidBuildInfo` as its type; `runtime_timing` is a flattened intersection whose
  published field order depends on declaration order; `hardware.batteryState`/`batteryHealth` publish
  as `string` with their closed value sets only in a code comment.
- [ ] Define contract versioning and compatibility checks independently from package versions and
  `schema_version`; avoid changing the event envelope merely because a package moves.
- [x] Add shared conformance fixtures for nested objects, string/number arrays, booleans, zeroes,
  absent fields, and `success`/`skipped`/`timeout`/`error` outcomes. `contract/fixtures/` holds six
  positive and eleven negative fixtures, each with a sidecar stating what it pins;
  `npm run verify:fixtures` validates them and the published example payloads against the schema and
  the catalog, and fails if a negative fixture stops being rejected or if the schema grows a keyword
  the validator does not implement. They pin JSON shape only: no SDK executes them yet.
- [ ] Make each implementation run the fixtures, so Kotlin, Swift and the bindings are checked
  against the same payloads rather than only their own unit tests.
- [x] Fix iOS boxing C comparison results as `int` rather than `BOOL`. All fourteen sites now use an
  explicit `(BOOL)` cast or a hoisted `BOOL` local, so every boolean field crosses the bridge as a
  real boolean and iOS payloads validate against the published schema. `expr ? YES : NO` was measured
  and rejected: the conditional operator promotes both branches back to `int`. A clang AST sweep
  confirms no `numberWithInt:` remains for a boolean field, which is the only evidence available for
  the eleven sites under `ios/` that have no test target. The extracted providers gained tests
  asserting CFBoolean identity for every boolean they emit, plus the inverse check that no numeric
  field became a boolean, and `npm run verify:ios-booleans` fails on a reintroduction.
- [ ] Give `ios/` a test target. Eleven of the fourteen fixed sites are covered only by a static AST
  sweep because the React Native binding tree has no XCTest bundle; the UIKit and `AVAudioSession`
  reads there need a host app.
- [ ] Resolve two contract ambiguities the fixtures exposed: `schema_version` is typed `number` in
  `src/DeviceIntel.ts` while the schema pins `const: 1`, and `session_id`/`client_id` carry
  `minLength: 1` in the schema but are plain `string` in TypeScript, so an empty client id
  type-checks and produces a schema-invalid event.
- [ ] Decide which SDK utilities construct collection outcomes/envelopes and which stay caller-owned.
  The current standalone Android facade returns raw models only; it does not implement RN's runner.
- [ ] Generate or validate native/binding models against the same fixtures, while keeping RN
  TurboModule codegen declarations framework-specific.
- [ ] Preserve canonical catalog/schema URLs and identical Pages mirrors during this transition.

## 4. Extract the iOS SDK

- [x] Add the `ios-device-risk-signals` package with product `IOSDeviceRiskSignals`. It carries the
  first extracted group as unmodified Objective-C: `SignalStatistics`, `RuntimeTimingProvider` and
  `NumericConsistencyProvider`, with 26 Swift XCTest cases and byte-identical maths. CocoaPods cannot
  consume a local Swift Package, so `RnDeviceIntel.podspec` compiles the package sources as a second
  source root, the same bridge the Android build uses. The component is now `in-development`.
- [ ] Extend the package beyond pure computation: supported
  destinations, exported headers/types and a documented Objective-C/Swift consumption surface.
- [ ] Move the remaining Foundation-compatible providers from `ios/` in small tested groups.
  Done so far: statistics, runtime timing, numeric consistency, locale, application metadata,
  telephony, audio latency, network. Still in `ios/`: device identity, hardware/fonts, geolocation,
  media, GPU, security posture and integrity - every one of those except GPU hops to the main thread
  inside the provider, so the dispatch-ownership decision below gates them. The package no longer builds for macOS, because CoreTelephony
  is unavailable there; iOS Simulator, device and Mac Catalyst destinations carry the tests. Preserve
  absent values, cached location, system framework use and current platform gates.
- [ ] Extract timing/statistics, hardware/application/identity, integrity and other providers;
  resolve local-port behavior before moving socket operations.
- [x] Give the iOS module its own concurrent `methodQueue` so probes stop serializing on React
  Native's shared serial queue, and guard the battery-monitoring toggle that concurrency would
  otherwise expose. Recorded in [ADR-0005](adr/0005-ios-threading-contract.md).
- [ ] Move the main-thread hops out of the `ios/` providers and into the binding as each is
  extracted, so the iOS SDK asserts its thread requirement the way the Android core does instead of
  dispatching on the caller's behalf. `SecurityPostureProvider` is the forcing case: tightest budget,
  two hops, and an Android counterpart that already went through ADR-0002.
- [ ] Resolve the `UIDevice` contradiction before extracting either file: `DeviceInfoProvider` reads
  `systemName`, `systemVersion` and `userInterfaceIdiom` off the main thread with no hop, while
  `HardwareInfoProvider` documents that `UIDevice` requires the main thread and hops for it. Both
  cannot be right, and the answer is the rule the remaining extractions will cite.
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

The next Android step is **physical-device QA for both Android components**, together with the
active-probe default decision and the final API documentation gate. Instrumented tests now exist
for GPU only; transaction lifecycle still has no instrumented coverage. The shared-contract and iOS work can proceed
independently; Android publication is not implied by collector extraction. Each slice should end
with typed core APIs, thin RN delegation, native-consumer checks, updated documentation and recorded
RED/GREEN evidence. The full monorepo migration remains incomplete until all relevant
platform, binding, contract, documentation and publication gates above are satisfied.
