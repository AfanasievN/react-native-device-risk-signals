import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key vocabulary, value types, sentinel filtering and omission rules of
/// `TelephonyInfoProvider`.
///
/// The React Native binding forwards this dictionary to JavaScript unchanged, so a renamed key, a
/// changed value type or a changed omission rule is a breaking contract change.
///
/// **Read this before adding an assertion here.** This is the first provider in the package whose
/// observations cannot be produced by the machine that runs the suite, for two independent reasons:
///
/// 1. **The reads are compiled out on a simulator.** Both CoreTelephony accessors sit inside
///    `#if !TARGET_OS_SIMULATOR`, so on an `*-apple-ios*-simulator` triple `providers` and `carrier`
///    stay `nil` and `-telephonySignals` returns an *empty* dictionary. Nothing is nulled at run
///    time; the calls are not in the binary at all. (The `CTTelephonyNetworkInfo` *construction* is
///    outside the guard and does still run, which is why a simulator run of this suite prints a
///    burst of `com.apple.commcenter.coretelephony.xpc ... No such process` log lines from
///    CoreTelephony itself. That noise is expected on a simulator and is not a test failure.)
/// 2. **Even on a device the fields are expected-absent.** Apple has progressively nulled `CTCarrier`
///    for non-carrier apps since iOS 16: `carrierName` becomes `"--"` and MCC/MNC become `"65535"`,
///    which this provider drops as sentinels. A device run on iOS 16+ therefore also produces a
///    nearly empty dictionary — by design, not by failure.
///
/// So almost every value-level assertion would be vacuous here. The suite is written around that
/// rather than pretending otherwise:
///
/// * Assertions that hold in **every** context — the key vocabulary, the `imei` omission, the
///   "no sentinel ever reaches the dictionary" rule, the co-emission rule, and the type/boxing rules
///   applied *conditionally on a key being present* — are unconditional tests.
/// * The sentinel filter itself is exercised **directly**, by calling the provider's own
///   `-putString:key:value:` helper through the Objective-C runtime (see
///   `testSentinelAndEmptyCarrierStringsAreDroppedRatherThanEmitted`). That is the one piece of real
///   behaviour reachable without a SIM, and it is the piece most likely to be "tidied up" by a later
///   refactor.
/// * The claim that a simulator build emits nothing at all is asserted only under
///   `#if targetEnvironment(simulator)`, and the host/device cases say so instead of asserting.
///
/// **What a physical-device run adds, and only a physical-device run.** On a device with an active
/// SIM the `if (carrier != nil)` branch executes for the first time, which is the only way to
/// observe: `simCount` actually being written, `carrierAllowsVoip` actually being boxed, the four
/// carrier strings carrying real values, and the sentinel filter running against values CoreTelephony
/// really produced rather than against literals this file passes in. Every test below that depends on
/// that branch is written as "if the key is present, it must look like *this*", so it is silent on a
/// simulator and load-bearing on a device — none of them is skipped, because a skip would hide the
/// fact that they do run, and pass meaningfully, the moment the suite is pointed at hardware.
///
/// `carrierAllowsVoip` is deliberately **not** added to `BooleanBoxingTests`' subject table: that
/// sweep asserts an exact count of inspected booleans (`checked == 8`), which is correct for
/// providers that emit their booleans unconditionally and would become wrong the moment a device run
/// made a ninth boolean appear. Its boxing is pinned here instead, with the same CFBoolean-identity
/// check.
final class TelephonyInfoProviderTests: XCTestCase {
    /// Every key `-telephonySignals` is allowed to emit on iOS. `TelephonySignals` in
    /// `src/NativeDeviceIntel.ts` is a shared cross-platform type and declares many more; the rest
    /// are populated by the Kotlin side and iOS must leave them absent.
    private static let allowedKeys: Set<String> = [
        "simCount",
        "networkOperatorName",
        "carrierMobileCountryCode",
        "carrierMobileNetworkCode",
        "networkCountryIso",
        "carrierAllowsVoip",
    ]

    /// The four keys written through `-putString:key:value:` inside the `carrier != nil` branch.
    private static let carrierStringKeys: Set<String> = [
        "networkOperatorName",
        "carrierMobileCountryCode",
        "carrierMobileNetworkCode",
        "networkCountryIso",
    ]

    /// The values CoreTelephony substitutes for a carrier it will not disclose. Kept as a named
    /// constant so the filter test and the dictionary test cannot drift apart.
    private static let sentinels = ["--", "65535"]

