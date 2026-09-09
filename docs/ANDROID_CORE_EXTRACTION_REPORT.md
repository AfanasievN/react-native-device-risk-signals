# Android core extraction progress

Date: 2026-09-08

This increment moves native runtime-clock sampling and shared distribution statistics into
`sdks/android/`. The standalone API now exposes identity, locale, and runtime timing. The React
Native module delegates native timing to `DeviceRiskSignals.collectRuntimeTiming()` and converts
its raw map at the existing bridge boundary. GPU benchmarking reuses the moved statistics helper;
its platform collector has not yet been extracted.

## Contract and compatibility

Probe IDs and field names are unchanged. No dependencies, permissions, network operations, or
automatic collection were added. Runtime timing remains disabled by default in React Native.

When Android observes no positive clock intervals, the sample count is zero and the four native
distribution measurements are omitted. Clock-read exceptions propagate to the binding's existing
per-probe error handling. Normal positive samples retain the previous calculations.

The four measurements in `NativeRuntimeTimingSignals` are now optional. TypeScript consumers that
previously used them without checking availability must add a guard. This is a source-compatibility
change and must be called out in the next breaking release; it must not be silently shipped as a
patch. No release or version change is performed in this increment. iOS collection is unchanged.

## RED / GREEN evidence

- Kotlin RED: standalone unit-test compilation failed on the missing `RuntimeTimingCollector`.
- Kotlin GREEN: eight standalone tests pass, including positive intervals, unavailable measurements,
  clock failure propagation, model serialization, and distribution statistics. Release AAR builds.
- TypeScript RED: the omission fixture failed typechecking because four fields were required.
- TypeScript GREEN: typecheck and the four computation-probe tests pass; the JS probe preserves
  omitted native measurements.
- Refactor: the original statistics implementation and its tests now live in the core, with no
  second implementation in the binding.

## Verification

- Standalone Android unit tests and release AAR build.
- React Native Android Kotlin compilation and JVM unit tests.
- Root verification: Jest, Node tests, TypeScript, native contract parity, ecosystem boundaries,
  generated contract, package verification, and GitHub Pages.
- npm package dry run and example tests, lint, and TypeScript.

The Android unit-test run initially lacked an existing transitive test artifact in the offline
cache; the online retry passed. npm packaging used a temporary cache because the default cache was
not writable in the sandbox. No dependency declarations changed.

## Remaining migration

Android application, hardware, integrity, network, location, and other collectors still need
extraction and standalone consumer verification. iOS and Web SDK implementations and Flutter and
Capacitor adapters remain future phases. The Android artifact is still unpublished. The shared
catalog and schema are synchronized, but their authoring source remains the transitional TypeScript
contract rather than an independent contract package.

## Second increment: numeric, audio, and a native consumer

The standalone facade now exposes five collections: identity, locale, runtime timing, numeric
consistency, and audio latency. Numeric and audio collectors return typed Kotlin models and raw
maps; the React Native adapter delegates both methods to the SDK. Their previous implementations
have been removed from the binding. Field names, values, permissions, and defaults are preserved.
Native numeric vectors do not include the binding's JavaScript comparisons. Audio output latency
remains an estimate from system properties, with the existing `measured` flag semantics; no audio
engine, microphone, playback, or loopback is started.

`sdks/android/example/` is a separate Android application module depending on the SDK project.
It uses only the public facade and Android system classes, with explicit collection buttons and
local display. CI now builds its debug APK together with the standalone release AAR and unit tests.
This checks the separate-module API boundary without React Native; it does not verify installation
from Maven Central or replace physical-device QA.

Additional RED / GREEN evidence:

- Numeric collector/model tests failed on missing symbols, then passed. A separate raw-map test
  failed on missing `toRawMap`, then passed with exact field names, ordered numeric arrays, large
  unsigned integer values, and `false` observations preserved. Three tests cover this extraction.
- Audio tests failed on missing collector symbols, then passed for valid, absent, malformed,
  partial, and nonpositive property values. Four tests cover the preserved behavior.
