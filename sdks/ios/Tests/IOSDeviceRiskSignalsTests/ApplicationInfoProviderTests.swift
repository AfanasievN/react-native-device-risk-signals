import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key vocabulary, value types, omission rules and field relationships of
/// `ApplicationInfoProvider`.
///
/// The React Native binding forwards this dictionary to JavaScript unchanged, so a renamed key, a
/// changed value type or a changed omission rule is a breaking contract change, not an
/// implementation detail.
///
/// **Why some fields cannot be asserted as literals.** `-applicationSignals` reads
/// `+[NSBundle mainBundle]` — the bundle of whatever process is running — and two of its fields are
/// decided at *compile* time by the preprocessor rather than at run time:
///
/// ```objc
/// #if TARGET_OS_SIMULATOR
///   result[@"isSimulatorBuild"] = @YES;
///   result[@"isDebuggable"]     = @YES;
/// #else
///   result[@"isSimulatorBuild"] = @NO;
/// #if DEBUG
///   result[@"isDebuggable"]     = @YES;
/// #else
///   result[@"isDebuggable"]     = @NO;
/// #endif
/// #endif
/// ```
///
/// Values of those two macros **as measured on the Objective-C compile task** for this file in every
/// context it is built in (read off the clang command line, not assumed):
///
/// | Compilation context of `ApplicationInfoProvider.m`   | `TARGET_OS_SIMULATOR` | `DEBUG`   |
/// | ---                                                  | ---                   | ---       |
/// | `swift test`, host (`arm64-apple-macosx12.0`)        | 0                     | 1         |
/// | `xcodebuild test`, `arm64-apple-ios15.1-simulator`   | 1                     | undefined |
/// | CocoaPods `RnDeviceIntel`, Debug                     | 1 simulator / 0 device | 1        |
/// | CocoaPods `RnDeviceIntel`, Release                   | 1 simulator / 0 device | undefined |
///
/// Two consequences drive the tests at the bottom of this file.
///
/// First, a literal `XCTAssertEqual(signals["isSimulatorBuild"] as? NSNumber, false)` would pass
/// under `swift test` and fail under `xcodebuild test` on a simulator — it would pin the destination
/// that ran the suite, not the provider.
///
/// Second, and less obvious: the test target's own `DEBUG` is **not** a proxy for the provider's.
/// Under `xcodebuild` the Swift test target is compiled with `-DDEBUG` (and `-Xcc -DDEBUG=1` for its
/// own clang importer) while the `IOSDeviceRiskSignals` Objective-C target is compiled with only
/// `-DSWIFT_PACKAGE -DXcode` and no `-DDEBUG` at all. A `#if DEBUG` mirror written in Swift would
/// therefore have *passed* on the simulator for the wrong reason — `isDebuggable` is true there
/// because of the `TARGET_OS_SIMULATOR` branch, not because the provider saw `DEBUG`. That is the
/// accidental pass this file deliberately does not ship; see
/// `testIsDebuggableTracksTheDebugMacroWhereThatIsObservable`.
final class ApplicationInfoProviderTests: XCTestCase {
    /// Every key `-applicationSignals` is allowed to emit on iOS. `ApplicationSignals` in
    /// `src/NativeDeviceIntel.ts` is a shared cross-platform type and declares many more; the
    /// install-provenance, signing, split-APK and SDK-version fields are Android-only and iOS must
    /// leave them absent.
    private static let allowedKeys: Set<String> = [
        "appVersion",
        "appBuild",
        "bundleId",
        "bundleExecutable",
        "minimumOsVersion",
        "receiptPresent",
        "receiptEnvironment",
        "isAppExtension",
        "embeddedProvisioningProfilePresent",
        "isSimulatorBuild",
        "isDebuggable",
    ]

    /// The subset that is written on every call, with no `if` in front of it.
    private static let unconditionalKeys: Set<String> = [
        "receiptPresent",
        "isAppExtension",
        "embeddedProvisioningProfilePresent",
        "isSimulatorBuild",
        "isDebuggable",
    ]

    private var signals: [String: Any] {
        ApplicationInfoProvider().applicationSignals() as? [String: Any] ?? [:]
    }

    // MARK: - Key vocabulary