    private var signals: [String: Any] {
        TelephonyInfoProvider().telephonySignals() as? [String: Any] ?? [:]
    }

    // MARK: - Key vocabulary

    func testEmitsNothingOutsideTheContractedKeyVocabulary() {
        XCTAssertTrue(
            Set(signals.keys).isSubset(of: Self.allowedKeys),
            "unexpected keys: \(Set(signals.keys).subtracting(Self.allowedKeys).sorted())"
        )
    }

    func testAndroidOnlyContractFieldsAreNeverEmittedFromIOS() {
        // A spot check with names, so the failure message says *which* platform's field leaked in.
        // All of these are `TelephonySignals` members populated by the Kotlin implementation from
        // `TelephonyManager`; iOS has no public equivalent for any of them.
        let signals = self.signals
        for key in [
            "phoneType", "simOperatorName", "simCountryIso", "simState",
            "hasIccCard", "isNetworkRoaming", "dataState",
        ] {
            XCTAssertNil(signals[key], "\(key) is an Android-only field and must stay absent on iOS")
        }
    }

    // MARK: - The deliberately unimplemented contract field

    func testImeiIsNeverEmitted() {
        // A documented decision in `TelephonyInfoProvider.m`, not an oversight. iOS has exposed no
        // public API for the IMEI since iOS 5; `TelephonySignals` in `src/NativeDeviceIntel.ts`
        // carries `imei?: string` commented "Expected null — present in the contract only to make
        // the absence explicit", and the provider's closing comment says the same. Absent means
        // absent: emitting `""` or `"unavailable"` would be a value where the contract promises
        // nothing, and either would also be a persistent-device-identifier field appearing in a
        // package whose whole premise is that it creates none. This test fails if that ever changes.
        let signals = self.signals
        XCTAssertNil(signals["imei"])
        XCTAssertFalse(signals.keys.contains("imei"))
    }

    // MARK: - Sentinel filtering

    /// The provider's private string-writing helper, reached through the Objective-C runtime.
    ///
    /// `-putString:key:value:` is not declared in `TelephonyInfoProvider.h`, so Swift cannot see it
    /// even with `@testable import` — `@testable` widens Swift's own access control and does nothing
    /// for an Objective-C method that no header declares. Declaring the selector in an `@objc`
    /// protocol and casting to it is the standard way to reach one: dispatch is by selector at run
    /// time, so this calls the real implementation in the real `.m` file, not a copy of its logic.
    ///
    /// The alternative — moving the helper into the header purely so a test could see it — would
    /// have widened the package's public surface to suit the test, and the extraction commit that
    /// introduced this file moved the provider byte-for-byte unchanged.
    @objc private protocol CarrierStringWriting {
        @objc(putString:key:value:)
        func putString(_ dictionary: NSMutableDictionary, key: String, value: String?)
    }

    func testTheSentinelFilterIsReachableUnderTheSelectorThisSuiteCallsIt() {
        // Guards the mechanism above rather than the behaviour: `unsafeBitCast` to an `@objc`
        // protocol does not check conformance, so a renamed or removed helper would otherwise
        // surface as a crash inside an unrelated test instead of as a clear failure here.
        XCTAssertTrue(
            TelephonyInfoProvider().responds(to: NSSelectorFromString("putString:key:value:")),
            "the carrier-string helper was renamed — update CarrierStringWriting to match"
        )
    }

    func testSentinelAndEmptyCarrierStringsAreDroppedRatherThanEmitted() {
        // The single piece of this provider's real behaviour that is observable without a SIM, and
        // the reason it matters: CoreTelephony does not return nil for a carrier it will not
        // disclose, it returns a *placeholder*. `carrierName` becomes "--" and MCC/MNC become
        // "65535" for any non-carrier app on iOS 16+. Forwarded as-is, those would reach JavaScript
        // as a plausible-looking operator name and a plausible-looking country code — a wrong value
        // is worse than an absent one for a signal a backend may compare across sessions.
        //
        // Each case is written into its own dictionary so an over-broad filter that dropped a key
        // it should have kept, or a short-circuit that stopped after the first rejection, fails on
        // the specific input rather than on an aggregate.
        let provider = unsafeBitCast(TelephonyInfoProvider(), to: CarrierStringWriting.self)

        for sentinel in Self.sentinels {
            let dictionary = NSMutableDictionary()
            provider.putString(dictionary, key: "networkOperatorName", value: sentinel)
            XCTAssertEqual(dictionary.count, 0, "the CoreTelephony sentinel \(sentinel) must be dropped")
        }

        let empty = NSMutableDictionary()
        provider.putString(empty, key: "networkOperatorName", value: "")
        XCTAssertEqual(empty.count, 0, "an empty string is dropped rather than emitted as \"\"")

        let nilled = NSMutableDictionary()
        let absent: String? = nil
        provider.putString(nilled, key: "networkOperatorName", value: absent)
        XCTAssertEqual(nilled.count, 0, "nil is dropped")

        let real = NSMutableDictionary()
        provider.putString(real, key: "networkOperatorName", value: "Carrier One")
        XCTAssertEqual(real["networkOperatorName"] as? String, "Carrier One", "a real value survives")
        XCTAssertEqual(real.count, 1)
    }

