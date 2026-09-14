# ios-device-risk-signals

Standalone Swift Package for the iOS side of the [Device Risk Signals](../../docs/ECOSYSTEM_ARCHITECTURE.md)
project. It is under active extraction from the React Native binding's `ios/` directory.

- **Package name:** `ios-device-risk-signals`
- **Library product:** `IOSDeviceRiskSignals`
- **Platforms:** iOS 15.1 and Mac Catalyst 15.1. macOS is **not** a platform — the package no
  longer builds for the host Mac and `swift test` no longer works, see
  [Building and testing](#building-and-testing)
- **Dependencies:** Foundation, plus CoreTelephony for `TelephonyInfoProvider`, AVFoundation for
  `AudioLatencyProvider`, SystemConfiguration + CFNetwork for `NetworkInfoProvider`, Metal +
  QuartzCore for `GpuBenchmarkProvider`, and UIKit for `DeviceInfoProvider`. All are picked up by
  clang module autolinking; the package declares no `linkerSettings`. It does not import React
  Native, performs no network request, adds no permission, and creates no persistent device
  identifier. (UIKit is new as of `DeviceInfoProvider`: `UIDevice` is the only supported way to read
  `systemName`/`systemVersion`/`userInterfaceIdiom`, and it is read behind a main-thread hop — see
  below.)

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
| `NetworkInfoProvider.networkSignals` | Interface topology from `getifaddrs` (up, non-loopback names and their non-link-local IPv4/IPv6 addresses), transport classification from `SCNetworkReachability`, VPN presence from scoped system-proxy entries, and the configured HTTP proxy host and port |
| `AudioLatencyProvider.audioLatency` | `AVAudioSession` property reads: output and input latency and IO buffer duration in milliseconds, the native sample rate in Hz, and whether anything was measured |
| `DeviceInfoProvider.deviceIdentity` | Permission-free device and OS identity: the `"Apple"` manufacturer/brand constants, the `hw.machine` hardware model, `UIDevice`'s system name, system version and tablet idiom, the Mac Catalyst and iOS-app-on-Mac flags, and the `kern.osversion`/`version`/`osrelease`/`ostype` kernel fingerprint |
| `GpuBenchmarkProvider.gpuBenchmark` | Metal GPU device name plus a blit-throughput count over a fixed time budget, the last command buffer's GPU time, and the operation-time statistics borrowed from `SignalStatistics`. Self-skips on the simulator |
| `RNDIRequireWorkerThread` | Execution-thread contract for GPU collection: asserts, never dispatches. The iOS mirror of the Android core's `GpuExecutionPolicy.requireWorker` |
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

`NetworkInfoProvider` is the most privacy-sensitive provider extracted so far, and the only one whose
probe is **enabled by default**. It emits the host's local IP addresses, its interface-name topology,
whether a VPN tunnel is up, and the configured HTTP proxy host and port. Four things it deliberately
drops, each of them a value it decided not to disclose rather than an accident of the enumeration:

- **Loopback interfaces** (`IFF_LOOPBACK`), so `lo0`, `127.0.0.1` and `::1` never appear.
- **Down interfaces** (no `IFF_UP`), so a configured-but-inactive interface is not disclosed.
- **IPv6 link-local addresses** (the literal `fe80` prefix), which carry an interface-derived host
  portion and are useless to a backend. The filter is IPv6-only and a prefix test: an IPv4
  169.254.0.0/16 auto-configuration address *is* emitted.
- **Non-IP address families**, so the `AF_LINK` entries — which is where `getifaddrs` exposes the MAC
  address — are skipped. A hardware address is the one category this package promises never to emit.

It also never collects `wifiSsid` or `wifiBssid`. `CNCopyCurrentNetworkInfo` needs the
`com.apple.developer.networking.wifi-info` entitlement, which the host application may not carry, and
an SSID is a location proxy besides; the shared TypeScript contract marks both expected-null on iOS.
Every one of these omissions is pinned by a test.

Two derivations in that provider are easy to "tidy" into something subtly different, so both are
pinned explicitly. The transport comes from `SCNetworkReachability`, not from interface names —
`en0` can be Wi-Fi, wired, or an unassociated-but-up radio, and `utun0`/`utun1` exist for system
services with no user VPN, which is the guess an earlier version of this provider got wrong. And
`isConnected` is derived from the *reachability* answer while `connectionType` may afterwards be
overwritten with `"vpn"`, so a host with a tunnel configured but no route out legitimately reports
`connectionType: "vpn"` with `isConnected: false`. `isConnected` is not `connectionType != "none"`.

`AudioLatencyProvider` reads four `AVAudioSession` properties and nothing else: no engine, no
permission, and — the part that is invisible in the emitted dictionary — **no session activation**.
`-setActive:` is a process-wide side effect that interrupts other audio and, paired with a recording
category, is the step that would turn a permission-free probe into one that prompts. Since the
dictionary cannot show whether that held, the test suite swizzles the four mutating `AVAudioSession`
entry points and asserts that the provider calls none of them. The probe ships **disabled by
default** (`src/probes/audioLatencyProbe.ts`), so those tests are the only routine guard on it.

`DeviceInfoProvider` is the first extracted provider whose probe is **enabled by default and
budgeted at 200 ms** — the tightest budget in the repository — and the first that imports UIKit. Two
things about it are deliberate and easy to "tidy" into something worse:

- **It hops to the main thread, and the hop moved with it.** `UIDevice` is declared
  `NS_SWIFT_UI_ACTOR` in the SDK and none of `systemName`, `systemVersion` or `userInterfaceIdiom`
  carries an `NS_SWIFT_NONISOLATED` exemption, so all three are main-actor isolated; the provider
  wraps exactly those three reads in a `dispatch_sync` guarded by `[NSThread isMainThread]`.
  [ADR-0005](../../docs/adr/0005-ios-threading-contract.md) plans to relocate hops of this kind into
  the binding, but that is a separate slice, so the provider was moved byte-for-byte with the hop
  intact. The `isMainThread` branch is not optional cleverness: without it, a main-thread caller
  deadlocks instantly and permanently. Both branches are pinned by tests, and the main-thread one is
  pinned by being the test itself — XCTest runs test methods on the main thread.
- **`model` is `hw.machine`, not `UIDevice.model`.** `sysctlbyname("hw.machine")` gives
  `"iPhone17,1"`; `UIDevice.model` gives the useless `"iPhone"`. On a simulator `hw.machine` is the
  host architecture, which is the documented answer there rather than a bug. None of the four
  `sysctl` keys it reads is an Apple Required-Reason API — `kern.boottime` is, and it is deliberately
  not read.

The two Mac fields answer different questions and must not be conflated: `isMacCatalystApp` is the
compile-time `TARGET_OS_MACCATALYST` conditional, while `isIosAppOnMac` is the run-time answer for an
unmodified iOS binary on Apple silicon macOS. Both branches of the first assign, so the key is never
absent.

`GpuBenchmarkProvider` ships **disabled by default** (`src/probes/gpuBenchmarkProbe.ts`) and is the
only provider here that must *not* run on the main thread. That requirement is now stated as an
executable precondition rather than a comment:

```objc
void RNDIRequireWorkerThread(BOOL isMainThread);   // raises NSInternalInconsistencyException
```

`-gpuBenchmark` calls it as its first statement, before the simulator skip — the same position and
the same reason as `GpuBenchmarkCollector.collect()` on Android, whose comment reads "Guard here, not
only in the facade, so no internal caller can start GL work on the UI thread". This is the inverse of
`TransactionObservationSession.requireMainThread()` in the Android core and the shape ADR-0005
prescribes for iOS: **assert a precondition, never dispatch.** A policy that quietly hopped to a
worker queue would hide every caller's thread choice and turn a synchronous contract into an
asynchronous one; a benchmark that ran anyway would block the UI for its whole budget and would
measure a contended thread. The thread is a parameter, not an internal `+[NSThread isMainThread]`
read, exactly as Kotlin's `requireWorker(isMainThread: Boolean)` — that keeps the observation at the
call site, where ADR-0005 puts dispatch, and is what lets both directions be tested from one thread.
`NSAssert` was rejected because it is unavailable in a C function and `NS_BLOCK_ASSERTIONS` compiles
its C counterpart out of release builds, so the contract would evaporate in the configuration that
ships. The raised exception carries byte-for-byte the message the Android core raises.

The header also publishes `RNDIExecutionPolicyFailure(block)`, which runs a block and returns the
exception it raised. It is a test seam and nothing in the collection paths calls it: Swift cannot
catch an Objective-C exception, and this package's test target is Swift-only, so without a catch on
the Objective-C side the rejecting direction of the contract would be unobservable and the policy
would be no better than the comment it replaces. It is named after the policy so it is not mistaken
for a general-purpose exception swallower.

Everything else on iOS still lives in the React Native package's `ios/` directory and moves here
incrementally.

## Layout

```text
sdks/ios/
  Package.swift
  Sources/IOSDeviceRiskSignals/
    ApplicationInfoProvider.m
    AudioLatencyProvider.m
    DeviceInfoProvider.m
    GpuBenchmarkProvider.m
    GpuExecutionPolicy.m
    LocaleInfoProvider.m
    NetworkInfoProvider.m
    NumericConsistencyProvider.m
    RuntimeTimingProvider.m
    SignalStatistics.m
    TelephonyInfoProvider.m
    include/                      # public headers (SwiftPM convention)
      ApplicationInfoProvider.h
      AudioLatencyProvider.h
      DeviceInfoProvider.h
      GpuBenchmarkProvider.h
      GpuExecutionPolicy.h
      LocaleInfoProvider.h
      NetworkInfoProvider.h
      NumericConsistencyProvider.h
      RuntimeTimingProvider.h
      SignalStatistics.h
      TelephonyInfoProvider.h
  Tests/IOSDeviceRiskSignalsTests/
    ApplicationInfoProviderTests.swift
    AudioLatencyProviderTests.swift
    BooleanBoxingTests.swift
    DeviceInfoProviderTests.swift
    GpuBenchmarkProviderTests.swift
    GpuExecutionPolicyTests.swift
    LocaleInfoProviderTests.swift
    NetworkInfoProviderTests.swift
    ProviderTests.swift
    SignalStatisticsTests.swift
    TelephonyInfoProviderTests.swift
```

The implementation is Objective-C and was moved verbatim from `ios/`, not rewritten, so behaviour is
byte-for-byte identical to what the published binding shipped: the same sample counts, the same
statistics, and the same emitted keys, value types and omission rules. The eleven headers in
`include/` are the whole public surface; implementation details such as the percentile helper, the
sample-count constant and `NetworkInfoProvider`'s reachability, VPN and proxy helpers are `static` or
undeclared inside the `.m` files and are not exported. Tests are Swift and exercise the package only
through that public surface, with two documented exceptions:
`TelephonyInfoProviderTests` reaches the provider's unpublished `-putString:key:value:` sentinel
filter through the Objective-C runtime, and `NetworkInfoProviderTests` reaches
`-vpnActiveInProxySettings:`, `-addProxyInfoFrom:to:` and `-reachabilityConnectionType` the same way,
because widening either header purely to let a test see them would have changed the extracted
source.

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
holds: CoreTelephony is available there, and so are Metal, QuartzCore and UIKit. Verified by building
the package for it, which compiles `TelephonyInfoProvider.m` with `-target arm64-apple-ios15.1-macabi`
and emits only a deprecation warning for the `subscriberCellularProvider` fallback:

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

One consequence of that table worth stating outright: **`TARGET_OS_SIMULATOR` is 0 under Mac
Catalyst**, so a Catalyst build does not take `GpuBenchmarkProvider`'s self-skip branch — it compiles
and runs the real Metal path against the Mac's GPU, and `DeviceInfoProvider` reports
`isMacCatalystApp: true` with the Mac's `hw.machine`. Neither is a problem (Metal on Catalyst is a
supported, full implementation), but a "skips on anything that is not a device" assumption is wrong
there.

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

