// swift-tools-version:5.9

import PackageDescription

// ios-device-risk-signals — the standalone iOS SDK of the Device Risk Signals project.
//
// Platforms:
//   * iOS 15.1 is the supported floor, matching the React Native binding's deployment target.
//   * Mac Catalyst is a declared target platform in `device-risk-signals.json`, so it is declared
//     here too rather than being left to implicit derivation.
//   * macOS is declared ONLY so `swift test` can execute on a developer/CI Mac. Everything in this
//     package so far is pure computation over Foundation (statistics, a monotonic-clock sampling
//     loop and a floating-point/integer consistency probe) with no UIKit or iOS-only framework,
//     so the host-platform test run exercises exactly the code that ships to iOS. It is not a
//     supported consumer platform and must not be advertised as one.
let package = Package(
    name: "ios-device-risk-signals",
    platforms: [
        .iOS("15.1"),
        .macCatalyst("15.1"),
        .macOS(.v12),
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
