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
| `ApplicationInfoProvider.applicationSignals` | Host-app identity from the main bundle: short version, build, bundle id, executable name, minimum OS version, App Store receipt presence and environment, app-extension and embedded-provisioning-profile flags, and the compile-time simulator/debuggable pair |
| `LocaleInfoProvider.localeSignals` | Language/country/currency codes, decimal and grouping separators, the preferred-language ordering, measurement system, time-zone id and UTC offset in minutes, calendar identifier, first day of week, and whether the locale uses a 24-hour clock |
| `RuntimeTimingProvider.runtimeTimingSignals` | Clock-source name, sample count, smallest observed positive delta, and median/p95/MAD of 256 back-to-back `CFAbsoluteTimeGetCurrent` deltas in nanoseconds |
| `NumericConsistencyProvider.numericConsistencySignals` | A fixed 1024-round 32-bit FNV-style digest, five transcendental double results, and two IEEE-754 behaviour flags (signed zero, subnormals) |
| `RNDISummarize` / `RNDIWarmupSlope` | Shared statistics helpers: nearest-rank median/p95, median absolute deviation, coefficient of variation, and a first-half/second-half warm-up slope |

`CFAbsoluteTimeGetCurrent` is a wall-clock read, deliberately not `mach_absolute_time` or
`systemUptime`, so no Apple Required-Reason API is used and no privacy-manifest entry is needed.
The numeric probe is pure arithmetic. The locale probe reads `NSLocale`, `NSTimeZone` and
`NSCalendar` only. None of these observations reads user data, device identifiers, or anything
requiring a permission prompt.

`LocaleInfoProvider` deliberately does **not** report `keyboardLanguages`, even though the shared
`LocaleSignals` contract carries that field: enumerating enabled keyboards through
`-[UITextInputMode activeInputModes]` is an Apple Required-Reason API (Active Keyboard, DDA9.1)
whose only sanctioned reason is a custom-keyboard extension. Android supplies that signal instead.
The omission is pinned by a test — do not add an active-keyboard read here without an explicit
policy decision, and note that doing so would also invalidate the binding's privacy manifest, which
declares zero Required-Reason APIs.

`ApplicationInfoProvider` deliberately does **not** report `getTaskAllowEntitlement`, even though
the shared `ApplicationSignals` contract reserves that field. Reading the `get-task-allow`
entitlement needs `SecTaskCreateFromSelf` / `SecTaskCopyValueForEntitlement`, which the public
iPhoneOS `Security` headers do not declare; reaching them would take hand-written extern
declarations and cross this package's public-system-API boundary. Reserved means absent, not
`false` — emitting `false` would claim the entitlement was checked and found off. The omission is
pinned by a test.

All of `ApplicationInfoProvider`'s reads are `NSBundle`/`NSFileManager` calls against the host app's
own bundle. It enumerates nothing outside that bundle, adds no permission, and creates no
identifier.

Everything else on iOS still lives in the React Native package's `ios/` directory and moves here
incrementally.

## Layout

```text
sdks/ios/
  Package.swift
  Sources/IOSDeviceRiskSignals/
    ApplicationInfoProvider.m
    LocaleInfoProvider.m
    NumericConsistencyProvider.m
    RuntimeTimingProvider.m
    SignalStatistics.m
    include/                      # public headers (SwiftPM convention)
      ApplicationInfoProvider.h
      LocaleInfoProvider.h
      NumericConsistencyProvider.h
      RuntimeTimingProvider.h
      SignalStatistics.h
  Tests/IOSDeviceRiskSignalsTests/
    ApplicationInfoProviderTests.swift
    LocaleInfoProviderTests.swift
    ProviderTests.swift
    SignalStatisticsTests.swift
```

The implementation is Objective-C and was moved verbatim from `ios/`, not rewritten, so behaviour is
byte-for-byte identical to what the published binding shipped: the same sample counts, the same
statistics, and the same emitted keys, value types and omission rules. The five headers in
`include/` are the whole public surface; implementation details such as the percentile helper and
the sample-count constant are `static` inside the `.m` files and are not exported. Tests are Swift
and exercise the package only through that public surface.

## Building and testing

```sh
swift build --package-path sdks/ios
swift test  --package-path sdks/ios
```

`swift test` runs on the host Mac. That works because everything extracted so far uses Foundation
only, with no UIKit and no iOS-only framework, which is why `Package.swift` lists `.macOS` alongside
`.iOS` and `.macCatalyst`. macOS is a test-execution convenience, not a supported consumer platform;
providers that later need an iOS-only framework will need a simulator destination instead, for
example:

```sh
cd sdks/ios && xcodebuild test -scheme ios-device-risk-signals \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

(The scheme is the *package* name, `ios-device-risk-signals`, not the library product name.)

Run that simulator destination as well as `swift test` whenever a provider's output depends on
conditional compilation, because the two compile the same source with different macros. Measured on
the Objective-C compile task for `ApplicationInfoProvider.m`:

| Context                                            | `TARGET_OS_SIMULATOR` | `DEBUG`   |
| ---                                                | ---                   | ---       |
| `swift test`, host `arm64-apple-macosx12.0`        | 0                     | 1         |
| `swift test -c release`, host                      | 0                     | undefined |
| `xcodebuild test`, `arm64-apple-ios15.1-simulator` | 1                     | undefined |
| CocoaPods `RnDeviceIntel`, Debug                   | 1 simulator / 0 device | 1        |
| CocoaPods `RnDeviceIntel`, Release                 | 1 simulator / 0 device | undefined |

The `xcodebuild` row is the one that catches people out: building this package with `xcodebuild`
compiles the Objective-C target with `-DSWIFT_PACKAGE -DXcode` and **no** `-DDEBUG`, even in the
Debug configuration, while the Swift test target *is* compiled with `-DDEBUG`. A test that mirrored
its own `#if DEBUG` onto an Objective-C `#if DEBUG` field would therefore pass under `xcodebuild`
for the wrong reason. `ApplicationInfoProviderTests` pins the parts that hold in every context — the
key vocabulary, the omission rules, the boxing, and the rule that a simulator build is always
reported debuggable — mirrors `TARGET_OS_SIMULATOR` through Swift's independent
`targetEnvironment(simulator)`, and skips the `DEBUG`-dependent assertion under `xcodebuild` with a
message saying why rather than asserting something that only looks true.

`LocaleInfoProviderTests` is the first suite whose subject is not pure computation: `-localeSignals`
reads the *host's* locale, time zone and calendar, with no seam to inject a fixed one. Those tests
therefore assert literals only where the value is host-independent (the key vocabulary, the
measurement-system mapping, the 24-hour derivation rule, the Required-Reason omission) and otherwise
compare the emitted value against Foundation's modern Swift API — a different code path from the
provider's `-[NSLocale objectForKey:]` reads, so a swapped key still fails.

## Relationship to the React Native binding

`react-native-device-risk-signals` is a thin adapter. `ios/DeviceIntel.mm` instantiates
`ApplicationInfoProvider`, `LocaleInfoProvider`, `RuntimeTimingProvider` and
`NumericConsistencyProvider` from this package,
and `ios/GpuBenchmarkProvider.m` calls this package's `RNDISummarize`/`RNDIWarmupSlope`. There is no
second copy of the collection logic in the binding, and this package must never depend on React
Native, Flutter, or Capacitor.
