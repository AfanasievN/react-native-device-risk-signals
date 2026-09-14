import Darwin
import UIKit
import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key vocabulary, value types, omission rules and main-thread hop of
/// `DeviceInfoProvider`.
///
/// **Why this suite is as strict as the network one, for a different reason.** `device_identity` is
/// *enabled by default* and carries the tightest budget in the repository — 200 ms — and every field
/// it emits is declared non-optional or conditionally-optional in `DeviceIdentity`
/// (`src/NativeDeviceIntel.ts`), so a consumer indexes `manufacturer`, `model`, `brand`,
/// `systemName`, `systemVersion` and `isTablet` without checking. A renamed key or a changed box is
/// a breaking contract change for the most widely enabled probe in the SDK, not an implementation
/// detail. Nothing here is sensitive — these are permission-free reads of the device's own model and
/// OS strings, with no identifier among them — so the strictness is about contract stability.
///
/// **The main-thread hop is part of the extracted behaviour, deliberately.** `UIDevice` is declared
/// `NS_SWIFT_UI_ACTOR` in the SDK and none of `systemName`, `systemVersion` or
/// `userInterfaceIdiom` carries an `NS_SWIFT_NONISOLATED` exemption, so Apple isolates all three to
/// the main actor; a previous commit added the `dispatch_sync` hop for exactly that reason.
/// ADR-0005 plans to relocate hops of this kind into the binding, but that is a separate slice, so
/// the provider moved with the hop intact and this suite pins both of its branches — including the
/// `[NSThread isMainThread]` check, whose absence would deadlock any main-thread caller instantly.
///
/// **What the host can exercise.** Everything. Unlike telephony or VPN detection, every read here
/// resolves on a simulator, so each assertion compares the provider's value against an independent
/// code path: `sysctlbyname` called directly from Swift for the kernel strings and the hardware
/// model, Foundation's `UIDevice`/`ProcessInfo` Swift API for the rest, and Swift's own
/// `targetEnvironment(macCatalyst)` for the compile-time conditional.
final class DeviceInfoProviderTests: XCTestCase {
    /// Every key `-deviceIdentity` is allowed to emit. `DeviceIdentity` is shared with Android and
    /// declares `androidBuild` as well, which must stay absent here.
    private static let allowedKeys: Set<String> = [
        "manufacturer",
        "model",
        "brand",
        "systemName",
        "systemVersion",
        "isTablet",
        "isMacCatalystApp",
        "isIosAppOnMac",
        "osBuild",
        "kernelVersion",
        "kernelOsRelease",
        "kernelOsType",
    ]

    /// The keys written on every path, independent of host, SDK and destination.
    private static let unconditionalKeys: Set<String> = [
        "manufacturer",
        "model",
        "brand",
        "systemName",
        "systemVersion",
        "isTablet",
        "isMacCatalystApp",
    ]

    /// The four `sysctl` reads, paired with the key each is written under.
    private static let sysctlKeys: [String: String] = [
        "osBuild": "kern.osversion",
        "kernelVersion": "kern.version",
        "kernelOsRelease": "kern.osrelease",
        "kernelOsType": "kern.ostype",
    ]

    private static let booleanKeys: Set<String> = ["isTablet", "isMacCatalystApp", "isIosAppOnMac"]

    private var signals: [String: Any] {
        DeviceInfoProvider().deviceIdentity() as? [String: Any] ?? [:]
    }

    /// An independent `sysctlbyname` read, so a swapped key name in the provider fails rather than
    /// being compared against itself.
    private func sysctlString(_ name: String) -> String? {
        var size = 0
        guard sysctlbyname(name, nil, &size, nil, 0) == 0, size > 0 else { return nil }
        var buffer = [CChar](repeating: 0, count: size)
        guard sysctlbyname(name, &buffer, &size, nil, 0) == 0 else { return nil }
        return String(cString: buffer)
    }

    // MARK: - Key vocabulary

    func testEmitsNothingOutsideTheContractedKeyVocabulary() {
        let keys = Set(signals.keys)
        XCTAssertTrue(
            keys.isSubset(of: Self.allowedKeys),
            "unexpected keys: \(keys.subtracting(Self.allowedKeys).sorted())"
        )
    }

    func testEveryUnconditionalKeyIsPresent() {
        let keys = Set(signals.keys)
        XCTAssertTrue(
            Self.unconditionalKeys.isSubset(of: keys),
            "missing: \(Self.unconditionalKeys.subtracting(keys).sorted())"
        )
    }

