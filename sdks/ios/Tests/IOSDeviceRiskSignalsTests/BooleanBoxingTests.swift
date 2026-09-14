import XCTest

@testable import IOSDeviceRiskSignals

/// One sweep over every provider in this package that asserts the *boxing* of its numeric fields,
/// separately from what those fields mean.
///
/// **The failure this exists to catch.** `@(expr)` in Objective-C chooses its `NSNumber`
/// constructor from the static type of `expr`. A `BOOL`-typed expression gives `+numberWithBool:`,
/// which returns a CFBoolean; a C comparison (`==`, `>`), negation (`!`) or logical combination
/// (`&&`, `||`) has type `int`, so the same-looking `@(...)` gives `+numberWithInt:` and an ordinary
/// `__NSCFNumber` with objCType `"i"`. The two are indistinguishable through `boolValue`, which is
/// why value-level tests never caught this — but only a CFBoolean crosses the React Native bridge as
/// a JavaScript `true`/`false`. An `"i"` arrives as `1`/`0`, disagrees with the Kotlin
/// implementation, and fails validation against `contract/raw-signal-event.schema.json`, which
/// declares those fields `boolean` exactly as `src/NativeDeviceIntel.ts` does.
///
/// Measured, on `arm64-apple-macosx` and `arm64-apple-ios-simulator` alike:
///
/// | spelling                         | objCType | class            |
/// | ---                              | ---      | ---              |
/// | `@(count > 0)`                   | `i`      | `__NSCFNumber`   |
/// | `@(count > 0 ? YES : NO)`        | `i`      | `__NSCFNumber`   |
/// | `@((BOOL)(count > 0))`           | `c`      | `__NSCFBoolean`  |
/// | `BOOL f = count > 0; @(f)`       | `c`      | `__NSCFBoolean`  |
///
/// Note the second row: the conditional operator promotes both of its branches back to `int`, so
/// `expr ? YES : NO` looks like a fix and is not one. The `BOOL` cast, or a `BOOL` local, is the
/// load-bearing part.
///
/// The tables below classify *every* key each provider can emit, so adding a field without deciding
/// which side it belongs on fails `testEveryEmittedNumberIsClassifiedByTheseTables` rather than
/// slipping through unchecked.
final class BooleanBoxingTests: XCTestCase {
    /// A provider's emitted dictionary plus the contract type of each key it can produce.
    private struct Subject {
        let name: String
        let signals: () -> [String: Any]
        /// Keys declared `boolean` by `src/NativeDeviceIntel.ts` and the published JSON Schema.
        let booleanKeys: Set<String>
        /// Keys declared `number`/`integer`. Listed so the inverse is checked too: an over-broad
        /// "cast everything to BOOL" fix would turn one of these into a CFBoolean and collapse, say,
        /// `firstDayOfWeek: 2` into `true`.
        let numberKeys: Set<String>
        /// Keys carrying a string or an array. Present only so the classification test is total.
        let otherKeys: Set<String>
    }

    private static let subjects: [Subject] = [
        Subject(
            name: "ApplicationInfoProvider",
            signals: { ApplicationInfoProvider().applicationSignals() as? [String: Any] ?? [:] },
            booleanKeys: [
                "receiptPresent",
                "isAppExtension",
                "embeddedProvisioningProfilePresent",
                "isSimulatorBuild",
                "isDebuggable",
            ],
            numberKeys: [],
            otherKeys: [
                "appVersion", "appBuild", "bundleId", "bundleExecutable", "minimumOsVersion",
                "receiptEnvironment",
            ]
        ),
        Subject(
            name: "LocaleInfoProvider",
            signals: { LocaleInfoProvider().localeSignals() as? [String: Any] ?? [:] },
            booleanKeys: ["uses24HourClock"],
            numberKeys: ["timezoneOffsetMinutes", "firstDayOfWeek"],
            otherKeys: [
                "language", "country", "currencyCode", "decimalSeparator", "groupingSeparator",
                "languages", "measurementSystem", "timezoneId", "calendar",
            ]
        ),
        Subject(
            name: "NumericConsistencyProvider",
            signals: { NumericConsistencyProvider().numericConsistencySignals() as? [String: Any] ?? [:] },
            booleanKeys: ["signedZeroPreserved", "subnormalPreserved"],
            numberKeys: ["integerVectorResult"],
            otherKeys: ["floatVector"]
        ),
        Subject(
            name: "RuntimeTimingProvider",
            signals: { RuntimeTimingProvider().runtimeTimingSignals() as? [String: Any] ?? [:] },
            booleanKeys: [],
            numberKeys: [
                "nativeSampleCount", "nativeTimerResolutionNs", "nativeIntervalMedianNs",
                "nativeIntervalP95Ns", "nativeIntervalMadNs",
            ],
            otherKeys: ["nativeClockSource"]
        ),
        Subject(
            name: "NetworkInfoProvider",
            signals: { NetworkInfoProvider().networkSignals() as? [String: Any] ?? [:] },
            booleanKeys: ["isVpnActive", "isProxyConfigured", "isConnected"],
            // `proxyPort` is conditional — it appears only on a host with an HTTP proxy carrying an
            // explicit port — so the count assertion below does not include it. Its boxing is pinned
            // unconditionally in `NetworkInfoProviderTests` by feeding the private proxy helper a
            // synthesised settings dictionary, which is the only way to exercise it without one.
            numberKeys: ["proxyPort"],
            otherKeys: ["interfaceNames", "localIpAddresses", "connectionType", "proxyHost"]
        ),
        Subject(
            name: "AudioLatencyProvider",
            signals: { AudioLatencyProvider().audioLatency() as? [String: Any] ?? [:] },
            booleanKeys: ["measured"],
            numberKeys: [
                "outputLatencyMs", "inputLatencyMs", "ioBufferDurationMs", "nativeSampleRate",
            ],
            otherKeys: []
        ),
    ]

