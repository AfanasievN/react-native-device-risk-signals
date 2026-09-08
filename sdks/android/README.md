# android-device-risk-signals

Standalone Android SDK under active extraction. It adds no runtime libraries beyond the Android and
Kotlin runtime, performs no network requests, and exposes typed Kotlin models rather than React
Native bridge containers. JUnit is used only for local unit tests and is not shipped in the AAR.

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
queries. The legacy React Native localhost TCP Frida scan remains in its adapter and is an
unresolved exception to the no-network architecture; the standalone SDK exposes no scan method.

All nine methods run only when called. They add no permissions, prompts, transport, or automatic
collection. See the [native Android example](example/README.md) for a consumer that has no React
Native dependency and exposes a separate collection button for each method.

The intended Maven coordinate is `io.github.afanasievn:android-device-risk-signals`. It is not
published yet, so applications should not declare that coordinate until a release is announced.

Run its independent unit tests and build both the library and native consumer from the repository
root (the existing Gradle wrapper is reused):

```sh
example/android/gradlew -p sdks/android :testDebugUnitTest :assembleRelease :example:assembleDebug --no-daemon
```
