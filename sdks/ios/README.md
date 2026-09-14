# ios-device-risk-signals

Standalone Swift Package for the iOS side of the [Device Risk Signals](../../docs/ECOSYSTEM_ARCHITECTURE.md)
project. It is under active extraction from the React Native binding's `ios/` directory.

- **Package name:** `ios-device-risk-signals`
- **Library product:** `IOSDeviceRiskSignals`
- **Platforms:** iOS 15.1 and Mac Catalyst 15.1. macOS is **not** a platform — the package no
  longer builds for the host Mac and `swift test` no longer works, see
  [Building and testing](#building-and-testing)
- **Dependencies:** Foundation, plus CoreTelephony for `TelephonyInfoProvider`. The package does not
  import React Native, performs no network request, adds no permission, and creates no persistent
  device identifier.

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
| `TelephonyInfoProvider.telephonySignals` | Opportunistic CoreTelephony carrier reads: active-SIM count, carrier name, MCC/MNC, ISO country code and the VoIP-allowed flag — all expected-absent on modern iOS, see below |
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

`TelephonyInfoProvider` is the first provider here that depends on a system framework rather than on
Foundation alone, and the first whose output cannot be produced by the machine that runs the tests.
Three things follow, all of them deliberate:

- **Its reads are compiled out on a simulator.** Both CoreTelephony accessors sit inside
  `#if !TARGET_OS_SIMULATOR`, so on a simulator `-telephonySignals` returns an *empty* dictionary —
  the calls are not in the binary. (`CTTelephonyNetworkInfo` is still constructed, which is why a
  simulator test run prints `com.apple.commcenter.coretelephony.xpc … No such process` log lines.
  That noise is expected and is not a failure.)
- **Even on a device the fields are expected-absent.** Apple has progressively nulled `CTCarrier`
  for non-carrier apps since iOS 16: `carrierName` becomes `"--"` and MCC/MNC become `"65535"`. The
  provider drops those sentinels rather than forwarding a plausible-looking wrong value, so an
  iOS 16+ device also reports a nearly empty dictionary. Documented degradation, not a promised
  value.
- **`imei` is never emitted.** iOS has exposed no public API for it since iOS 5. `TelephonySignals`
  carries the optional field only to make the absence explicit; absent means absent, and emitting a
  placeholder would put a persistent-device-identifier field into a package whose premise is that it
  creates none. The omission is pinned by a test.

CoreTelephony reads no user data and triggers no permission prompt, and none of these fields is an
Apple Required-Reason API, so the binding's privacy manifest still declares zero of them.

**CoreTelephony is why macOS is no longer a package platform.** Its whole API surface is annotated
`API_UNAVAILABLE(macos)`, so the host build is a hard compile error — see
[Building and testing](#building-and-testing).

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
    TelephonyInfoProvider.m
    include/                      # public headers (SwiftPM convention)
      ApplicationInfoProvider.h
      LocaleInfoProvider.h
      NumericConsistencyProvider.h
      RuntimeTimingProvider.h
      SignalStatistics.h
      TelephonyInfoProvider.h
  Tests/IOSDeviceRiskSignalsTests/
    ApplicationInfoProviderTests.swift
    BooleanBoxingTests.swift
    LocaleInfoProviderTests.swift
    ProviderTests.swift
    SignalStatisticsTests.swift
    TelephonyInfoProviderTests.swift
```

The implementation is Objective-C and was moved verbatim from `ios/`, not rewritten, so behaviour is
byte-for-byte identical to what the published binding shipped: the same sample counts, the same
statistics, and the same emitted keys, value types and omission rules. The six headers in
`include/` are the whole public surface; implementation details such as the percentile helper and
the sample-count constant are `static` inside the `.m` files and are not exported. Tests are Swift
and exercise the package only through that public surface, with one documented exception:
`TelephonyInfoProviderTests` reaches the provider's unpublished `-putString:key:value:` sentinel
filter through the Objective-C runtime, because widening the header purely to let a test see it
would have changed the extracted source.

## Building and testing

```sh
cd sdks/ios && xcodebuild test -scheme ios-device-risk-signals \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

(The scheme is the *package* name, `ios-device-risk-signals`, not the library product name.)

That simulator destination is now the **only** way to run the suite.

### `swift test` no longer works, and that is expected

`swift build` and `swift test` always build for the host, regardless of what `Package.swift`
declares, and `TelephonyInfoProvider.m` cannot compile for macOS: every CoreTelephony symbol it
touches — `CTTelephonyNetworkInfo`, `CTCarrier`, `serviceSubscriberCellularProviders`,
`subscriberCellularProvider`, `allowsVOIP` — is annotated `API_UNAVAILABLE(macos)`. Both commands
therefore fail with 11 errors of the form

```text
sdks/ios/Sources/IOSDeviceRiskSignals/TelephonyInfoProvider.m:11:3: error: 'CTTelephonyNetworkInfo'
is unavailable: not available on macOS
...
11 errors generated.
error: fatalError
```

This is expected behaviour, not a regression. `.macOS(.v12)` was declared only so the suite could
run on a developer or CI Mac while every extracted provider was pure Foundation; it was never a
supported consumer platform. It has been removed rather than papered over, because the alternative —
hiding this provider behind a compile-time guard so the host kept building — would have meant the
host suite compiled a stub and proved nothing about the code that ships.

Mac Catalyst is **not** affected, and `device-risk-signals.json`'s `mac-catalyst` platform still
holds: CoreTelephony is available there. Verified by building the package for it, which compiles
`TelephonyInfoProvider.m` with `-target arm64-apple-ios15.1-macabi` and emits only a deprecation
warning for the `subscriberCellularProvider` fallback:

```sh
cd sdks/ios && xcodebuild build -scheme ios-device-risk-signals \
  -destination 'platform=macOS,variant=Mac Catalyst' CODE_SIGNING_ALLOWED=NO
cd sdks/ios && xcodebuild build -scheme ios-device-risk-signals \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO
```

### Conditional compilation

A provider's output can depend on which destination compiled it, so a literal assertion can pin the
destination rather than the provider. Measured on the Objective-C compile task, on the clang command
line rather than assumed:

| Context                                            | `TARGET_OS_SIMULATOR` | `DEBUG`   |
| ---                                                | ---                   | ---       |
| `xcodebuild test`, `arm64-apple-ios15.1-simulator` | 1                     | undefined |
| `xcodebuild build`, `arm64-apple-ios15.1` (device) | 0                     | undefined |
| CocoaPods `RnDeviceIntel`, Debug                   | 1 simulator / 0 device | 1        |
| CocoaPods `RnDeviceIntel`, Release                 | 1 simulator / 0 device | undefined |

(The host `arm64-apple-macosx` rows are gone with the macOS platform. They read
`TARGET_OS_SIMULATOR` 0, `DEBUG` 1 under `swift test` and undefined under `swift test -c release`.)

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

`TelephonyInfoProviderTests` goes one step further: its subject's observations cannot be produced by
a simulator at all, because the CoreTelephony reads are compiled out there and because iOS 16+ nulls
carrier data anyway. It therefore pins only what holds in every context — the key vocabulary, the
`imei` omission, the "no sentinel ever reaches the dictionary" rule, and the type/boxing rules
applied *conditionally on a key being present* — exercises the sentinel filter directly through the
Objective-C runtime, and guards the "a simulator emits nothing" claim behind
`#if targetEnvironment(simulator)`. The tests that depend on a real SIM are written as
"if the key is present, it must look like this" rather than skipped, so they are silent on a
simulator and load-bearing the moment the suite is pointed at hardware. A physical-device run with
an active SIM is the only thing that exercises `simCount` being written, `carrierAllowsVoip` being
boxed, and the filter running against values CoreTelephony really produced; the file says so in its
header comment.

`carrierAllowsVoip` is deliberately absent from `BooleanBoxingTests`' subject table, which asserts an
exact count of inspected booleans — correct for providers that emit theirs unconditionally, and wrong
the moment a device run makes a ninth boolean appear. Its boxing is pinned in
`TelephonyInfoProviderTests` with the same CFBoolean-identity check.

## Relationship to the React Native binding

`react-native-device-risk-signals` is a thin adapter. `ios/DeviceIntel.mm` instantiates
`ApplicationInfoProvider`, `LocaleInfoProvider`, `RuntimeTimingProvider`,
`NumericConsistencyProvider` and `TelephonyInfoProvider` from this package,
and `ios/GpuBenchmarkProvider.m` calls this package's `RNDISummarize`/`RNDIWarmupSlope`. There is no
second copy of the collection logic in the binding, and this package must never depend on React
Native, Flutter, or Capacitor.