`NetworkInfoProviderTests` faces the opposite problem to the telephony suite: the interface inventory
*is* genuinely exercised on any host (every machine has a loopback interface and link-local
addresses, so those two dropping rules really run), but the VPN and proxy branches never execute,
because a test host has neither. Rather than leave the rules that decide what gets disclosed
untested, that suite calls `-vpnActiveInProxySettings:` and `-addProxyInfoFrom:to:` directly through
the Objective-C runtime with synthesised system-settings dictionaries. That is what pins the prefix-
not-substring tunnel match, the `enabled && host.length > 0` conjunction that keeps a
disabled-but-remembered proxy from disclosing its host, and the `proxyPort` boxing — none of which a
dictionary-level assertion can reach on an unproxied machine. The dictionary-level counterparts are
still there and become load-bearing the moment the suite is pointed at a host with a VPN or a proxy.

`DeviceInfoProviderTests` has the opposite problem to every suite above it: *everything* it
observes is available on the test host, so every assertion compares the provider against an
independent code path — `sysctlbyname` called directly from Swift for the hardware model and the four
kernel strings, the Swift `UIDevice`/`ProcessInfo` API for the OS fields, and Swift's own
`targetEnvironment(macCatalyst)` for the compile-time conditional. The key-pairing test is the
load-bearing one: `kern.osrelease` and `kern.ostype` are adjacent in the source and produce very
different strings ("25.0.0" and "Darwin"), which a non-empty-string assertion would accept either way
round. The hop gets a test per branch, and the main-thread branch is the test itself.

