# Device Risk Signals migration checklist

Last reviewed: 2026-09-18, including the instrumented transaction-lifecycle suite, the active
probe's declared default and the javadoc artifact.

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
| Android | Sixteen typed collections, explicit transaction sessions, worker-only GPU, instrumented EGL and transaction-lifecycle suites, native example, CI checks, AAR with sources and javadoc | Physical-device QA (GL drivers, OEM windows, denied permissions), registry publication |
| Android active probes | Separate optional component with the loopback Frida scan, a declared default, reassembled handshake reads with split timeouts, JVM socket tests, lint and package-content gates, a native one-button demo, consumed by the binding | Host-permission outcome modeling, remaining collapsed false flags, physical-device QA, iOS loopback resolution, registry publication |
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
release gate. The inherited `locationAgeMs` 32-bit narrowing is fixed: the age is a `Long` end to
end, so a fix older than roughly 24.86 days no longer wraps to a negative value. The serialized JSON
type is unchanged, but the Kotlin signature is source-breaking for standalone hosts; iOS never
shared the defect because `NSInteger` is already 64-bit on every supported target.

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
- [x] Add an instrumented suite for the transaction session: no activity, lifecycle calls from a
  background thread on the real `Looper`, repeated attach, detach/reattach, activity switch, activity
  destroyed mid-session, a wrapper installed before attach and another wrapped around ours after it, a
  retained stale-generation callback, the granted-permission registration path, the API 29/34/35 gates
  and a three-reader snapshot race across 40 attach/detach cycles. Fifteen tests, executed on an API 35
  arm64 emulator alongside the ten GPU tests; CI compiles the suite but has no device to run it.
- [x] Run that suite across every gate branch, not only the top one. Twenty-five instrumented tests
  pass on API 24, 28, 29, 34 and 35 arm64 emulators: below 29 the capture fields stay absent and
  partial obscuration is omitted rather than synthesized to `false`; at 29 obscuration is read and
  reported as an observed `false` while both capture gates stay closed; at 34 `DETECT_SCREEN_CAPTURE`
  is really granted and registered while recording visibility stays absent, which is the only
  configuration where the two capture gates disagree. No test needed changing for the low side. The
  test APK requests the two capture permissions on every level; below 34/35 the platform records them
  as requested and never grants them, which is exactly what the `SDK_INT` gate assumes, so no
  `maxSdkVersion` scoping was added - adding one would hide a real regression if the grant ever failed
  on 34/35. API 30-33 were not run; they share the 29..33 branch with 29, so that part is inference.
- [ ] Cover what no emulator can reach: the permission-denied branch of `attach()`, which needs a
  second test APK because permissions are per-APK; a genuine callback registration failure, which no
  emulator provokes - a destroyed Activity does not, so the `safe {}` swallow path stays unproven;
  real obscured touches, which need a second app holding `SYSTEM_ALERT_WINDOW`; and real
  screenshot/recording events actually firing, so `recordScreenshot` and the recording-visibility
  consumer are still only proven to be installed, never to be invoked by the platform.
- [ ] Decide whether `attach()` should refuse a destroyed Activity. On API 35 neither
  `registerScreenCaptureCallback` nor `addScreenRecordingCallback` rejects one, so the session reports
  `screenshotObservationActive = true` for coverage it cannot have. The instrumented suite pins the
  observed platform behavior so a change becomes visible; this is host misuse today, but it is silent.
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

- [x] Document the final synchronous/lifecycle API, concurrency rules, cancellation/timeout ownership,
  cleanup obligations, supported Android versions and per-probe capabilities:
  [`ANDROID_SDK_API.md`](ANDROID_SDK_API.md), written against the code with file:line citations.
- [x] Decide the active probe's default in the component itself. `DeviceRiskActiveProbes` now
  declares `FRIDA_SCAN_PROBE_ID` and `FRIDA_SCAN_ENABLED_BY_DEFAULT = true`, matching the React
  Native catalog, with the AGENTS.md exemption written out: the probe is not new, it shipped enabled
  before extraction and ADR-0003 preserved that default, so switching it off would itself be the
  behavior change. The declaration states plainly that physical-device QA has not happened and that
  no benchmark justifies the cost. A test pins the constant against the catalog source, and the test
  task declares that source as an input so the pin cannot go stale behind an up-to-date check.
