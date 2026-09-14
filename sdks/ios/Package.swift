// swift-tools-version:5.9

import PackageDescription

// ios-device-risk-signals — the standalone iOS SDK of the Device Risk Signals project.
//
// Platforms:
//   * iOS 15.1 is the supported floor, matching the React Native binding's deployment target.
//   * Mac Catalyst is a declared target platform in `device-risk-signals.json`, so it is declared
//     here too rather than being left to implicit derivation.
//   * macOS is NO LONGER declared, and this package no longer builds for the host Mac at all.
//     `TelephonyInfoProvider` imports CoreTelephony, whose entire API surface is annotated
//     `API_UNAVAILABLE(macos)`: on an `*-apple-macos*` triple `CTTelephonyNetworkInfo`,
//     `CTCarrier`, `serviceSubscriberCellularProviders`, `subscriberCellularProvider` and
//     `allowsVOIP` are hard compile errors, not empty values at run time.
//
//     Note that removing the platform is what makes that honest, not what causes it: SwiftPM
//     always builds for the host regardless of this list, so `swift build` / `swift test` still
//     attempt an `arm64-apple-macosx` compile and now fail with 11 unavailability errors followed
//     by `error: fatalError`. That failure is expected and is not a regression — the iOS Simulator
//     destination below is the supported way to run the suite. macOS was only ever a
//     test-execution convenience, valid while every extracted provider was pure Foundation.
//
//     The rejected alternative was to keep macOS by hiding this provider behind a compile-time
//     guard. That was not acceptable: the host suite would then have compiled a stub and proved
//     nothing about the code that actually ships.
//
//     Mac Catalyst is unaffected — CoreTelephony *is* available there, verified by compiling this
//     provider for `arm64-apple-ios15.1-macabi`, which produces only a deprecation warning for the
//     `subscriberCellularProvider` fallback.
//
//     Tests now run on the iOS Simulator, and on a physical device for the fields that need real
//     carrier hardware (see TelephonyInfoProviderTests):
//
//       xcodebuild test -scheme ios-device-risk-signals \
//         -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
let package = Package(
    name: "ios-device-risk-signals",
    platforms: [
        .iOS("15.1"),
        .macCatalyst("15.1"),
    ],
    products: [
        .library(
            name: "IOSDeviceRiskSignals",
            targets: ["IOSDeviceRiskSignals"]
        )
    ],
    targets: [
        // The moved Objective-C implementation. Public headers live in `include/` per the SwiftPM
        // convention; implementation-only helpers stay `static` inside the `.m` files.
        .target(
            name: "IOSDeviceRiskSignals",
            path: "Sources/IOSDeviceRiskSignals"
        ),
        .testTarget(
            name: "IOSDeviceRiskSignalsTests",
            dependencies: ["IOSDeviceRiskSignals"],
            path: "Tests/IOSDeviceRiskSignalsTests"
        ),
    ]
)