- Native consumer compilation failed on the two missing public facade methods before integration.
  The same compilation target passed after integration, followed by a successful debug APK build.
- Concurrent Gradle runs initially conflicted in Kotlin build caches; these infrastructure failures
  were excluded from RED evidence and subsequent native builds were serialized.

Final second-increment checks passed: all 15 standalone JVM tests, release AAR, native consumer APK,
React Native Android compilation and JVM tests, root verification (107 Jest tests, three Node tests,
19-method native parity, all 24 Pages), npm pack dry run, and example tests/lint/TypeScript. No
physical-device session or registry publication was performed.

The original runtime-timing optional-field compatibility note above still applies to the next
release. The second increment adds no further TypeScript contract changes. Other Android providers
and all iOS/Web implementation work remain outstanding.

## Third increment: application, hardware, and fonts

`collectApplication()`, `collectHardware()`, and `collectFonts()` extend the standalone facade to
eight explicit collections. The React Native module now delegates these three methods to typed SDK
models. The native example includes separate buttons for them. Installer, battery, and resident
memory helpers and their existing JVM tests have also moved into the core.

Source inventory comparison found all 27 existing Android application fields and all 35 hardware
fields in the new raw models, with no added or missing keys. Tests cover every builder-to-model-to-map
assignment, including boolean values, arrays, zeroes, and omission. Own-package scope, installer alias,
certificate hashing, platform gates, numeric representation, exception scopes, and font digest
algorithm are preserved. Fonts remain separate from hardware; this extraction does not change the
React Native probe's existing default.

RED evidence: the standalone tests failed on missing application/hardware/fonts models and installer
mapper; the native example failed on the three missing facade methods; the existing JS provenance
contract test failed until its new core model existed. Those same targets passed after extraction.
Post-GREEN cleanup simplified application list construction and made collector comments independent
of React Native. Core tests now total 31. This increment does not add TypeScript fields, permissions,
runtime dependencies, or collection behavior. Physical-device QA and registry publication remain
outstanding.

Third-increment verification passed: 31 standalone JVM tests, release AAR, native example APK,
React Native Android compilation/JVM tests, the root verification ring (107 Jest tests, three Node
tests, native contract parity, package/ecosystem checks, and 24 Pages), npm pack dry run, and example
tests/lint/TypeScript. Existing deprecated Android display API warnings remain; the extraction does
not replace these APIs or change their returned observations.

## Fourth increment: passive OS integrity

`collectOsIntegrity()` is the ninth standalone collection. Its 58 fields preserve the original
Android provider's raw observations, expressions, field types, conditional omissions, artifact
lists, and helper logic. Integrity, emulator, and PATH helpers and their tests now live in the core.
`KnownAppLists` also moved to the core and is shared with the remaining React Native app-audit and
transaction providers. The existing RN manifest queries are unchanged; standalone consumers own
their finite visibility declarations. A package-presence flag of false can mean not visible as
well as absent.

RED: relocated JVM tests failed on missing core model/helpers; the two JS native-source/manifest
drift tests failed on the missing relocated files. GREEN: the same targets pass, including 58-field
serialization, one-field-at-a-time builder mapping, false/zero/empty values, omission, and the
existing evidence-classifier tests. The standalone suite now has 54 tests. Native example
compilation, release AAR, and debug APK builds passed. Post-GREEN cleanup clarified comments about
legacy fallback behavior; no collection semantics were changed.

The active Frida TCP collector remains in the React Native adapter. Review found a conflict between
its existing localhost connect/AUTH exchange and the repository's no-network boundary. This
increment does not copy that socket behavior into the standalone SDK or expose it in the native
example. Passive Frida observations from existing mapped-library, thread, pipe, and procfs reads
are included in `collectOsIntegrity()`.

The legacy active scan still has its existing defaults and limitations: REJECT-like bytes are
protocol evidence rather than service identity; one socket read can receive only part of a response;
connection/handshake errors collapse into false flags; separate connect/read timeouts are not a
single collection deadline. Documentation now describes these limitations accurately. Resolving
the active scanner's architecture, result contract, and defaults is separate follow-up work.
No active Frida scan was executed during verification. Native collectors were compiled, while
the JVM tests exercised pure models/helpers rather than collecting device observations.

