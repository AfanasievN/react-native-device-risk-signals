import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key set, value types and values of `LocaleInfoProvider`.
///
/// This probe is `enabledByDefault: true` in the Probe Catalog, so its dictionary reaches production
/// traffic on every collection. The React Native binding forwards it to JavaScript unchanged: a
/// renamed key, a changed value type or a changed omission rule is a breaking contract change, not
/// an implementation detail.
///
/// `-localeSignals` reads `+[NSLocale currentLocale]`, `+[NSTimeZone localTimeZone]` and
/// `+[NSCalendar currentCalendar]` directly — there is no seam to inject a fixed locale, and the
/// values therefore depend on the machine running the tests. Where that is the case the expectation
/// is computed from Foundation's *modern* Swift API (`Locale.current`, `TimeZone`, `Calendar`)
/// rather than from a literal. That is deliberately a different API path from the provider's
/// `-[NSLocale objectForKey:]` reads, so a swapped or misspelled key — `country` carrying the
/// language, say — still fails here. Literals are asserted wherever the value does not depend on
/// the host: the key vocabulary, the measurement-system mapping, the derivation rule behind
/// `uses24HourClock`, and the Required-Reason omission below.
final class LocaleInfoProviderTests: XCTestCase {
    /// Every key `-localeSignals` is allowed to emit. Each one is optional: the provider drops
    /// empty strings and omits whole fields when the platform has nothing to report.
    private static let allowedKeys: Set<String> = [
        "language",
        "country",
        "currencyCode",
        "decimalSeparator",
        "groupingSeparator",
        "languages",
        "measurementSystem",
        "timezoneId",
        "timezoneOffsetMinutes",
        "calendar",
        "firstDayOfWeek",
        "uses24HourClock",
    ]

    private var signals: [String: Any] {
        LocaleInfoProvider().localeSignals() as? [String: Any] ?? [:]
    }

    // MARK: - Required-Reason API

    func testKeyboardLanguagesIsNeverEmitted() {
        // The header documents this as a hard rule, not an oversight: reading the enabled keyboards
        // through `-[UITextInputMode activeInputModes]` is an Apple Required-Reason API (Active
        // Keyboard, DDA9.1) whose only sanctioned reason is a custom-keyboard extension. The
        // binding's PrivacyInfo.xcprivacy declares ZERO Required-Reason APIs, so the moment this
        // key appears the shipped privacy manifest becomes false. `LocaleSignals` in
        // `src/NativeDeviceIntel.ts` declares `keyboardLanguages` because Android supplies it; iOS
        // must leave it absent. This test fails if that ever changes.
        XCTAssertNil(signals["keyboardLanguages"])
        XCTAssertFalse(signals.keys.contains("keyboardLanguages"))
    }

    func testEmitsNothingOutsideTheContractedKeyVocabulary() {
        XCTAssertTrue(
            Set(signals.keys).isSubset(of: Self.allowedKeys),
            "unexpected keys: \(Set(signals.keys).subtracting(Self.allowedKeys).sorted())"
        )
    }

    // MARK: - Locale codes and separators

    func testLanguageCountryAndCurrencyCarryTheCurrentLocaleCodes() throws {
        let signals = self.signals
        let locale = NSLocale.current as NSLocale

        for (key, nsKey) in [
            ("language", NSLocale.Key.languageCode),
            ("country", NSLocale.Key.countryCode),
            ("currencyCode", NSLocale.Key.currencyCode),
        ] {
            let expected = locale.object(forKey: nsKey) as? String
            if let expected, !expected.isEmpty {
                XCTAssertEqual(signals[key] as? String, expected, "\(key) must be the locale's \(nsKey.rawValue)")
            } else {
                // `-putString:key:value:` drops nil and empty strings rather than emitting "".
                XCTAssertNil(signals[key], "\(key) must be omitted when the locale has no value")
            }
        }
    }

    func testSeparatorsCarryTheCurrentLocaleSeparators() throws {
        let signals = self.signals
        let locale = NSLocale.current as NSLocale

        let decimal = try XCTUnwrap(locale.object(forKey: .decimalSeparator) as? String)
        XCTAssertEqual(signals["decimalSeparator"] as? String, decimal)
        // The two separators must differ for a number to be parseable at all; this catches the two
        // adjacent `objectForKey:` reads being swapped, which the equality checks above cannot when
        // a host locale happens to use the same character for both.
        let grouping = locale.object(forKey: .groupingSeparator) as? String
        if let grouping, !grouping.isEmpty {
            XCTAssertEqual(signals["groupingSeparator"] as? String, grouping)
            XCTAssertNotEqual(signals["groupingSeparator"] as? String, signals["decimalSeparator"] as? String)
        } else {
            XCTAssertNil(signals["groupingSeparator"])
        }
    }

    // MARK: - Preferred languages

    func testLanguagesIsThePreferredLanguageOrdering() throws {
        let languages = try XCTUnwrap(signals["languages"] as? [String], "languages must be an array of strings")
        XCTAssertFalse(languages.isEmpty, "the key is omitted entirely rather than emitted empty")
        XCTAssertEqual(languages, NSLocale.preferredLanguages)
        // Full ordering, not just the first entry: the ordering itself is the signal.
        XCTAssertEqual(languages.count, NSLocale.preferredLanguages.count)
    }

    // MARK: - Measurement system