    func testTheFilterIsAnExactMatchAndNotASubstringOrPrefixTest() {
        // Pins the comparison as `-isEqualToString:` on the whole value. A carrier legitimately
        // named with a sentinel as a substring — or an MNC that merely starts with "65535" — must
        // still be emitted. This is what fails if the filter is ever "generalised" to `hasPrefix`,
        // `containsString` or a regular expression.
        let provider = unsafeBitCast(TelephonyInfoProvider(), to: CarrierStringWriting.self)

        for value in ["--A", "A--", "655350", "165535", "-", "6553"] {
            let dictionary = NSMutableDictionary()
            provider.putString(dictionary, key: "carrierMobileNetworkCode", value: value)
            XCTAssertEqual(
                dictionary["carrierMobileNetworkCode"] as? String,
                value,
                "\(value) is not a sentinel and must be emitted unchanged"
            )
        }
    }

    func testNoEmittedStringIsEverACoreTelephonySentinel() {
        // The dictionary-level counterpart of the helper test above. It is vacuous on a simulator
        // (nothing is emitted) and on an iOS 16+ device with no entitlement (the sentinels are
        // filtered) — but it is the assertion that would catch a future change that added a fifth
        // carrier string by writing it into `result` directly instead of going through the helper,
        // which is exactly how this class of bug gets reintroduced.
        for (key, value) in signals {
            guard let string = value as? String else { continue }
            XCTAssertFalse(
                Self.sentinels.contains(string),
                "\(key) carries the CoreTelephony sentinel \(string); it must be filtered"
            )
            XCTAssertFalse(string.isEmpty, "\(key) is dropped rather than emitted empty")
        }
    }

    // MARK: - Value types and boxing

    func testCarrierAllowsVoipBoxesAsACFBooleanWheneverItIsEmitted() throws {
        // `@(carrier.allowsVOIP)` boxes a `BOOL`-typed property expression, so it takes
        // `+numberWithBool:` and yields a CFBoolean — the only box that crosses the React Native
        // bridge as a JavaScript `true`/`false`. Had it been written as a comparison or a
        // `?:` expression it would be an `int`, reach JavaScript as 1/0, and fail validation against
        // `contract/raw-signal-event.schema.json`, which declares `carrierAllowsVoip` a boolean.
        // `boolValue` is equal on both boxes, so identity against kCFBoolean* is the only check that
        // separates them; see `BooleanBoxingTests.testCFBooleanIdentityIsWhatDistinguishesTheTwoBoxes`.
        //
        // SIMULATOR-ONLY CAVEAT: the key is absent on a simulator, so this body does nothing there.
        // A physical-device run with an active SIM is what actually exercises it.
        guard let value = signals["carrierAllowsVoip"] else { return }
        let number = try XCTUnwrap(value as? NSNumber, "carrierAllowsVoip must be an NSNumber")
        XCTAssertTrue(
            number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
            "carrierAllowsVoip boxed as objCType \"\(String(cString: number.objCType))\" "
                + "(\(NSStringFromClass(type(of: number)))). It must be a CFBoolean, or it crosses "
                + "the bridge as 1/0 and fails schema validation."
        )
        XCTAssertEqual(String(cString: number.objCType), "c")
    }

    func testSimCountIsAPositiveIntegerAndNotABoolean() throws {
        // The inverse guard, and the reason the provider's `if (providers.count > 0)` matters: a
        // zero count is omitted rather than emitted as `0`, so a present key always means at least
        // one subscriber entry. `@(providers.count)` boxes an `NSUInteger`; an over-broad
        // "cast everything to BOOL" fix would collapse a dual-SIM `2` into `true`.
        //
        // SIMULATOR-ONLY CAVEAT: absent on a simulator. Exercised on a device, where a dual-SIM
        // handset is the case worth having hardware for.
        guard let value = signals["simCount"] else { return }
        let number = try XCTUnwrap(value as? NSNumber, "simCount must be an NSNumber")
        XCTAssertFalse(
            number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
            "simCount is a number in the contract and must not box as a CFBoolean"
        )
        XCTAssertGreaterThan(number.intValue, 0, "a zero count is omitted, never emitted")
    }