`GpuBenchmarkProviderTests` deliberately asserts **no timing, no draw-call count and no budget**. The
probe ships disabled, its numbers are a fingerprint rather than a threshold, and every one of them
depends on hardware, thermal state and scheduler luck, so "at least N draw calls" would pin the CI
machine rather than the provider. What is pinned instead is structural: the key vocabulary, the
two-key simulator skip, the rule that a skipped run carries no measurement keys and a completed one
carries no skip reason, the three statistics keys appearing as a group, and — because the provider
indexes `summary[@"median"]`, `summary[@"p95"]`, `summary[@"mad"]` and
`summary[@"coefficientOfVariation"]` *by string* — the vocabulary `RNDISummarize` publishes. A rename
inside `SignalStatistics` would not break the build; it would silently drop four fields from the
payload on hardware that no simulator test could ever see.

`GpuExecutionPolicyTests` is the mirror of `GpuExecutionPolicyTest.kt` and exercises both directions
of the precondition, that it returns on the caller's own thread rather than dispatching, and the test
seam itself — a seam that always returned `nil` would make the accepting direction pass by observing
nothing, the same trap `AudioLatencyProviderTests` guards against for its swizzle.

`DeviceInfoProvider`'s three booleans are deliberately **not** in `BooleanBoxingTests`' subject
table, for the reason that table already states about `carrierAllowsVoip`: it asserts an exact count
of inspected booleans. They are pinned in `DeviceInfoProviderTests` with the same CFBoolean-identity
check and their own exact count, and `benchmarkPerformed` is pinned the same way in
`GpuBenchmarkProviderTests`. `isTablet` is the one genuinely at risk there — it is a C `==`
comparison, whose type is `int`, and only the explicit `(BOOL)` cast makes it a CFBoolean.