Final checks passed: standalone tests/AAR/native APK, RN Android compilation and JVM tests, root
verification (107 Jest tests, three Node tests, 19-method native parity, package/ecosystem and all
24 Pages), npm pack dry run, and example tests/lint/TypeScript. No publication or physical-device
QA was performed.

## Fifth increment: network, telephony, and cached location

`collectNetwork()`, `collectTelephony()`, and `collectGeolocation()` bring the standalone facade
to twelve explicit collections. The RN module delegates through the existing value converter;
the native example now offers all twelve calls. Existing network observation policy and its tests
move to the core, with no React Native imports in the new collectors or models.

Field inventory comparison against the previous providers found identical raw key sets: 22 network,
10 telephony, and 11 geolocation fields. Platform gates, numeric representation, permission checks,
cached-provider selection and exception scopes are preserved. Network reads observe local platform
state without making requests. Telephony does not collect IMEI. Location never requests a fresh fix;
without a cached fix it does not emit coordinates or the mock-provider flag.

Host permission and privacy requirements were documented before extraction. No permissions, package
queries, runtime dependencies or native frameworks were added. Protected reads use already granted
host permissions; no permission prompts occur. Inherited limitations remain explicit follow-ups:
some geolocation helpers collapse failures into false, and `locationAgeMs` retains its 32-bit
narrowing (overflow is possible after roughly 24.86 days). Fixing those semantics requires separate
regression and compatibility work, not an undocumented change during a provider move.

RED: new model/policy tests failed on missing core types; the native example failed on missing facade
methods. GREEN: those same targets pass after extraction. Tests cover omitted values, false/zero,
empty arrays, complete raw maps, every network builder assignment and existing connectivity policy.
Post-GREEN cleanup made collector comments platform-neutral and corrected permission descriptions;
the narrow standalone tests and native builds passed again. There are now 64 standalone JVM tests.
These tests validate pure models/helpers, not physical-device permission or provider behavior.

Final verification passed: standalone JVM tests, release AAR, native example debug APK, RN Android
compilation/JVM tests, root verification (107 Jest tests, three Node tests, 19-method native parity,
package/ecosystem checks and 24 Pages), npm pack dry run, and example tests/lint/TypeScript.
Existing AGP compile-SDK and deprecated Android/Gradle API warnings remain. Physical-device QA,
registry publication and deployment remain outstanding.

The migration checklist now identifies media/Bluetooth/finite app audit and device security posture
as the next extraction slice. Confirmed public package names and an editorial platform SEO plan
are documented separately; drafts do not imply live platform pages or available registry releases.

## Sixth increment: media/app audit and point-in-time device posture

`collectMediaBluetoothApps()` and `collectDeviceSecurityPosture()` extend the standalone facade
to fourteen calls. Two implementation subagents worked on independent providers while the parent
integrated the facade, RN conversion and native example. A third subagent performed a read-only
audit of the remaining transaction/GPU boundaries. No additional component was activated or renamed.

The media collector preserves seven raw fields: audio route/music state, bonded Bluetooth count,
two display counts, finite known-package matches and enabled accessibility service component names.
It adds no Bluetooth discovery, device names/addresses, package queries or permissions. Existing
list contents/order, route priority, permission gates and read-failure behavior remain unchanged.
In particular, empty accessibility/app lists and false music observations retain legacy ambiguity;
they are not proof of absence or a risk verdict.

The posture collector preserves all twelve existing raw keys, platform gates, read order and
failure scopes. It does not test biometric enrollment, authenticate the user, create a key or attach
transaction observers. The transaction methods, observation state and lifecycle are unchanged in
the RN provider; only its posture method, now-unused helper and imports were removed.