    func testEveryEmittedCarrierStringIsAString() throws {
        for key in Self.carrierStringKeys.sorted() {
            guard let value = signals[key] else { continue }
            XCTAssertTrue(value is String, "\(key) must be a string, got \(type(of: value))")
        }
    }

    // MARK: - Field relationships

    func testAnyCarrierStringImpliesCarrierAllowsVoip() {
        // `carrierAllowsVoip` is the one field written unconditionally inside the `carrier != nil`
        // branch, so it is the branch's marker: if any of the four carrier strings made it out, the
        // branch ran, and the boolean must be there too. Asserting the implication rather than the
        // values keeps this true on the host, on a simulator and on a device alike, and it is what
        // catches a refactor that moved a string read out of the branch — or moved the boolean into
        // an `if` of its own.
        let signals = self.signals
        let emittedStrings = Set(signals.keys).intersection(Self.carrierStringKeys)
        if !emittedStrings.isEmpty {
            XCTAssertNotNil(
                signals["carrierAllowsVoip"],
                "\(emittedStrings.sorted()) came from the carrier branch, which always writes "
                    + "carrierAllowsVoip"
            )
        }
    }

    func testSimCountIsIndependentOfTheCarrierFields()  {
        // Documents a real asymmetry rather than asserting a coincidence. `simCount` comes from
        // `serviceSubscriberCellularProviders.count` while the carrier fields come from the first
        // value in that dictionary — or, when it is empty, from the deprecated
        // `subscriberCellularProvider` fallback. So a device can legitimately report carrier fields
        // with no `simCount` (the fallback path), and the two must not be assumed to co-occur.
        // The only thing that always holds is the direction below.
        let signals = self.signals
        if let count = signals["simCount"] as? NSNumber, count.intValue > 0 {
            XCTAssertNotNil(
                signals["carrierAllowsVoip"],
                "a non-empty providers dictionary has a first value, so the carrier branch ran"
            )
        }
    }

    // MARK: - The compiled-out simulator path

    func testASimulatorBuildEmitsNothingBecauseEveryCoreTelephonyReadIsCompiledOut() {
        // SIMULATOR-ONLY. Both `serviceSubscriberCellularProviders` and the deprecated
        // `subscriberCellularProvider` fallback sit inside `#if !TARGET_OS_SIMULATOR`, so on a
        // simulator triple the accessors are not in the binary: `providers` and `carrier` are `nil`
        // by construction and the result is empty. That is a deliberate property — the simulator's
        // CoreTelephony returns a stub carrier whose fields would be meaningless noise — and it is
        // worth pinning, because deleting the `#if` would make this suite start reporting a fake
        // carrier as if it were real data.
        //
        // Swift's `targetEnvironment(simulator)` is the language-level spelling of the same triple
        // property, evaluated when this test target is compiled; the two targets are always built
        // for one triple per build, so they agree by construction while remaining independent paths
        // to the answer (Swift condition vs. C preprocessor macro).
        #if targetEnvironment(simulator)
        XCTAssertTrue(
            signals.isEmpty,
            "a simulator build compiles out every CoreTelephony read, so nothing can be emitted; "
                + "got \(signals.keys.sorted())"
        )
        #else
        // On a device the reads are compiled in and the result depends on the SIM and on iOS's
        // carrier-disclosure policy, so there is nothing host-independent to assert. The
        // vocabulary, sentinel, boxing and implication tests above cover the device path.
        XCTAssertTrue(
            Set(signals.keys).isSubset(of: Self.allowedKeys),
            "unexpected keys: \(Set(signals.keys).subtracting(Self.allowedKeys).sorted())"
        )
        #endif
    }

    // MARK: - Stability

    func testRepeatedCallsAgreeOnEveryField() throws {
        // Each call constructs a fresh `CTTelephonyNetworkInfo`. Two calls back to back must agree:
        // a difference would mean the provider is reporting something that changes underneath it
        // within a single collection run, which the shared contract does not allow for a field a
        // backend may compare across sessions.
        let first = try XCTUnwrap(TelephonyInfoProvider().telephonySignals() as NSDictionary?)
        let second = try XCTUnwrap(TelephonyInfoProvider().telephonySignals() as NSDictionary?)
        XCTAssertEqual(first, second)
    }
}