    func testEmitsNothingOutsideTheContractedKeyVocabulary() {
        XCTAssertTrue(
            Set(signals.keys).isSubset(of: Self.allowedKeys),
            "unexpected keys: \(Set(signals.keys).subtracting(Self.allowedKeys).sorted())"
        )
    }

    func testAlwaysEmitsTheFiveUnconditionalFields() {
        XCTAssertTrue(
            Self.unconditionalKeys.isSubset(of: Set(signals.keys)),
            "missing keys: \(Self.unconditionalKeys.subtracting(Set(signals.keys)).sorted())"
        )
    }

    func testAndroidOnlyContractFieldsAreNeverEmittedFromIOS() {
        // A spot check with names, so the failure message says *which* platform's field leaked in
        // rather than only that the vocabulary grew. These are all populated by the Kotlin side.
        let signals = self.signals
        for key in [
            "installerPackage", "installingPackageName", "initiatingPackageName",
            "signingCertificateSha256", "firstInstallTimeMs", "lastUpdateTimeMs",
            "targetSdkVersion", "minSdkVersion", "isSplitApks", "grantedPermissions",
            "isSystemApp", "isInstantApp", "appName",
        ] {
            XCTAssertNil(signals[key], "\(key) is an Android-only field and must stay absent on iOS")
        }
    }

    // MARK: - The deliberately unimplemented contract field

    func testGetTaskAllowEntitlementIsNeverEmitted() {
        // A documented decision in `ApplicationInfoProvider.m`, not an oversight: the `SecTask`
        // entitlement-lookup symbols (`SecTaskCreateFromSelf` / `SecTaskCopyValueForEntitlement`)
        // are not declared by the public iPhoneOS `Security` headers, so reaching them needs
        // hand-written extern declarations — which would cross this package's public-system-API
        // boundary. `ApplicationSignals` in `src/NativeDeviceIntel.ts` therefore carries
        // `getTaskAllowEntitlement?: boolean` marked "Reserved; not populated without a supported
        // public iOS API". Reserved means absent, not `false`: emitting `false` here would assert
        // that the entitlement was checked and found off. This test fails if either ever changes.
        let signals = self.signals
        XCTAssertNil(signals["getTaskAllowEntitlement"])
        XCTAssertFalse(signals.keys.contains("getTaskAllowEntitlement"))
    }

    // MARK: - Bundle identity strings

    func testBundleIdentityStringsMatchTheMainBundleAndAreNeverEmpty() throws {
        // Host-independent only in shape, so the expectation is recomputed from Foundation's modern
        // Swift API (`Bundle.main`) rather than written as a literal — a different path from the
        // provider's `infoDictionary` subscripting, so a swapped key still fails here. The provider
        // drops nil and empty strings instead of emitting "", so each field is checked both ways.
        let signals = self.signals
        let pairs: [(String, String?)] = [
            ("appVersion", Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String),
            ("appBuild", Bundle.main.infoDictionary?["CFBundleVersion"] as? String),
            ("bundleId", Bundle.main.bundleIdentifier),
            ("bundleExecutable", Bundle.main.infoDictionary?["CFBundleExecutable"] as? String),
            ("minimumOsVersion", Bundle.main.infoDictionary?["MinimumOSVersion"] as? String),
        ]
        for (key, expected) in pairs {
            if let expected, !expected.isEmpty {
                XCTAssertEqual(signals[key] as? String, expected, "\(key) must carry the main bundle's value")
            } else {
                XCTAssertNil(signals[key], "\(key) must be omitted when the bundle has no value")
            }
            if let emitted = signals[key] as? String {
                XCTAssertFalse(emitted.isEmpty, "\(key) is dropped rather than emitted empty")
            }
        }
    }

    func testBundleIdComesFromTheBundleIdentifierPropertyNotTheInfoDictionary() {
        // `bundleId` is the one identity string read off the `NSBundle` object rather than out of
        // `infoDictionary`. The two normally agree, but an app extension or a bundle whose
        // Info.plist is rewritten at build time can disagree, and the property is the authoritative
        // one. Pinned so a "tidy up, read them all the same way" refactor is caught.
        XCTAssertEqual(signals["bundleId"] as? String, Bundle.main.bundleIdentifier)
    }

    // MARK: - App Store receipt