RED: the native example failed compilation on both missing facade methods. Standalone tests then
failed compilation on the missing media/posture models before production implementation began.
GREEN: the same example and test targets pass after extraction. Six new model tests cover omissions,
false, numeric zeroes where applicable, empty lists and all field mappings. Source inventory
comparison found identical 7-field media and 12-field posture key sets. Post-GREEN cleanup clarified
the media collector's host-permission/count-only comments; the same targets and native builds
passed again. The standalone suite now has 70 JVM tests.

No percentage coverage claim is made: this slice tests pure models and compiles platform reads,
but does not add an instrumented Android test harness or execute permission/lifecycle/device APIs
on physical devices. Representative device QA remains a release gate. The implementation does not
add runtime dependencies, permissions, frameworks, prompts, queries or change RN probe defaults.

Final checks passed: standalone JVM suite, release AAR and native example APK; RN Android compile
and JVM tests; root verification (107 Jest tests, three Node tests, 19-method native parity,
package/ecosystem validation and 24 Pages); npm pack dry run; example tests, lint and TypeScript.
Existing AGP compile-SDK and Gradle deprecation warnings remain. No registry publication or Pages
deployment was performed.

The roadmap now records the proposed explicit transaction-session boundary and concrete source-review
risks: queued attachment after timeout/disposal, stale capture availability, unsupported partial-touch
false values, wrapped callbacks, EGL restoration and GPU budget/cleanup limits. Those findings are
not fixes delivered by this extraction; they require dedicated regression and compatibility work.

## Seventh increment: explicit transaction sessions

The SDK now has fifteen synchronous collections plus `createTransactionObservationSession()`.
`collectTransactionSafety()` preserves the eight point-in-time Android fields without installing
observers. Public `TransactionObservationSession` owns Android callbacks and a typed snapshot with
the eleven existing session wire keys. RN retains only lifecycle/UI dispatch and map composition.
Three subagents independently implemented point-in-time models, state/serialization and the session;
the parent integrated the bridge, queue cancellation, example, contract notes and documentation.

Sessions are inactive on construction. Attach/detach/close require the main thread; snapshots are
thread-safe and null before first attachment. Close is terminal and idempotent, while detached/closed
history remains readable. The native example supplies Start/Read/Stop and stop/destroy cleanup.
RN attaches lazily only after host resume, detaches on pause/destroy and closes on module invalidation.
Reattachment is driven by a subsequent collection, not an automatic background schedule.

This increment includes intentional corrections, not just moves: API <29 partial obscuration is
omitted; detach clears stale screenshot-negative coverage and recording visibility; callback
generations suppress recording from old wrappers; queued UI work is canceled after timeout or
interruption. Historical positive touch/screenshot observations are retained across gaps. A wait
timeout does not forcibly interrupt already executing Android work. No permission declarations,
network operations, persistent IDs, capture content or runtime dependencies were added.
[ADR-0002](adr/0002-explicit-android-transaction-session.md) records behavior changes and release gates.

RED evidence: standalone tests failed on missing state, snapshot, lifecycle and point-in-time model
types; the native example failed on the missing facade/session API; RN tests failed on the missing
pending-task helper; the JS native-source contract test failed on the missing relocated snapshot.
Those same targets are GREEN. Pure tests cover exact serialization/aliasing, omission, false/zero,
unsupported partial flags, stale coverage, retained history, immutable snapshots, callback generations,
terminal close, canceled/time-out queue execution, duplicate execution and failed task completion.
Post-GREEN cleanup made the bridge wait for the initial host-resume notification before attachment;
the RN compile/test target passed again. Independent review found no additional concrete blocker.

Final checks passed: 86 standalone JVM tests, release AAR, native example debug APK, four RN JVM tests
and RN Android compilation, full root verification (107 Jest tests, three Node tests, 19-method native
parity, package/ecosystem and 24 Pages), npm pack dry run, and example tests/lint/TypeScript.
Existing AGP compile-SDK/deprecation warnings remain. Percentage coverage is not asserted: lifecycle
helpers are tested, but Android registration/Window integration still requires instrumented and
physical-device QA, including nested wrappers, permission changes and activity recreation.

