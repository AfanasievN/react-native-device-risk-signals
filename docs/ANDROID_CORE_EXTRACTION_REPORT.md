# Android core extraction: runtime timing

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
