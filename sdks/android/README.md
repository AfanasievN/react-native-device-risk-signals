# android-device-risk-signals

Standalone Android SDK under active extraction. It adds no runtime libraries beyond the Android and
Kotlin runtime, performs no network requests, and exposes typed Kotlin models rather than React
Native bridge containers. JUnit is used only for local unit tests and is not shipped in the AAR.

Currently extracted:

- `DeviceRiskSignals.collectDeviceIdentity()`
- `DeviceRiskSignals.collectLocale()`

```kotlin
val signals = DeviceRiskSignals(applicationContext)
val identity: DeviceIdentitySignals = signals.collectDeviceIdentity()
val locale: LocaleSignals = signals.collectLocale()
```

The current React Native package compiles these same sources and converts `toRawMap()` results only
at its TurboModule boundary. Remaining Android providers will move here probe-by-probe.

The intended Maven coordinate is `io.github.afanasievn:android-device-risk-signals`. It is not
published yet, so applications should not declare that coordinate until a release is announced.

Run its independent unit tests from the repository root:

```sh
example/android/gradlew -p sdks/android testDebugUnitTest --no-daemon
```