No version bump, registry publication or deployment was performed. GPU extraction, active Frida
boundary resolution and native release gates remain outstanding. Transaction behavior corrections
must appear in the next breaking migration release notes, not an undocumented patch.

## Eighth increment: worker-only GPU execution and resource cleanup

`collectGpuBenchmark()` brings the standalone facade to sixteen synchronous collections. The React
Native module deletes `GpuBenchmarkProvider.kt` and delegates `getGpuBenchmark()` to the core
through the shared value converter. `GpuBenchmarkSignals` keeps all fourteen existing Android raw keys — the catalog's fifteenth GPU field, `gpuTimeMs`, is iOS-only and was never emitted by the Kotlin provider — plus the
emulator/unsupported/error skip reasons, partial GPU identity on late failure, the 32x32 pbuffer,
GLES 2.0 configuration and the 50 ms draw-loop budget. No probe default, permission, dependency or
network behavior changed; the probe stays disabled pending device-lab calibration.

Execution ownership is now explicit. `GpuExecutionPolicy.requireWorker()` rejects UI-thread calls
with `IllegalStateException` before any GPU work starts, and the SDK neither creates a worker nor
schedules collection. React Native already dispatches native probes to background workers; the
native example uses one single-thread executor, disables its GPU button while running and shuts the
worker down in `onDestroy()`. Shutdown does not cancel a driver call that has already started.

Cleanup is separated into testable ownership boundaries. `GpuProgramBuilder` transfers only a
successfully linked program and deletes every other object it created, including a compiled vertex
shader after a fragment failure and a program that failed to link. `GpuResourceCleanup` deletes the
GL program while its context is current, restores the calling thread's previous EGL binding only
when the collector actually changed it, then releases the owned surface and context. Each step is
independent, so one driver failure cannot block the rest, and the process-shared display is never
terminated. Restoration is best-effort: a driver can refuse it, in which case the collector at least
clears its own binding, so hosts must use a worker that owns no application rendering state.

RED: the native example failed compilation on the missing facade method, and the standalone suite
failed compilation on the missing GPU model, policy and cleanup types. GREEN: both targets pass.
Thirteen new pure tests cover raw-map omissions and full field mapping, UI-thread rejection, program
ownership transfer, shader/link failure cleanup, unowned-object protection, cleanup ordering,
unchanged-binding behavior and survival of failures in each cleanup step. These use a fake driver
and carry no claim about real GL behavior.

The 50 ms target is documented as a draw-loop budget, not an end-to-end deadline: setup, a single
`glFinish()` and driver work can overrun it, and a caller or JS timeout does not cancel native
execution. Repeated or concurrent runs can distort measurements, so hosts must serialize benchmarks
and calibrate against their own rendering workload.

Checks passed: 99 standalone JVM tests, release AAR, native example debug APK, React Native Android
compilation and JVM tests, full root verification (Jest and Node suites, 19-method native parity,
package/ecosystem validation and 24 Pages), `npm pack --dry-run`, and example tests, lint and
TypeScript. Existing AGP compile-SDK and Gradle deprecation warnings remain.

No percentage coverage is claimed. Real EGL setup and restoration failures, repeated/concurrent
calls, GL/camera/video coexistence and Activity teardown still require instrumented and
physical-device QA, which remains a release gate. The legacy active Frida boundary, standalone
lint/package-content gates and Maven publication remain outstanding; no version bump, registry
publication or Pages deployment was performed.

## Ninth increment: instrumented GPU execution coverage

This increment adds no collection method. It closes the emulator-reachable part of the GPU QA gate
with an instrumented `androidTest` suite and hardens the execution contract that only source review
had covered before.

Two implementation subagents authored the instrumented tests against a documented seam while a third
performed a read-only parity/documentation audit; the parent integrated the Gradle wiring, the
production seam and the documentation.