`AudioLatencyProviderTests` asserts a *negative*: that reading latency never activates or
reconfigures the shared `AVAudioSession`. `-[AVAudioSession sharedInstance]` is a singleton the
provider reaches directly, with no seam to inject through and none added, so the four mutating
selectors are replaced with counting stubs via `method_setImplementation`. The stubs deliberately do
not forward — the point is to observe a call that must never happen, and forwarding would perform the
side effect under test. A companion test triggers an activation on purpose to prove the spy is
really intercepting, because a swizzle that silently failed would make the no-activation test pass by
observing nothing. Worth knowing if you extend that spy: Swift's `setActive(_:)` bridges to
`setActive:withOptions:error:`, **not** to `setActive:error:` — measured, after the control test was
first written against the obvious selector and failed.

`carrierAllowsVoip` is deliberately absent from `BooleanBoxingTests`' subject table, which asserts an
exact count of inspected booleans — 12, once `NetworkInfoProvider`'s three and
`AudioLatencyProvider`'s one are counted. That shape is correct for providers that emit their
booleans unconditionally and would be wrong the moment a device run made a thirteenth appear, which
is why the telephony flag is pinned in `TelephonyInfoProviderTests` with the same CFBoolean-identity
check instead. `NetworkInfoProvider.proxyPort` is in the table's number column but not in the count
for the same reason — it appears only on a proxied host, so its boxing is pinned in
`NetworkInfoProviderTests` by feeding the private proxy helper a synthesised settings dictionary.