    func testReceiptPresentAgreesWithTheAppStoreReceiptURL() throws {
        let expected = Bundle.main.appStoreReceiptURL.map {
            FileManager.default.fileExists(atPath: $0.path)
        } ?? false
        let emitted = try XCTUnwrap(signals["receiptPresent"] as? NSNumber)
        // A URL alone is not enough — `appStoreReceiptURL` is non-nil in plenty of builds that have
        // no receipt file on disk, which is exactly why the provider stats the path.
        XCTAssertEqual(emitted.boolValue, expected)
    }

    func testReceiptEnvironmentIsEmittedOnlyWithAReceiptAndOnlyAsTheTwoTokens() throws {
        let signals = self.signals
        let present = try XCTUnwrap(signals["receiptPresent"] as? NSNumber).boolValue
        if present {
            let environment = try XCTUnwrap(
                signals["receiptEnvironment"] as? String,
                "receiptEnvironment accompanies a present receipt"
            )
            XCTAssertTrue(["sandbox", "production"].contains(environment))
            // The classification rule is a literal filename comparison, not a heuristic: the
            // sandbox receipt is the file named exactly `sandboxReceipt`; everything else, TestFlight
            // included, is reported as production.
            let isSandbox = Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
            XCTAssertEqual(environment, isSandbox ? "sandbox" : "production")
        } else {
            XCTAssertNil(signals["receiptEnvironment"], "no receipt means no environment key")
        }
    }

    // MARK: - Bundle shape

    func testIsAppExtensionAgreesWithTheBundlePathExtension() throws {
        // Case-insensitive `appex` on the bundle *path*, which is why an XCTest bundle
        // (`.xctest`) and a host app (`.app`) both report false.
        let expected = Bundle.main.bundleURL.pathExtension.lowercased() == "appex"
        XCTAssertEqual(try XCTUnwrap(signals["isAppExtension"] as? NSNumber).boolValue, expected)
    }

    func testEmbeddedProvisioningProfilePresentAgreesWithTheFilesystem() throws {
        let path = Bundle.main.bundleURL.appendingPathComponent("embedded.mobileprovision").path
        let expected = FileManager.default.fileExists(atPath: path)
        XCTAssertEqual(
            try XCTUnwrap(signals["embeddedProvisioningProfilePresent"] as? NSNumber).boolValue,
            expected
        )
    }

    // MARK: - Boxing

    func testEveryBooleanFieldBoxesAsACFBooleanAndNotAsAnInt() throws {
        // The trap already documented for `uses24HourClock` in `LocaleInfoProviderTests` and for
        // `signedZeroPreserved` in `NumericConsistencyProviderTests`, checked here in the direction
        // where it currently holds. `@(expr)` picks its `NSNumber` constructor from the *static
        // type* of `expr`: a `BOOL`-typed expression gives `+numberWithBool:` and a CFBoolean,
        // while a C comparison yields `int` and `+numberWithInt:`. Only a CFBoolean crosses the
        // React Native bridge as a JavaScript `true`/`false`; an int reaches JavaScript as 1/0
        // even though `ApplicationSignals` declares these fields `boolean`. Every boolean here is
        // either an `@YES`/`@NO` literal or `@(BOOL-expression)`, so all five must be CFBooleans.
        let signals = self.signals
        for key in Self.unconditionalKeys {
            let number = try XCTUnwrap(signals[key] as? NSNumber, "\(key) must be an NSNumber")
            XCTAssertTrue(
                number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
                "\(key) must box as a CFBoolean so it crosses the bridge as true/false, not 1/0"
            )
        }
    }

    // MARK: - The two compile-time flags

    func testIsSimulatorBuildFollowsTheTargetEnvironmentTheSourceWasCompiledFor() throws {
        // Decided by `TARGET_OS_SIMULATOR` in <TargetConditionals.h>, which is a property of the
        // compilation target triple, not of the running device: it is 1 for
        // `*-apple-ios*-simulator` and 0 for `arm64-apple-macosx` and for a device build. A literal
        // here would pin whichever destination happened to run the suite — false under
        // `swift test` on the host, true under `xcodebuild test` on an iOS Simulator.
        //
        // Swift's `targetEnvironment(simulator)` is the language-level spelling of the same triple
        // property, evaluated when this test target is compiled. The test target and the
        // Objective-C target are always built for one triple per build, so the two agree by
        // construction — and this is a genuinely independent path to the answer (Swift condition vs.
        // C preprocessor macro), not a restatement of the provider's own `#if`.
        #if targetEnvironment(simulator)
        let expected = true
        #else
        let expected = false
        #endif
        XCTAssertEqual(try XCTUnwrap(signals["isSimulatorBuild"] as? NSNumber).boolValue, expected)
    }