Production changes are behavior-preserving for the public facade. `GpuEmulatorHeuristic` is now a
separate internal object with a pure `(fingerprint, model, hardware)` overload, so the build-string
decision is unit-testable and the real EGL path can be forced in tests. `GpuBenchmarkCollector` takes
internal `emulatorObserved` and `mainThread` seams with production defaults. The worker-thread guard
moved from the facade into `collect()`, so no internal caller can start GL work on the UI thread;
`DeviceRiskSignals.collectGpuBenchmark()` still throws `IllegalStateException` before any GPU work.
One intentional correction: an `InterruptedException` now re-raises the thread's interrupt flag
before the collapsed `error` result, so a host cancelling its worker does not lose the signal. The
raw key set, skip reasons, statistics omissions and the RN bridge output are unchanged.

Instrumented coverage is ten tests in two files. Execution: UI-thread rejection on a real `Looper`
produces an exception and no result, a worker call returns a well-formed raw map, three sequential
calls keep the same decision and field set, two overlapping worker calls both return without
throwing, and a worker call still works after a rejected UI-thread attempt. EGL ownership, run on
the forced path: a caller's prior display/context/draw/read binding is restored exactly and its
context stays usable, no binding is left current when the caller had none, the process-shared
display remains initializable and still returns a config, three repeated forced collects preserve
those invariants, and the result shape is either performed with non-negative counters or a documented
skip reason. No test asserts a duration, draw-call count or budget compliance.

Gradle wiring is test-only: `testInstrumentationRunner`, `androidTest` dependencies on
`androidx.test:runner`/`androidx.test.ext:junit`/JUnit 4, and a new `gradle.properties` enabling
`android.useAndroidX` for that runtime. The release AAR was inspected after the change: manifest with
only `uses-sdk`, no permissions, and no dependency entries. CI now compiles the instrumented sources
(`:compileDebugAndroidTestKotlin`); it cannot execute them because the runners have no device.

RED: `:compileDebugAndroidTestKotlin` failed on the missing androidTest configuration and the missing
`GpuEmulatorHeuristic`/collector seam. GREEN: 103 JVM tests (four new heuristic/seam tests) and ten
instrumented tests pass. The instrumented run used an API 35 arm64 emulator (`ro.hardware=ranchu`,
`ro.hardware.egl=emulation`). A direct observation on that device confirmed the suite exercises real
EGL rather than only the skip contract: the forced path reported `benchmarkPerformed = true` with
identity strings from the ANGLE/SwiftShader translator, 336 draw calls in a 50 ms loop and complete
timing statistics, while the public facade returned `benchmarkPerformed = false` with
`skippedReason = "emulator"`.

Also verified: release AAR, native example debug APK, React Native Android compilation and JVM tests,
full root verification (107 Jest tests, three Node tests, 19-method native parity, package/ecosystem
validation and 24 Pages), `npm pack --dry-run`, and example tests, lint and TypeScript.

A parity audit of the extraction commit confirmed an unchanged key set, value types, skip ordering,
omission semantics and bridge shape, and recorded two behavior improvements that the extraction
introduced but did not document. Cleanup steps are now individually guarded, so a throwing
`eglDestroySurface`/`eglDestroyContext` can no longer escape `finally` and turn an already computed
benchmark into a bridge error. Restoration is gated on `bindingChanged`, so a skip that happens
before the collector makes anything current no longer unbinds the caller's own context; the old
provider unbound it unconditionally. Both belong in the migration release notes as output/side-effect
changes in those branches. The audit also confirmed a leak fix: a compiled vertex shader is now
released when the fragment shader fails. One accepted limitation is restated: a caller holding a
surfaceless context can lose it, because EGL 1.4 rejects restoring a context with no surfaces on
drivers without `EGL_KHR_surfaceless_context`, and the fallback then unbinds.

Emulated GL is not a driver, and this is not device QA. The failure modes still open are a driver
refusing restoration, a surfaceless or non-default-display caller, GL/camera/video coexistence,
Activity teardown mid-benchmark and FD/GPU-memory growth across runs. Two known modeling limits are
recorded rather than silently changed: an `error` result can retain counters from a partially
completed run, and a driver that optimizes the vertex attribute away would still report a performed
benchmark. Both match the deleted React Native provider. The legacy active Frida boundary, standalone
lint/package-content gates and Maven publication remain outstanding; no version bump, registry
publication or Pages deployment was performed.