    private static func isCFBoolean(_ number: NSNumber) -> Bool {
        number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber)
    }

    // MARK: - The contract

    func testEveryBooleanFieldBoxesAsACFBoolean() throws {
        var checked = 0
        for subject in Self.subjects {
            let signals = subject.signals()
            for key in subject.booleanKeys.sorted() {
                // Every boolean in this package is emitted unconditionally except
                // `uses24HourClock`, which the provider omits when Foundation returns no time
                // pattern. An absent key is a different contract question, pinned by the
                // per-provider omission tests; here it is simply nothing to box.
                //
                // The count below is 12: five from ApplicationInfoProvider, one each from
                // LocaleInfoProvider and AudioLatencyProvider, two from NumericConsistencyProvider,
                // and three from NetworkInfoProvider. `TelephonyInfoProvider.carrierAllowsVoip` is
                // deliberately absent from the table — it appears only on a device with a SIM, and
                // an exact count is the wrong shape for a key that comes and goes. It is pinned in
                // `TelephonyInfoProviderTests` with the same CFBoolean-identity check.
                guard let value = signals[key] else { continue }
                let number = try XCTUnwrap(value as? NSNumber, "\(subject.name).\(key) must be an NSNumber")
                XCTAssertTrue(
                    Self.isCFBoolean(number),
                    "\(subject.name).\(key) boxed as objCType \"\(String(cString: number.objCType))\" "
                        + "(\(NSStringFromClass(type(of: number)))). It must be a CFBoolean, or it "
                        + "crosses the bridge as 1/0 and fails schema validation."
                )
                XCTAssertEqual(String(cString: number.objCType), "c", "\(subject.name).\(key)")
                checked += 1
            }
        }
        XCTAssertEqual(checked, 12, "every boolean this package emits must have been inspected")
    }

    func testNoNumericFieldIsBoxedAsACFBoolean() throws {
        // The inverse guard. `@((BOOL)x)` applied to a count would emit `true` where the contract
        // says `2`, and `boolValue`-based assertions elsewhere would not notice.
        for subject in Self.subjects {
            let signals = subject.signals()
            for key in subject.numberKeys.sorted() {
                guard let value = signals[key] else { continue }
                let number = try XCTUnwrap(value as? NSNumber, "\(subject.name).\(key) must be an NSNumber")
                XCTAssertFalse(
                    Self.isCFBoolean(number),
                    "\(subject.name).\(key) is a number in the contract and must not box as a CFBoolean"
                )
            }
        }
    }

    // MARK: - Keeping the tables honest

    func testEveryEmittedNumberIsClassifiedByTheseTables() {
        for subject in Self.subjects {
            let emitted = Set(subject.signals().keys)
            let classified = subject.booleanKeys
                .union(subject.numberKeys)
                .union(subject.otherKeys)
            XCTAssertTrue(
                emitted.isSubset(of: classified),
                "\(subject.name) emits unclassified keys — decide whether each is a contract boolean "
                    + "or a number and add it above: \(emitted.subtracting(classified).sorted())"
            )
        }
    }

    func testEveryNumberInAnEmittedArrayIsANumberAndNotACFBoolean() throws {
        // `floatVector` is the only array of numbers in this package. A CFBoolean inside it would
        // serialize as `true` in the middle of a list of doubles.
        for subject in Self.subjects {
            for (key, value) in subject.signals() {
                guard let array = value as? [NSNumber] else { continue }
                for (index, number) in array.enumerated() {
                    XCTAssertFalse(
                        Self.isCFBoolean(number),
                        "\(subject.name).\(key)[\(index)] must not box as a CFBoolean"
                    )
                }
            }
        }
    }

    // MARK: - The mechanism itself

    func testCFBooleanIdentityIsWhatDistinguishesTheTwoBoxes() {
        // Guards the assertion style above rather than the providers: `boolValue` is equal on both
        // sides, so identity against kCFBooleanTrue/False — not value — is the only thing that
        // separates a correctly boxed flag from an int. If this ever stopped being true the sweep
        // would silently pass on broken data.
        let asInt = NSNumber(value: 1 as Int32)
        let asBool = NSNumber(value: true)

        XCTAssertEqual(asInt.boolValue, asBool.boolValue, "boolValue cannot tell the two apart")
        XCTAssertEqual(asInt, asBool, "isEqual: cannot tell the two apart either")
        XCTAssertFalse(Self.isCFBoolean(asInt))
        XCTAssertTrue(Self.isCFBoolean(asBool))
        XCTAssertEqual(String(cString: asInt.objCType), "i")
        XCTAssertEqual(String(cString: asBool.objCType), "c")
    }
}