## Native example app

[`example/`](example/) is a development consumer that uses this package and nothing else — no React
Native, no CocoaPods, no third-party dependency. It is a single UIKit app target with one button per
provider it exercises (runtime timing, numeric consistency, locale, application, telephony, audio
latency, network), printing each raw dictionary on screen as JSON. It has no button for
`DeviceInfoProvider` or `GpuBenchmarkProvider` yet — adding them is an example-app change, not part
of the extraction that moved them here. Collection happens only on an explicit
press; nothing runs at launch, nothing is uploaded, and no score or verdict is derived.

It exists to check the integration boundary from the outside: that the headers in `include/` are
reachable from a plain Swift target through `import IOSDeviceRiskSignals`, with no bridging header
and no build-setting help. The Xcode project is hand-written and checked in, because this repository
has no project generator and a seven-button development app is not worth adding one for.

```sh
xcodebuild build \
  -project sdks/ios/example/IOSDeviceRiskSignalsExample.xcodeproj \
  -scheme IOSDeviceRiskSignalsExample \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

Telephony returns an empty dictionary on a simulator and network reports the host Mac's interfaces;
both are documented degradations, explained in the [example README](example/README.md).

## Relationship to the React Native binding

`react-native-device-risk-signals` is a thin adapter. `ios/DeviceIntel.mm` instantiates
`ApplicationInfoProvider`, `AudioLatencyProvider`, `DeviceInfoProvider`, `GpuBenchmarkProvider`,
`LocaleInfoProvider`, `NetworkInfoProvider`, `NumericConsistencyProvider`, `RuntimeTimingProvider`
and `TelephonyInfoProvider` from this package. There is no second copy of the collection logic in
the binding, and this package must never depend on React Native, Flutter, or Capacitor.

The binding satisfies `RNDIRequireWorkerThread` for free: `DeviceIntel` declares its own concurrent
`methodQueue` (ADR-0005), so `-getGpuBenchmark:reject:` never runs on the main thread. A host that
called `-gpuBenchmark` directly from the main thread would now get an exception instead of a
benchmark — that is the point of the precondition, not a regression.

`RnDeviceIntel.podspec` compiles both roots, so its `s.frameworks` list still covers everything this
package links: `AVFoundation`, `SystemConfiguration`, `CFNetwork`, `Metal` and `QuartzCore` are all
listed, and `ios/` still needs `AVFoundation` on its own account for `MediaBluetoothAppsProvider` and
`SecurityPostureProvider`. The podspec needs no edit for this move. Its explanatory comments do still name `NetworkInfoProvider`
and `AudioLatencyProvider` as if they lived in `ios/`; that is stale prose, not a stale build setting.