## Tenth increment: the active loopback boundary becomes a component

This increment resolves the last item in the provider table. The legacy React Native localhost Frida
scan is not extracted into the no-network core and the core gains no socket API. It moves into a new
optional component, `android-active-probes-device-risk-signals` under `sdks/android-active-probes/`,
whose contract explicitly permits loopback socket I/O.
[ADR-0003](adr/0003-active-loopback-probe-component.md) records the decision, its limits and what it
deliberately does not decide.

Two subagents worked in parallel: one implemented the component and its JVM socket tests against a
documented seam, the other established the manifest, Pages and ADR requirements. The parent
integrated the decision record, the ecosystem manifest, the binding wiring and the documentation.

The component exposes `DeviceRiskActiveProbes.collectFridaScan()` returning a typed
`FridaScanSignals` with the same four keys and types as before. Behavior was ported byte-for-byte:
127.0.0.1, port 27042, one 700 ms value used as both connect and read timeout, `0x00` then
`AUTH\r\n` in US-ASCII, a single read of up to six bytes, the `REJECT` prefix check, and
swallow-to-false on any failure. Host, port and timeout are `internal` seams with production
defaults, so no consumer can retarget the scan; the module imports no `com.facebook.react`, declares
no permission or manifest entry, and adds no AndroidX or instrumented-test runtime.
`android/src/main/java/com/reactnativedeviceintel/FridaScanProvider.kt` is deleted and
`getFridaScanSignals()` now converts the component's raw map through `ReactNativeValueConverter`.

Three preserved defects are documented in the code, the component README and the roadmap rather than
silently fixed: the handshake reads once, so a partial or slow reply reads as no REJECT;
`fridaHandshakeReject = false` collapses read timeout, reset, EOF and write failure with a listener
that answered something else; and `defaultPortOpen = false` collapses connection refused, connect
timeout and any other socket failure. A REJECT-like reply authenticates nothing. Fixing any of them
changes emitted meaning and belongs in its own change with breaking-release notes.

RED: the component's tests failed to compile against absent production classes (14 unresolved
references). GREEN: eight JVM tests pass, driving the collector against a local `ServerSocket` on an
ephemeral port for a REJECT reply, a non-REJECT reply, an accepted connection that never answers, an
immediate close after accept, a closed port, and raw-model omission. A mutation check
(`REJECT` to `NOPE`) failed exactly one test, confirming the assertion is load-bearing. The suite
never touches port 27042 and needs no network access.

Ecosystem bookkeeping landed in the same change: a seventh manifest component with
`published: false` and an empty `dependsOn` as the SDK verifier requires, the React Native binding's
`targetDependsOn` extended, ADR-0003 registered in the manifest and the architecture header, new
rows in the repository-layout, source-ownership, library-naming and minimum-verification tables, a
component README, `.gitignore` entries, the npm `files` entry for the new source set, the RN
`srcDirs` entry, and a dedicated CI step that builds and tests the component. Pages surfaces that
described the scan as an unresolved exception were rewritten, and the unqualified "performs no
network request" claims on five pages now state that nothing is sent off the device and that the
optional active probe connects only to loopback.

Verified: eight component JVM tests and its release AAR, 103 core JVM tests, ten instrumented core
tests, core release AAR and native example APK, React Native Android compilation and JVM tests, full
root verification (107 Jest tests, three Node tests, 19-method native parity, seven-component
ecosystem validation and 24 Pages), `npm pack --dry-run`, and example tests, lint and TypeScript.

JVM socket tests are not device QA. Behavior against a real frida-server, OEM builds, IPv6-only
loopback stacks and hosts with a local proxy still needs physical devices. Open follow-ups: whether
an active probe should remain enabled by default, the three preserved defects, the iOS loopback port
check in `ios/JailbreakDetector.m` under the same component rule, standalone lint and
package-content gates, and Maven publication. No version bump, component tag, workspace or registry
publication was created.