    func testASimulatorBuildIsAlwaysReportedDebuggable() throws {
        // The one relationship between the two flags that holds in *every* configuration, including
        // a Release build, because the `#if TARGET_OS_SIMULATOR` branch sets both to `@YES` with no
        // inner `#if DEBUG`. Asserting it as an implication rather than as values keeps it true on
        // the host, on a simulator, and on a device. Splitting `isDebuggable` out of that branch —
        // so a Release simulator build would report `isSimulatorBuild: true, isDebuggable: false` —
        // is a behavior change, and this test is what catches it.
        let signals = self.signals
        let simulator = try XCTUnwrap(signals["isSimulatorBuild"] as? NSNumber).boolValue
        let debuggable = try XCTUnwrap(signals["isDebuggable"] as? NSNumber).boolValue
        if simulator {
            XCTAssertTrue(debuggable, "the simulator branch sets isDebuggable unconditionally")
        }
    }

    func testIsDebuggableTracksTheDebugMacroWhereThatIsObservable() throws {
        // The other half of `isDebuggable`, decided by `DEBUG` — which, unlike `TARGET_OS_SIMULATOR`,
        // no header defines. It exists only if some `-D` on the clang command line puts it there, so
        // its value is a property of the build system's configuration handling, and Swift has no way
        // to read the macro the *Objective-C* target was compiled with.
        //
        // What can be done honestly is to assert it only where the two halves are known to move
        // together, and to say so out loud everywhere else:
        //
        //   * `swift test` — SwiftPM derives both from one `-c debug|release`: the clang task for
        //     this provider gets `-DDEBUG=1` and swiftc gets `-DDEBUG` in debug, and neither gets
        //     anything in release. The mirror below is sound, and `swift test -c release` exercises
        //     the false branch rather than leaving it theoretical.
        //   * `xcodebuild` — NOT sound, and this is the trap. Measured on the Debug/iphonesimulator
        //     build of this package, the `IOSDeviceRiskSignals` Objective-C target is compiled with
        //     `-DSWIFT_PACKAGE -DXcode` and no `-DDEBUG`, while the Swift test target *is* compiled
        //     with `-DDEBUG`. Mirroring the Swift flag there would assert `isDebuggable == true` and
        //     pass — but only because the simulator branch already forced it true, never because the
        //     provider saw `DEBUG`. On an `xcodebuild` device destination the same mirror would
        //     assert true against an actual false. So the Xcode build system is skipped here
        //     explicitly, not silently mirrored. `Xcode` is an active compilation condition only
        //     under xcodebuild; SwiftPM passes `-DDEBUG -DSWIFT_PACKAGE` and no `-DXcode`.
        //
        // The part of `isDebuggable` that no build system can change is pinned unconditionally by
        // `testASimulatorBuildIsAlwaysReportedDebuggable` above.
        let signals = self.signals
        let debuggable = try XCTUnwrap(signals["isDebuggable"] as? NSNumber).boolValue
        try XCTSkipIf(
            try XCTUnwrap(signals["isSimulatorBuild"] as? NSNumber).boolValue,
            "the simulator branch forces isDebuggable true regardless of DEBUG — pinned separately"
        )

        #if Xcode
        throw XCTSkip(
            "xcodebuild compiles the Objective-C target without -DDEBUG even in Debug, so the test "
                + "target's own DEBUG says nothing about the provider's"
        )
        #elseif DEBUG
        XCTAssertTrue(debuggable, "SwiftPM debug builds compile this provider with -DDEBUG=1")
        #else
        XCTAssertFalse(debuggable, "SwiftPM release builds pass no -DDEBUG, and this is not a simulator build")
        #endif
    }

    // MARK: - Stability

    func testRepeatedCallsAgreeOnEveryField() throws {
        let first = try XCTUnwrap(ApplicationInfoProvider().applicationSignals() as NSDictionary?)
        let second = try XCTUnwrap(ApplicationInfoProvider().applicationSignals() as NSDictionary?)
        XCTAssertEqual(first, second)
    }
}
