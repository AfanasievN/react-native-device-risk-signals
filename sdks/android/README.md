# android-device-risk-signals

Standalone Android SDK under active extraction. It adds no runtime libraries beyond the Android and
Kotlin runtime, performs no network requests, and exposes typed Kotlin models rather than React
Native bridge containers. JUnit is used only for local unit tests and is not shipped in the AAR.

Remaining provider extraction and release gates are tracked in the
[migration checklist](../../docs/MIGRATION_ROADMAP.md#1-finish-android-extraction).

Network, telephony and cached-location collections preserve the existing observations. These
include sensitive local addresses, carrier/SIM metadata and coordinates. Native hosts must decide
their collection purpose, consent and retention before calling them. This migration adds no
permissions to the SDK manifest and never opens a permission prompt.

| Host permission | Existing observation scope |
| --- | --- |
| `ACCESS_NETWORK_STATE` | Active connectivity/capabilities and link properties; other local observations may remain available without it |
| `READ_PHONE_STATE` | Active SIM count when already granted; no IMEI or persistent identifier is collected |
| `ACCESS_COARSE_LOCATION` or `ACCESS_FINE_LOCATION` | Last-known cached fix only; no location subscription or fresh-fix request |

Permission-gated observations remain unavailable without the host's grant. Location/mock fields
are omitted when no cached fix exists. Host permission requests, if needed for its product, remain
outside the SDK and the native example.

The media/app-audit collection preserves sensitive accessibility service component names and
matches against the existing finite `KnownAppLists` package list. Collect them only for a documented
purpose with appropriate consent and retention; an empty list is not proof that no service or app
exists. Host package visibility constrains matches, and the standalone manifest adds no queries.
Bluetooth collection reads only the bonded-device count, never names or addresses. It requires the
host's existing `BLUETOOTH_CONNECT` grant on Android 12+ or legacy `BLUETOOTH` permission on older
versions; neither the SDK nor example declares or requests them. No Bluetooth discovery occurs.
Point-in-time device security posture reads lock state, advertised biometric hardware features,
StrongBox support, clock settings and provisioning state. It does not authenticate the user, test
biometric enrollment, create a key or install transaction/screenshot observers. Existing raw field
names and fallback behavior are preserved during extraction; none represents a trust verdict.

Transaction observations require explicit host ownership: an inactive session may be attached to an
Activity only for a documented transaction-context purpose and must be detached/closed by its host.
It observes touch obscuration flags (not coordinates or text), screenshot events (not image content)
and app visibility in screen recording (not video content). API 34/35 capture registration requires
the host's existing `DETECT_SCREEN_CAPTURE`/`DETECT_SCREEN_RECORDING` declarations, respectively;
the SDK and example add neither. Ordinary point-in-time collection never starts a session.
Accessibility counts and finite remote-access package matches remain sensitive and visibility-limited.

GPU extraction preserves high-entropy renderer/vendor/version strings and timing statistics.
Invoke it explicitly only for a documented purpose after consent/retention review. It uses a small
off-screen surface, not camera, screen capture or visible rendering. The native caller must supply
a worker thread; no permission or network operation is needed. A 50 ms draw-loop target is not a
hard timeout: driver setup and `glFinish()` may take longer. Caller timeouts do not cancel a driver
call. Keep this workload disabled by default until physical-device calibration.

Currently extracted:

- `DeviceRiskSignals.collectDeviceIdentity()`
- `DeviceRiskSignals.collectLocale()`
- `DeviceRiskSignals.collectRuntimeTiming()`
- `DeviceRiskSignals.collectNumericConsistency()`
- `DeviceRiskSignals.collectAudioLatency()`
- `DeviceRiskSignals.collectApplication()`
- `DeviceRiskSignals.collectHardware()`
- `DeviceRiskSignals.collectFonts()`
- `DeviceRiskSignals.collectOsIntegrity()`
- `DeviceRiskSignals.collectNetwork()`
- `DeviceRiskSignals.collectTelephony()`
- `DeviceRiskSignals.collectGeolocation()`
- `DeviceRiskSignals.collectMediaBluetoothApps()`
- `DeviceRiskSignals.collectDeviceSecurityPosture()`
- `DeviceRiskSignals.collectTransactionSafety()`
- `DeviceRiskSignals.collectGpuBenchmark()` (worker thread only)

```kotlin
val signals = DeviceRiskSignals(applicationContext)
val identity: DeviceIdentitySignals = signals.collectDeviceIdentity()
val locale: LocaleSignals = signals.collectLocale()
// Optional active measurement; call only when your application needs timing observations.
val timing: RuntimeTimingSignals = signals.collectRuntimeTiming()
val numeric: NumericConsistencySignals = signals.collectNumericConsistency()
val audio: AudioLatencySignals = signals.collectAudioLatency()
val application: ApplicationSignals = signals.collectApplication()
val hardware: HardwareSignals = signals.collectHardware()
// Optional, expensive, high-entropy observation; collect only for a documented purpose.
val fonts: FontsSignals = signals.collectFonts()
val integrity: OsIntegritySignals = signals.collectOsIntegrity()
val network: NetworkSignals = signals.collectNetwork()
val telephony: TelephonySignals = signals.collectTelephony()
val geolocation: GeolocationSignals = signals.collectGeolocation()
// Sensitive finite app/accessibility observations; invoke only for a documented purpose.
val media: MediaBluetoothAppsSignals = signals.collectMediaBluetoothApps()
val posture: DeviceSecurityPostureSignals = signals.collectDeviceSecurityPosture()
val transaction: TransactionSafetySignals = signals.collectTransactionSafety()
```

The current React Native package compiles these same sources and converts `toRawMap()` results only
at its TurboModule boundary. Remaining Android providers will move here probe-by-probe.

`collectRuntimeTiming()` measures a bounded series of native monotonic-clock intervals. It returns
the native clock source and sample count; resolution, median, p95, and median absolute deviation are
omitted when no positive intervals are available. A clock-read failure propagates to the caller.
JavaScript timer/event-loop measurements and JS-to-native call duration remain owned by the React
Native binding. Its `runtime_timing` probe remains disabled by default. The standalone SDK does not
schedule collection automatically or provide the binding's event orchestration.

`collectNumericConsistency()` returns the native deterministic integer and floating-point vector.
Comparisons against JavaScript values belong to the React Native binding; the standalone result is
not a consistency verdict. The binding's `numeric_consistency` probe remains disabled by default.

`collectAudioLatency()` reads Android output-buffer and sample-rate properties. Its latency value is
a buffer-duration estimate, not measured playback or loopback latency. It does not start playback or
recording. Unavailable properties and estimates are omitted from `toRawMap()`.

`collectApplication()` reads metadata for the host application's own package, including available
signing and installation-source observations. It preserves the compatibility `installerPackage`
alias alongside the more specific installer fields and does not enumerate installed applications.

`collectHardware()` returns the existing Android hardware, display, memory, battery, power, NFC, and
storage observations. Extraction preserves the existing platform checks and omission behavior;
it introduces no additional data collection or permissions.

`collectFonts()` returns the existing system-font observations. Font data is high entropy and
collection can be expensive: call it explicitly only for a documented purpose and apply the host
application's consent and retention policy. The existing React Native probe default is unchanged.

`collectOsIntegrity()` preserves the existing passive debugger, emulator, root-artifact, hook, and
process-local Frida observations, including `/proc`, mapped libraries, thread names, and pipe
evidence. It returns raw fields, not an integrity verdict. Finite known-package observations depend
on the host application's package visibility: `false` can mean not visible, not necessarily absent.
The standalone SDK manifest adds no package queries; the React Native manifest keeps its existing
queries. The active localhost Frida scan lives in the optional [active-probes component](../android-active-probes/README.md) per [ADR-0003](../../docs/adr/0003-active-loopback-probe-component.md); this passive core exposes no scan method and opens no socket. Its legacy
`installedFlaggedApps` name denotes list matches, not a risk verdict. Accessibility read failures
still collapse into an empty list; music-read failures still become false. These inherited fallbacks
need separate compatibility work and must not be interpreted as confirmed absence.

`collectDeviceSecurityPosture()` exposes only point-in-time reads. `biometryAvailable` denotes
advertised hardware features, not enrollment or a successful authentication check. Transaction
touch/capture observation uses the separate session described below.

All sixteen collection methods run only when called. They add no permissions, prompts, transport, or automatic
collection. See the [native Android example](example/README.md) for a consumer that has no React
Native dependency and exposes a separate collection button for each method.

## Transaction observation session

`collectTransactionSafety()` returns lock, interaction, accessibility-count, finite remote-app and
call/audio observations only. To observe UI events, create an inactive session and explicitly attach
it on the main thread:

```kotlin
val session = signals.createTransactionObservationSession() // No callbacks registered.
session.attach(activity) // Main thread; starts history on first attachment.
val observations = session.snapshot()?.toRawMap() // Any thread; null before first attach.
session.detach() // Main thread; stop registrations, retain history.
session.close() // Main thread; terminal/idempotent. Use a new session for a new history.
```

Detach when the host pauses/stops and close when its owner is destroyed. Reattachment to the same
Activity and installed wrapper is a no-op; detach/attach explicitly to retry unavailable registrations.
Calling attach after close throws. Snapshot remains readable after close but cannot restart collection.
The session itself never dispatches threads or waits. Registration cleanup is best-effort; invalidated
callback generations cannot write further observations, even if a host wrapper retains them.

Historical positive touches/screenshots survive observation gaps. Recording visibility and unobserved
screenshot negatives require current coverage; detach clears them. Partial obscuration is omitted
below API 29. The snapshot's monotonic start time is not evidence of continuous observation or a
per-payment boundary. React Native keeps lazy opt-in and owns UI dispatch/lifecycle; its timeout
cancels queued-but-not-started work, not a running Android API call. These migration semantics are
documented in [ADR-0002](../../docs/adr/0002-explicit-android-transaction-session.md) and require release
notes and physical-device QA before publication. The transaction probe remains disabled by default.

## GPU execution

Call `collectGpuBenchmark()` from a dedicated background worker, never the UI/render thread.
The facade rejects UI-thread calls with `IllegalStateException` before GPU work starts; it does not
create a worker or schedule collection for the host. React Native already dispatches native probes
to background workers. The example uses a single worker and disables its GPU button while running.

```kotlin
// Inside the host's worker, after explicit opt-in:
val gpu: GpuBenchmarkSignals = signals.collectGpuBenchmark()
val raw = gpu.toRawMap()
```

The existing emulator skip, partial identity on failure, 32x32 pbuffer and 50 ms draw-loop target
are retained. Setup/driver calls can exceed that target; neither a JS timeout nor interruption can
forcibly stop a driver call. Repeated/concurrent calls can interfere with measured performance:
hosts should serialize their benchmark runs and calibrate with their actual rendering workload.

Cleanup attempts to delete owned GL objects while their context is current, then restore the
calling thread's prior EGL binding and destroy only the created surface/context. An unchanged
binding is not touched, and the shared display is never terminated. Restoration is best-effort:
a driver failure can prevent it, so use a dedicated worker without application rendering state.
Independent cleanup attempts and partial shader/program failures are tested with a fake driver.
An instrumented `androidTest` suite additionally runs the real EGL path and asserts that a caller's
prior binding is restored, that no binding is left behind when the caller had none, that the shared
display stays usable, and that repeated forced collects keep those invariants. It never asserts a
timing, draw-call or budget value. Instrumented dependencies are test-only: the release AAR still
declares no runtime dependency and no permission.

```sh
# From the repository root, with a booted emulator or connected device:
example/android/gradlew -p sdks/android :connectedDebugAndroidTest --no-daemon
```

Emulated GL is not a driver. A driver refusing restoration, a surfaceless or non-default-display
caller, GL/camera/video coexistence, Activity teardown mid-benchmark and resource growth across
runs still require physical-device QA, which remains a release gate.

## Local publication

The component publishes a release AAR, a sources jar and full POM metadata to a file repository
inside its own build output, so artifact consumption can be verified without any registry:

```sh
example/android/gradlew -p sdks/android :publishReleasePublicationToLocalBuildRepository --no-daemon
```

The result lands in `sdks/android/build/local-maven` under ``io.github.afanasievn:android-device-risk-signals``.
Nothing is written to `~/.m2` and no credentials are involved. A real Maven Central release still
needs signing, a javadoc or Dokka artifact, Sonatype namespace verification, the staging flow and a
version other than `0.1.0-SNAPSHOT`; those are tracked in
[the migration checklist](../../docs/MIGRATION_ROADMAP.md).

## Development distribution

The intended Maven coordinate is `io.github.afanasievn:android-device-risk-signals`. It is not
published yet, so applications should not declare that coordinate until a release is announced.

Run its independent unit tests and build both the library and native consumer from the repository
root (the existing Gradle wrapper is reused):

```sh
example/android/gradlew -p sdks/android :testDebugUnitTest :assembleRelease :example:assembleDebug --no-daemon
```