    func testAndroidOnlyContractFieldsAreNeverEmittedFromIOS() {
        // `androidBuild` is a nested `AndroidBuildInfo` populated by the Kotlin side. iOS has no
        // counterpart and must not invent one.
        XCTAssertNil(signals["androidBuild"], "androidBuild is Android-only and omitted on iOS")
    }

    func testNoRequiredReasonSysctlIsRead() throws {
        // `kern.boottime` is the one sysctl key in this family that *is* an Apple Required-Reason
        // API (system boot time, DDA9.1), and the binding's `PrivacyInfo.xcprivacy` declares zero
        // Required-Reason APIs. The provider reads the other four and deliberately not that one, so
        // no value derived from it can appear under any key. Checked by value, because a rename
        // would not be caught by a key-name test.
        let boottime = try XCTUnwrap(sysctlString("kern.osrelease"))
        XCTAssertFalse(boottime.isEmpty)
        for key in ["bootTime", "bootTimeMs", "systemUptimeMs", "uptimeMs"] {
            XCTAssertNil(signals[key], "\(key) would imply a kern.boottime read, which is a Required-Reason API")
        }
    }

    // MARK: - The constant fields

    func testManufacturerAndBrandAreTheAppleConstants() {
        // Both are hard-coded string literals rather than reads, and Android fills the same two
        // fields from `Build.MANUFACTURER`/`Build.BRAND`. They exist so a cross-platform consumer
        // can read one field on both platforms.
        XCTAssertEqual(signals["manufacturer"] as? String, "Apple")
        XCTAssertEqual(signals["brand"] as? String, "Apple")
    }

    func testModelIsTheHardwareMachineIdentifierAndNotTheMarketingName() throws {
        // `hw.machine` — "iPhone17,1", not "iPhone 16 Pro" and not `UIDevice.model`, which returns
        // the useless "iPhone"/"iPad" class name. On a simulator it is the *host* architecture
        // ("arm64"/"x86_64"), which is itself the documented answer there rather than a bug.
        let model = try XCTUnwrap(signals["model"] as? String, "model is non-optional in the contract")
        XCTAssertEqual(model, sysctlString("hw.machine"))
        XCTAssertFalse(model.isEmpty)
        XCTAssertNotEqual(model, UIDevice.current.model, "model is hw.machine, not UIDevice.model")
    }

    func testModelIsAnEmptyStringRatherThanAbsentWhenTheSysctlFails() throws {
        // `-hardwareModel` is `[self sysctlString:"hw.machine"] ?: @""`, and the result is assigned
        // without going through `-putString:key:value:`, so `model` is present even on the
        // unreachable failure path. The contract declares it non-optional, so absence would be the
        // wrong answer; the empty string is the chosen one.
        XCTAssertNotNil(signals["model"])
        XCTAssertTrue(signals["model"] is String)
    }

    // MARK: - The UIDevice reads, which happen inside the hop

    func testSystemNameAndVersionMatchUIDevice() throws {
        // Compared against the Swift `UIDevice` API from the main thread, a different call path
        // from the provider's Objective-C property reads inside its dispatched block.
        let signals = self.signals
        XCTAssertEqual(signals["systemName"] as? String, UIDevice.current.systemName)
        XCTAssertEqual(signals["systemVersion"] as? String, UIDevice.current.systemVersion)
        XCTAssertFalse(try XCTUnwrap(signals["systemName"] as? String).isEmpty)
        XCTAssertFalse(try XCTUnwrap(signals["systemVersion"] as? String).isEmpty)
    }

    func testIsTabletIsTheIdiomComparisonAndNotAModelStringTest() {
        // `device.userInterfaceIdiom == UIUserInterfaceIdiomPad`. Note that this is an *idiom*
        // question, not a hardware one: an iPhone-idiom app running on an iPad reports `false`,
        // which is the intended answer for a signal about the UI the user is looking at.
        XCTAssertEqual(
            (signals["isTablet"] as? NSNumber)?.boolValue,
            UIDevice.current.userInterfaceIdiom == .pad
        )
    }

    // MARK: - The two Mac conditionals

    func testIsMacCatalystAppMirrorsTheCompileTimeEnvironment() throws {
        // `#if TARGET_OS_MACCATALYST` in Objective-C, mirrored through Swift's independent
        // `targetEnvironment(macCatalyst)`. Both branches assign — the key is never absent — so a
        // Catalyst build reports `true` and every other destination reports `false`.
        let value = try XCTUnwrap(signals["isMacCatalystApp"] as? NSNumber).boolValue
        #if targetEnvironment(macCatalyst)
            XCTAssertTrue(value, "this destination is Mac Catalyst")
        #else
            XCTAssertFalse(value, "this destination is not Mac Catalyst")
        #endif
    }

