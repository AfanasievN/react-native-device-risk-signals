# Native iOS SDK example

This development app consumes `ios-device-risk-signals` directly, as a local Swift package
reference to `..`. It has no React Native dependency, no CocoaPods, and no third-party dependency at
all. This example uses the unreleased sixteen-method source. Published 0.1.0 has nine collectors;
see the [package README](../README.md) for the distinction.

The app exposes sixteen collection buttons via DeviceRiskSignals, including the newly extracted
hardware, fonts, socket-free integrity, geolocation, media, security and transaction methods.
Collection starts only when a button is pressed; nothing runs on launch. Results are local raw
observations printed on screen as JSON. The app does not upload them, store them, or calculate a
risk score or verdict, and the screen says so.

Fonts, GPU, native timing and numeric workloads run on a worker. UIKit-bound collectors run on
main. Sensitive collection requires a separate explicit tap; no permission prompt or location
update is requested. App visibility is limited by host Info.plist declarations (none are added).

On a simulator two of these buttons are expected to look empty or degraded, and that is the
documented behaviour rather than a fault:

- **Telephony** returns `{}`. The CoreTelephony reads sit inside `#if !TARGET_OS_SIMULATOR`, so they
  are not in the binary. Even on a device, iOS 16+ nulls `CTCarrier` for non-carrier apps and the
  provider drops the `--` / `65535` sentinels rather than forwarding them.
- **Network** reports the *host Mac's* interface inventory, because a simulator shares the host's
  network stack. `utun*` interfaces appear in `interfaceNames` while `isVpnActive` stays `false` —
  the provider classifies VPN presence from scoped system-proxy entries, not from interface names.

## Using the SDK

The providers are Objective-C. SwiftPM exports the public headers in
`Sources/IOSDeviceRiskSignals/include/` as a clang module named after the SwiftPM target, so a Swift
consumer needs exactly one import and no bridging header, no `module.modulemap` of its own, and no
`-import-objc-header` flag:

```swift
import IOSDeviceRiskSignals

let locale = LocaleInfoProvider().localeSignals()   // NSDictionary
let timing = RuntimeTimingProvider().runtimeTimingSignals()
```

The DeviceRiskSignals facade and individual providers return raw dictionaries.
Calls are synchronous; fonts and GPU are expensive and should stay off-main. None adds a permission
prompt, a network request, or an automatic collection schedule. Missing observations are absent
keys, never placeholder values — `AudioLatencyProvider` is the one that says so explicitly, with
`measured`.

The returned dictionaries hold only `NSString`, `NSNumber` and `NSArray<NSString *>` values, so
`JSONSerialization` accepts them as-is; that is what this app does to render them. Host applications
own any event envelope, serialization, consent policy, storage, and transport.

## Build

```sh
xcodebuild build \
  -project sdks/ios/example/IOSDeviceRiskSignalsExample.xcodeproj \
  -scheme IOSDeviceRiskSignalsExample \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
```

Run it on a booted simulator with the product from that build:

```sh
xcrun simctl install booted <DerivedData>/Build/Products/Debug-iphonesimulator/IOSDeviceRiskSignalsExample.app
xcrun simctl launch booted io.github.afanasievn.devicerisksignals.example
```

The Xcode project is **checked in and hand-written** rather than generated. Neither `xcodegen` nor
`tuist` is a dependency of this repository, and adding a project generator to build this
development consumer would have been a new toolchain requirement for everyone. The project is kept
as small as that decision allows: one application target, two Swift files, no asset catalog, no
storyboard, no `Info.plist` (`GENERATE_INFOPLIST_FILE = YES`), and one
`XCLocalSwiftPackageReference` with `relativePath = ..`. Code signing is disabled
(`CODE_SIGNING_ALLOWED = NO`), which is enough for the simulator and keeps the build free of a team
identifier. Only the scheme under `xcshareddata` is checked in; per-user `xcuserdata` is ignored.

Building the consumer checks the native integration boundary: it proves the package's public headers
really are reachable from a plain Swift app target with no bridging configuration. It does not
replace physical-device validation, which remains necessary before publishing the SDK or changing
collection defaults — in particular for telephony, whose fields no simulator can produce.
