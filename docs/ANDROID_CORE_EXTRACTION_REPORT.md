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