    func testIsIosAppOnMacIsTheRuntimeAnswerAndIsDistinctFromCatalyst() throws {
        // The two Mac fields answer different questions and must not be conflated: Catalyst is a
        // compile-time environment, while `isiOSAppOnMac` is an *unmodified iOS binary* running on
        // Apple silicon macOS, answered at run time. `respondsToSelector:` guards a selector that
        // exists on every supported OS (iOS 14+ against a 15.1 floor), so the key is present here
        // and the guard is belt-and-braces for an older host SDK.
        let signals = self.signals
        let value = try XCTUnwrap(signals["isIosAppOnMac"] as? NSNumber).boolValue
        XCTAssertEqual(value, ProcessInfo.processInfo.isiOSAppOnMac)
        if value {
            XCTAssertEqual(
                (signals["isMacCatalystApp"] as? NSNumber)?.boolValue, false,
                "an iOS app on Mac is by definition not a Catalyst build"
            )
        }
    }

    // MARK: - The sysctl fingerprint

    func testEverySysctlFieldMatchesAnIndependentReadOfItsOwnKey() throws {
        // Four separate reads, each compared against the same `sysctlbyname` key from Swift. This is
        // what fails if two of the keys are ever swapped — `kern.osrelease` and `kern.ostype` are
        // adjacent in the source and produce very different strings ("25.0.0" vs "Darwin"), which a
        // non-empty-string test would happily accept either way round.
        let signals = self.signals
        for (key, name) in Self.sysctlKeys.sorted(by: { $0.key < $1.key }) {
            let expected = try XCTUnwrap(sysctlString(name), "\(name) is readable on every Darwin host")
            XCTAssertEqual(signals[key] as? String, expected, "\(key) must be \(name)")
        }
    }

    func testKernelOsTypeIsDarwin() {
        // A literal that holds on every destination this package supports, simulator and device
        // alike, and the cheapest possible guard on the key pairing above.
        XCTAssertEqual(signals["kernelOsType"] as? String, "Darwin")
    }

    func testEmptySysctlResultsAreOmittedRatherThanEmitted() {
        // `-putString:key:value:` writes only `if (value.length > 0)`, so a failed or empty read
        // yields an absent optional field rather than an empty string. All four are declared
        // optional in `DeviceIdentity`, so absent is representable; `""` would be a claim that the
        // kernel reported nothing, which is different from not having asked.
        let signals = self.signals
        for key in Self.sysctlKeys.keys.sorted() {
            guard let value = signals[key] else { continue }
            XCTAssertFalse(
                try! XCTUnwrap(value as? String).isEmpty,
                "\(key) is omitted when empty, never emitted as an empty string"
            )
        }
    }

    // MARK: - The main-thread hop, both branches

    func testCallingOnTheMainThreadDoesNotDeadlock() {
        // The whole point of the `if ([NSThread isMainThread])` branch. `dispatch_sync` onto the
        // main queue *from* the main queue deadlocks the process immediately and permanently, so a
        // "simplification" that dropped the check would not fail this test — it would hang the whole
        // suite here. XCTest runs test methods on the main thread, so this test is that call.
        XCTAssertTrue(Thread.isMainThread, "XCTest runs test methods on the main thread")
        let signals = self.signals
        XCTAssertFalse(signals.isEmpty)
        XCTAssertNotNil(signals["systemName"], "the UIDevice reads ran on the direct branch")
    }

    func testCallingOffTheMainThreadReturnsTheSameFieldsThroughTheHop() {
        // The dispatched branch, which is how `ios/DeviceIntel.mm` really calls it: the module owns
        // a concurrent `methodQueue`, so `-deviceIdentity` runs on a worker thread and the three
        // `UIDevice` reads hop to the main queue and back. A timeout here means the hop stopped
        // completing — ADR-0005 notes this probe pays a full main-thread work item on a busy host,
        // which is why the budget is worth watching but is not asserted as a timing here.
        var offMain: [String: Any] = [:]
        var wasMainThread = true
        let done = expectation(description: "deviceIdentity on a worker thread")
        DispatchQueue.global(qos: .userInitiated).async {
            wasMainThread = Thread.isMainThread
            offMain = DeviceInfoProvider().deviceIdentity() as? [String: Any] ?? [:]
            done.fulfill()
        }
        wait(for: [done], timeout: 10)

        XCTAssertFalse(wasMainThread, "the collection really happened off the main thread")
        XCTAssertEqual(Set(offMain.keys), Set(signals.keys), "the hop must not change the key set")
        XCTAssertEqual(offMain["systemName"] as? String, signals["systemName"] as? String)
        XCTAssertEqual(offMain["systemVersion"] as? String, signals["systemVersion"] as? String)
        XCTAssertEqual(
            (offMain["isTablet"] as? NSNumber)?.boolValue,
            (signals["isTablet"] as? NSNumber)?.boolValue
        )
    }

