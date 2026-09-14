# ios-device-risk-signals

Standalone Swift Package for the iOS side of the [Device Risk Signals](../../docs/ECOSYSTEM_ARCHITECTURE.md)
project. It is under active extraction from the React Native binding's `ios/` directory.

- **Package name:** `ios-device-risk-signals`
- **Library product:** `IOSDeviceRiskSignals`
- **Platforms:** iOS 15.1 and Mac Catalyst 15.1 (macOS is declared only so `swift test` runs on a
  developer or CI Mac — see [Building and testing](#building-and-testing))
- **Dependencies:** none beyond Foundation. The package does not import React Native, performs no
  network request, adds no permission, and creates no persistent device identifier.

## Status: in development, unpublished

**There is no installable coordinate for this package.** It is not on any Swift Package registry,
it has no git tag, and it has no release. Do not add it to a `Package.swift` or an Xcode project as
a dependency, and do not treat the names above as proof that an artifact exists — they are the
intended identity recorded in [`device-risk-signals.json`](../../device-risk-signals.json), nothing
more. Consumption is via the repository checkout only, and only for development on this repository.
The remaining extraction and release gates are tracked in the
[migration checklist](../../docs/MIGRATION_ROADMAP.md).

## What it collects

Raw observations only. Nothing here computes a risk score, returns a trusted/untrusted verdict, or
makes a blocking decision; that stays with the calling application and its backend.

Currently extracted:

| Symbol | Raw observation |
| --- | --- |
| `RuntimeTimingProvider.runtimeTimingSignals` | Clock-source name, sample count, smallest observed positive delta, and median/p95/MAD of 256 back-to-back `CFAbsoluteTimeGetCurrent` deltas in nanoseconds |
| `NumericConsistencyProvider.numericConsistencySignals` | A fixed 1024-round 32-bit FNV-style digest, five transcendental double results, and two IEEE-754 behaviour flags (signed zero, subnormals) |
| `RNDISummarize` / `RNDIWarmupSlope` | Shared statistics helpers: nearest-rank median/p95, median absolute deviation, coefficient of variation, and a first-half/second-half warm-up slope |

`CFAbsoluteTimeGetCurrent` is a wall-clock read, deliberately not `mach_absolute_time` or
`systemUptime`, so no Apple Required-Reason API is used and no privacy-manifest entry is needed.
The numeric probe is pure arithmetic. Neither observation reads user data, device identifiers, or
anything requiring a permission prompt.

Everything else on iOS still lives in the React Native package's `ios/` directory and moves here
incrementally.

## Layout

```text
sdks/ios/
  Package.swift
  Sources/IOSDeviceRiskSignals/
    NumericConsistencyProvider.m
    RuntimeTimingProvider.m
    SignalStatistics.m
    include/                      # public headers (SwiftPM convention)
      NumericConsistencyProvider.h
      RuntimeTimingProvider.h
      SignalStatistics.h
  Tests/IOSDeviceRiskSignalsTests/
    ProviderTests.swift
    SignalStatisticsTests.swift
```

The implementation is Objective-C and was moved verbatim from `ios/`, not rewritten, so behaviour is
byte-for-byte identical to what the published binding shipped: the same sample counts, the same
statistics, and the same emitted keys and value types. The three headers in `include/` are the whole
public surface; implementation details such as the percentile helper and the sample-count constant
are `static` inside the `.m` files and are not exported. Tests are Swift and exercise the package
only through that public surface.

## Building and testing

```sh
swift build --package-path sdks/ios
swift test  --package-path sdks/ios
```

`swift test` runs on the host Mac. That works because everything extracted so far is pure
computation over Foundation with no UIKit or iOS-only framework, which is why `Package.swift` lists
`.macOS` alongside `.iOS` and `.macCatalyst`. macOS is a test-execution convenience, not a supported
consumer platform; providers that later need an iOS-only framework will need a simulator
destination instead, for example:

```sh
xcodebuild test -scheme IOSDeviceRiskSignals -destination 'platform=iOS Simulator,name=iPhone 17'
```

## Relationship to the React Native binding

`react-native-device-risk-signals` is a thin adapter. `ios/DeviceIntel.mm` instantiates
`RuntimeTimingProvider` and `NumericConsistencyProvider` from this package, and
`ios/GpuBenchmarkProvider.m` calls this package's `RNDISummarize`/`RNDIWarmupSlope`. There is no
second copy of the maths in the binding, and this package must never depend on React Native,
Flutter, or Capacitor.
