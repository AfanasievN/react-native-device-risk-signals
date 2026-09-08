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