    func testMeasurementSystemUsesTheThreeLowercaseTokens() throws {
        // The mapping is a literal table in the provider and does not depend on the host:
        // "Metric" -> "metric", "U.S." -> "us", "U.K." -> "uk", anything else -> key omitted.
        let raw = (NSLocale.current as NSLocale).object(forKey: .measurementSystem) as? String
        let expected = ["Metric": "metric", "U.S.": "us", "U.K.": "uk"][raw ?? ""]
        XCTAssertEqual(signals["measurementSystem"] as? String, expected)
        if let emitted = signals["measurementSystem"] as? String {
            XCTAssertTrue(["metric", "us", "uk"].contains(emitted))
        }
    }

    func testMeasurementSystemMappingCoversTheThreeFoundationValues() {
        // Pins the assumption the table is built on: Foundation still reports these three exact
        // strings. "U.K." in particular is a later addition — before it existed the UK reported
        // "U.S.", and a regression there would silently relabel British devices.
        let observed = ["en_US", "en_GB", "fr_FR"].map {
            (Locale(identifier: $0) as NSLocale).object(forKey: .measurementSystem) as? String
        }
        XCTAssertEqual(observed, ["U.S.", "U.K.", "Metric"])
    }

    // MARK: - Time zone

    func testTimezoneIdAndOffsetMatchTheLocalTimeZone() throws {
        let signals = self.signals
        let zone = NSTimeZone.local as NSTimeZone

        XCTAssertEqual(signals["timezoneId"] as? String, zone.name)
        let offset = try XCTUnwrap(signals["timezoneOffsetMinutes"] as? NSNumber)
        XCTAssertEqual(offset.intValue, zone.secondsFromGMT / 60)
        // Minutes, not seconds, and within the real range of UTC offsets (-12:00 .. +14:00).
        XCTAssertGreaterThanOrEqual(offset.intValue, -12 * 60)
        XCTAssertLessThanOrEqual(offset.intValue, 14 * 60)
    }

    // MARK: - Calendar

    func testCalendarIdentifierAndFirstDayOfWeekMatchTheCurrentCalendar() throws {
        let signals = self.signals
        let calendar = NSCalendar.current as NSCalendar

        // `NSCalendarIdentifier` is an NSString under the hood, so this is the raw ICU-style token
        // ("gregorian", "islamic-civil", ...) reaching JavaScript, not a localized display name.
        XCTAssertEqual(signals["calendar"] as? String, calendar.calendarIdentifier.rawValue)

        let firstDay = try XCTUnwrap(signals["firstDayOfWeek"] as? NSNumber)
        XCTAssertEqual(firstDay.intValue, calendar.firstWeekday)
        // Foundation's 1-based Sunday==1 convention, not the ISO-8601 Monday==1 one.
        XCTAssertGreaterThanOrEqual(firstDay.intValue, 1)
        XCTAssertLessThanOrEqual(firstDay.intValue, 7)
    }

    // MARK: - 24-hour clock

    func testUses24HourClockFollowsTheLocaleTimePattern() throws {
        let flag = try XCTUnwrap(signals["uses24HourClock"] as? NSNumber)
        let pattern = try XCTUnwrap(
            DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: NSLocale.current)
        )
        XCTAssertEqual(flag.boolValue, !pattern.contains("a"))
    }

    func testUses24HourClockBoxesAsACFBooleanAndNotAsAnInt() throws {
        // `@(expr)` picks its `NSNumber` constructor from the *static type* of `expr`. The flag is
        // derived from a C `==` comparison, whose result type in a `.m` file is `int`, so the
        // unguarded spelling `@(range.location == NSNotFound)` called `+numberWithInt:` and produced
        // an NSNumber with objCType "i". Only a CFBoolean — what `+numberWithBool:` returns — crosses
        // the React Native bridge as a JavaScript `true`/`false`; an "i" arrives as 1/0 and fails
        // validation against `contract/raw-signal-event.schema.json`, which declares this field
        // `boolean` exactly as `LocaleSignals` in `src/NativeDeviceIntel.ts` does. The provider now
        // casts the comparison to `BOOL` inside the literal, so the box is a CFBoolean.
        //
        // Note that `expr ? YES : NO` does NOT fix this: the conditional operator promotes both
        // branches back to `int` and the box is an "i" again. The cast is the load-bearing part.
        //
        // `locale` is `enabledByDefault: true`, so this field is on the wire for every collection.
        let flag = try XCTUnwrap(signals["uses24HourClock"] as? NSNumber)
        XCTAssertTrue(
            flag === (kCFBooleanTrue as NSNumber) || flag === (kCFBooleanFalse as NSNumber),
            "uses24HourClock must box as a CFBoolean so it crosses the bridge as true/false, not 1/0"
        )
        XCTAssertEqual(String(cString: flag.objCType), "c")
    }

    func testUses24HourClockDerivationRuleHoldsForKnownLocales() {
        // The host's own answer is whatever the host is set to, so the rule itself is pinned against
        // locales whose clock convention is fixed: the "j" skeleton resolves to a 12-hour pattern
        // carrying the `a` (AM/PM) field for en_US and to a 24-hour pattern without it elsewhere.
        func twelveHour(_ identifier: String) -> Bool? {
            DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: Locale(identifier: identifier))?
                .contains("a")
        }
        XCTAssertEqual(twelveHour("en_US"), true)
        XCTAssertEqual(twelveHour("en_GB"), false)
        XCTAssertEqual(twelveHour("de_DE"), false)
        XCTAssertEqual(twelveHour("ru_RU"), false)
    }

    // MARK: - Stability

    func testRepeatedCallsAgreeOnEveryField() throws {
        let first = try XCTUnwrap(LocaleInfoProvider().localeSignals() as NSDictionary?)
        let second = try XCTUnwrap(LocaleInfoProvider().localeSignals() as NSDictionary?)
        XCTAssertEqual(first, second)
    }
}