    func testConcurrentCallsFromSeveralWorkerThreadsAllComplete() {
        // ADR-0005 made the module's queue concurrent, so provider methods can now be entered
        // concurrently. This provider holds no process-global state — unlike `HardwareInfoProvider`
        // and its battery-monitoring toggle — so overlapping callers must simply all get an answer.
        // Four concurrent hops onto one main queue is also the cheapest reproduction of a hop that
        // serializes badly.
        let done = expectation(description: "four concurrent collections")
        done.expectedFulfillmentCount = 4
        let lock = NSLock()
        var keySets: [Set<String>] = []
        for _ in 0..<4 {
            DispatchQueue.global(qos: .userInitiated).async {
                let keys = Set((DeviceInfoProvider().deviceIdentity() as? [String: Any] ?? [:]).keys)
                lock.lock()
                keySets.append(keys)
                lock.unlock()
                done.fulfill()
            }
        }
        wait(for: [done], timeout: 20)
        XCTAssertEqual(keySets.count, 4)
        for keys in keySets {
            XCTAssertEqual(keys, Set(signals.keys))
        }
    }

    // MARK: - Value types and boxing

    func testEveryBooleanBoxesAsACFBoolean() throws {
        // `isTablet` is the one at risk: it is a C `==` comparison, whose type is `int`, and only
        // the explicit `(BOOL)` cast in `@((BOOL)(device.userInterfaceIdiom == UIUserInterfaceIdiomPad))`
        // makes it a CFBoolean. `@YES`/`@NO` and `@(processInfo.isiOSAppOnMac)` are correct by
        // construction and are checked so the set is complete. CFBoolean identity is the only check
        // that separates the two boxes — `boolValue` and `isEqual:` agree on both — and only a
        // CFBoolean crosses the React Native bridge as a JavaScript `true`/`false`, which is what
        // `contract/raw-signal-event.schema.json` declares for all three.
        //
        // These three are deliberately *not* in `BooleanBoxingTests`' subject table: that table
        // asserts an exact count of inspected booleans, and this provider is pinned here instead,
        // with the same CFBoolean-identity check.
        let signals = self.signals
        var checked = 0
        for key in Self.booleanKeys.sorted() {
            guard let value = signals[key] else { continue }
            let number = try XCTUnwrap(value as? NSNumber, "\(key) must be an NSNumber")
            XCTAssertTrue(
                number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
                "\(key) boxed as objCType \"\(String(cString: number.objCType))\" "
                    + "(\(NSStringFromClass(type(of: number)))). It must be a CFBoolean, or it "
                    + "crosses the bridge as 1/0 and fails schema validation."
            )
            XCTAssertEqual(String(cString: number.objCType), "c", key)
            checked += 1
        }
        XCTAssertEqual(checked, 3, "every boolean this provider emits must have been inspected")
    }

    func testEveryEmittedValueHasItsContractedType() {
        let signals = self.signals
        for key in ["manufacturer", "model", "brand", "systemName", "systemVersion"] {
            XCTAssertTrue(signals[key] is String, "\(key) must be a String")
        }
        for key in Self.sysctlKeys.keys where signals[key] != nil {
            XCTAssertTrue(signals[key] is String, "\(key) must be a String")
        }
        for key in Self.booleanKeys where signals[key] != nil {
            XCTAssertTrue(signals[key] is NSNumber, "\(key) must be an NSNumber")
        }
    }

    func testNoStringFieldIsEverEmptyOrNull() throws {
        // Every string this provider emits is either a constant, a guarded sysctl read, or a
        // `UIDevice` property that Apple never returns empty. A consumer of the most widely enabled
        // probe in the SDK should never receive `""` where it expects an identifier-shaped string.
        for (key, value) in signals {
            guard let string = value as? String else { continue }
            XCTAssertFalse(string.isEmpty, "\(key) must be omitted rather than emitted empty")
        }
    }

    // MARK: - Stability

    func testRepeatedCallsAgree() {
        // Nothing here can legitimately change between two calls within a collection run: the model,
        // the OS strings and the kernel strings are fixed for the process's lifetime. Unlike the
        // network provider, this one can assert full equality.
        let first = DeviceInfoProvider().deviceIdentity() as? [String: Any] ?? [:]
        let second = DeviceInfoProvider().deviceIdentity() as? [String: Any] ?? [:]
        XCTAssertEqual(first as NSDictionary, second as NSDictionary)
    }
}