- [x] Complete standalone Android lint and native-consumer checks in CI, plus package-content checks
  proving the AAR contains no RN/other-platform implementation or unintended dependency. Both
  components run `:lintRelease` with `warningsAsErrors`/`checkAllWarnings` plus test sources, and
  `npm run verify:android-aar` rejects a permission or manifest component, React Native or other
  foreign framework classes, cross-component classes and unreviewed AAR payload. Lint exemptions are
  per file and per issue in `sdks/android/lint.xml` with written justification; there is no baseline.
  Both demo modules now run `:example:lintRelease` at the same bar in CI, with their own per-file
  `lint.xml`: the findings it surfaced were fixed rather than exempted, except the code-built UI
  strings, since the demos deliberately ship no resource strings.
- [ ] Validate on representative physical devices: stock/OEM builds, permission-denied cases,
  inaccessible procfs, activity recreation, and expensive-probe latency/cleanup.
- [ ] Keep existing default/omission changes separate from mechanical extraction and document any
  source or event compatibility impact.
- [x] Configure Maven publication for both Android components: `maven-publish`, a release
  publication with a sources jar and full POM metadata, published to a file repository inside each
  component's build output. CI publishes there and builds the binding against the result.
- [x] Produce the javadoc artifact Maven Central requires. Both components apply
  `org.jetbrains.dokka-javadoc` and attach a `-javadoc.jar` rendered from the KDoc - real
  documentation, not an empty placeholder - next to the AAR and sources jar in each local
  publication.
- [x] Build everything for the registry half that does not need a key. Each component's version is
  declared once in its `gradle.properties` and overridable for a release build; the `signing` plugin
  signs the publication only when an in-memory key is present, so every task stays green with no
  secrets; the Central Portal repository sits beside the file repository, gated on credentials; and
  `.github/workflows/publish-android.yml` is `workflow_dispatch` only, defaults to a dry run, fails
  before checkout when a secret is missing, runs the full verification ring, asserts the `.asc`
  signatures exist, and creates no git tag. `npm run verify:android-version` fails when the three
  places that name a component version disagree - the two `gradle.properties` and the binding's
  artifact-mode default - because a stale coordinate there resolves nothing and only the artifact
  CI job would notice. Sonatype retired OSSRH on 2025-06-30 and documents no official Gradle plugin,
  so the build publishes through the Central Portal's OSSRH Staging API compatibility endpoint with
  plain `maven-publish` rather than taking on a community plugin. `RELEASING.md` carries the steps.
- [ ] Do the part that needs credentials: register at central.sonatype.com with the `AfanasievN`
  GitHub account so `io.github.afanasievn` is auto-verified, create a Portal user token, generate a
  GPG key and publish its public half, add the four repository secrets, set the version to `0.1.0`,
  run the workflow, and press Publish on the deployment. Then verify installation from Maven Central
  in a clean native consumer outside this checkout.
- [ ] Publish platform documentation and a tested binding-to-SDK compatibility range before marking
  Android `active`. A locally built AAR does not satisfy this gate.

## 2. Resolve known contract and architecture gaps

- [x] **Active localhost scan:** resolved by [ADR-0003](adr/0003-active-loopback-probe-component.md).
  The scan moved into the optional `sdks/android-active-probes/` component, whose contract permits
  loopback socket I/O only; the core gained no socket API and no `INTERNET` declaration, and the
  binding delegates to the component. Emitted fields, types and the probe default are unchanged.
- [x] Decide whether an active probe stays enabled by default. Resolved as enabled, declared
  explicitly in the component as well as the React Native catalog, with the reasoning and the
  missing QA recorded rather than implied.
- [ ] Model "the host cannot open a socket" as its own outcome. Device evidence: an application that
  has not declared `INTERNET` is outside the `inet` group, so the scan reports `defaultPortOpen` and
  `fridaHandshakeReject` as false even with a listener on 127.0.0.1:27042, indistinguishable from
  nothing listening. The component must keep declaring no permission; the fix belongs in the result
  contract, not the manifest.
