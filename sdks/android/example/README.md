# Native Android SDK example

This development app consumes `android-device-risk-signals` directly through `implementation(project(":"))`.
It has no React Native dependency. The SDK is partially extracted and unpublished; this example is
a local integration reference, not an instruction to install a Maven release.

The app exposes nine separate buttons: identity, locale, runtime timing, native numeric vectors,
audio properties, application metadata, hardware, fonts, and passive OS integrity. Collection starts
only when a button is pressed. Results are local raw observations;
the app does not upload them or calculate a risk score.

## Using the SDK

Create the facade with an application context and call only the methods your application needs:

```kotlin
import io.github.afanasievn.devicerisksignals.DeviceRiskSignals

val signals = DeviceRiskSignals(applicationContext)
val identity = signals.collectDeviceIdentity()
val locale = signals.collectLocale()
val timing = signals.collectRuntimeTiming()
val numeric = signals.collectNumericConsistency()
val audio = signals.collectAudioLatency()
val application = signals.collectApplication()
val hardware = signals.collectHardware()
// Optional, expensive, high-entropy collection.
val fonts = signals.collectFonts()
val integrity = signals.collectOsIntegrity()

// Convert a typed result when the host application needs a raw map.
val rawIdentity = identity.toRawMap()
```

These calls are synchronous and explicit. Timing samples the native clock; it does not include
JavaScript or bridge measurements. Numeric data contains native vectors without JavaScript
comparisons. Audio latency is a property-derived buffer-duration estimate and does not measure
playback or loopback. Missing observations remain absent in raw maps. None of these methods adds a
permission prompt, network request, or automatic collection schedule.

Application metadata describes only this app's package, including available signing and installer
fields. Hardware includes the existing Android storage observations. Font collection is expensive
and high entropy; its dedicated button makes collection explicit. The existing React Native fonts
probe default is unchanged. This extraction adds no permissions or new categories of observations.
OS integrity includes process-local Frida evidence but no localhost TCP scan. Finite package checks
are constrained by host package visibility; `false` does not establish that a package is absent.
The standalone SDK adds no package queries.

Host applications own any event envelope, serialization, consent policy, storage, and transport.
The complete React Native probe set is not yet available from this standalone facade.

## Build

From the repository root, with an Android SDK and the repository's Java/Gradle toolchain configured:

```sh
example/android/gradlew -p sdks/android :testDebugUnitTest :assembleRelease :example:assembleDebug --no-daemon
```

The existing wrapper is reused; the Gradle build is rooted in `sdks/android`. The library output is
`sdks/android/build/outputs/aar/android-device-risk-signals-release.aar`; the example APK is
`sdks/android/example/build/outputs/apk/debug/example-debug.apk`.

Building the consumer checks the native integration boundary. Physical-device validation remains
necessary before publishing the SDK or changing collection defaults.