- [x] Fix the scan's single-read/partial-response behavior and split the shared connect/read
  timeout. The handshake reply is now reassembled across reads until the `REJECT` prefix is decided
  or the read budget is spent, short-circuiting on a disagreeing byte or a mid-prefix EOF; connect
  (700 ms) and read (800 ms) have separate budgets, the read budget covers the whole read rather
  than each call, and the worst case is a documented 1500 ms. `fridaHandshakeReject` can now be true
  where it was false, so values from before and after must not be compared.
- [ ] Fix the scan's remaining ambiguous false flags. Every handshake failure still collapses to
  `false`, indistinguishable from a listener that answered something other than `REJECT`. A
  REJECT-like response still does not authenticate a service.
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
  telephony, audio latency, network, GPU benchmark, device identity. Still in `ios/`: hardware and
  fonts, geolocation, media, security posture and integrity - every one of those hops to the main
  thread inside the provider, so the dispatch-ownership decision below gates them. The package no longer builds for macOS, because CoreTelephony
  is unavailable there; iOS Simulator, device and Mac Catalyst destinations carry the tests. Preserve
  absent values, cached location, system framework use and current platform gates.
- [ ] Extract timing/statistics, hardware/application/identity, integrity and other providers;
  resolve local-port behavior before moving socket operations.
- [x] Give the iOS module its own concurrent `methodQueue` so probes stop serializing on React
  Native's shared serial queue, and guard the battery-monitoring toggle that concurrency would
  otherwise expose. Recorded in [ADR-0005](adr/0005-ios-threading-contract.md).
- [x] Establish the iOS thread-assertion pattern: `RNDIRequireWorkerThread` mirrors the Android
  core's `GpuExecutionPolicy.requireWorker`, takes the thread as a parameter so both directions are
  testable, raises rather than downgrading the result, and never dispatches. The GPU provider calls
  it before any work.
- [ ] Record that Mac Catalyst is not a simulator: `TARGET_OS_SIMULATOR` is 0 under `-macabi`, so the
  GPU provider does not self-skip there and runs the real Metal path against the Mac's GPU. Any
  assumption that a non-device build skips is wrong on Catalyst.
- [ ] Move the main-thread hops out of the remaining `ios/` providers and into the binding as each is
  extracted, so the iOS SDK asserts its thread requirement the way the Android core does instead of
  dispatching on the caller's behalf. `SecurityPostureProvider` is the forcing case: tightest budget,
  two hops, and an Android counterpart that already went through ADR-0002.
- [x] Resolve the `UIDevice` contradiction. The SDK annotations decide it: `UIDevice` and `UIScreen`
  are `NS_SWIFT_UI_ACTOR` with no property exemptions, so `DeviceInfoProvider` gained the hop it was
  missing; `UIFont` is `NS_SWIFT_SENDABLE`, so the font enumeration hop was unnecessary and is gone.
- [ ] Watch `device_identity` under its 200 ms budget now that it hops to the main thread, and
  measure it on a physical device with a busy UI before assuming the budget still holds.
- [ ] Define observer ownership and cleanup for screenshot/capture/transaction state; do not rely
  on RN types or global RN lifecycle inside the core.
- [ ] Keep `ios/DeviceIntel.mm` as bridge glue; verify both direct native consumption and RN's
  CocoaPods integration with the same implementation.
- [ ] Package existing privacy resources correctly, audit API usage and avoid introducing
  Required-Reason APIs, persistent identifiers, permission prompts or new runtime dependencies.
- [x] Add a native iOS consumer: `sdks/ios/example/` is a plain UIKit app that imports the package
  as a local Swift package and calls only its public API, with one button per available collection
  and nothing collected on launch. Verified on a simulator with real values, and built in CI.
- [ ] Give the iOS package a facade comparable to Android's `DeviceRiskSignals`. Today a consumer
  instantiates seven separate providers and fans out by hand, and each returns an untyped
  `NSDictionary` rather than a typed model with `toRawMap()`. The native example makes that gap
  concrete.
- [ ] Add tests and builds for the remaining supported destinations;
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
