import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key sets, value types and — where the observation is deterministic — the exact
/// values of the two providers that moved into this package. The React Native binding forwards these
/// dictionaries to JavaScript unchanged, so a renamed key or a changed value type is a breaking
/// contract change, not an implementation detail.
final class RuntimeTimingProviderTests: XCTestCase {
    private static let expectedKeys: Set<String> = [
        "nativeClockSource",
        "nativeSampleCount",
        "nativeTimerResolutionNs",
        "nativeIntervalMedianNs",
        "nativeIntervalP95Ns",
        "nativeIntervalMadNs",
    ]

    private var signals: [String: Any] {
        RuntimeTimingProvider().runtimeTimingSignals() as? [String: Any] ?? [:]
    }

    func testEmitsExactlyTheContractedKeys() {
        XCTAssertEqual(Set(signals.keys), Self.expectedKeys)
    }

    func testClockSourceIsTheDocumentedStringConstant() {
        XCTAssertEqual(signals["nativeClockSource"] as? String, "cf_absolute_time")
    }

    func testNumericFieldsAreNumbersAndNeverNegative() throws {
        let signals = self.signals
        for key in Self.expectedKeys where key != "nativeClockSource" {
            let number = try XCTUnwrap(signals[key] as? NSNumber, "\(key) must be an NSNumber")
            XCTAssertTrue(number.doubleValue.isFinite, "\(key) must be finite")
            XCTAssertGreaterThanOrEqual(number.doubleValue, 0, "\(key) must not be negative")
        }
    }

    func testSampleCountNeverExceedsTheFixedSampleBudget() throws {
        // The loop takes 256 readings and keeps only the strictly positive deltas, so the reported
        // count is bounded by 256 and can legitimately be lower on a coarse clock.
        let count = try XCTUnwrap(signals["nativeSampleCount"] as? NSNumber).intValue
        XCTAssertGreaterThanOrEqual(count, 0)
        XCTAssertLessThanOrEqual(count, 256)
    }

    func testPercentileOrderingHoldsAcrossTheSummary() throws {
        let signals = self.signals
        let count = try XCTUnwrap(signals["nativeSampleCount"] as? NSNumber).intValue
        try XCTSkipIf(count == 0, "clock produced no positive deltas on this host")

        let resolution = try XCTUnwrap(signals["nativeTimerResolutionNs"] as? NSNumber).doubleValue
        let median = try XCTUnwrap(signals["nativeIntervalMedianNs"] as? NSNumber).doubleValue
        let p95 = try XCTUnwrap(signals["nativeIntervalP95Ns"] as? NSNumber).doubleValue

        XCTAssertGreaterThan(resolution, 0, "resolution is the smallest positive delta observed")
        XCTAssertLessThanOrEqual(resolution, median)
        XCTAssertLessThanOrEqual(median, p95)
    }

    func testResultIsStableAcrossCalls() {
        XCTAssertEqual(Set(signals.keys), Set(signals.keys))
        XCTAssertEqual(signals["nativeClockSource"] as? String, signals["nativeClockSource"] as? String)
    }
}

final class NumericConsistencyProviderTests: XCTestCase {
    private var signals: [String: Any] {
        NumericConsistencyProvider().numericConsistencySignals() as? [String: Any] ?? [:]
    }

    func testEmitsExactlyTheContractedKeys() {
        XCTAssertEqual(
            Set(signals.keys),
            ["integerVectorResult", "floatVector", "signedZeroPreserved", "subnormalPreserved"]
        )
    }

    func testIntegerVectorResultIsTheFixedFnvStyleDigest() throws {
        // 1024 rounds of `hash ^= i * 2654435761; hash *= 16777619` over uint32 starting at the FNV
        // offset basis 2166136261. The result is a constant on every conforming platform; a
        // different number means the 32-bit wrap-around maths changed.
        let value = try XCTUnwrap(signals["integerVectorResult"] as? NSNumber)
        XCTAssertEqual(value.doubleValue, 3_373_885_893, accuracy: 0)
    }

    func testFloatVectorCarriesTheFiveTranscendentalProbeValues() throws {
        let vector = try XCTUnwrap(signals["floatVector"] as? [NSNumber])
        XCTAssertEqual(vector.count, 5)

        let expected = [Double(2).squareRoot(), sin(0.5), cos(0.5), log(2.0), exp(0.25)]
        for (index, (actual, want)) in zip(vector, expected).enumerated() {
            XCTAssertEqual(actual.doubleValue, want, accuracy: 0, "floatVector[\(index)]")
        }
        // Spot-check against literals so a silently swapped element is caught too.
        XCTAssertEqual(vector[0].doubleValue, 1.4142135623730951, accuracy: 0)
        XCTAssertEqual(vector[1].doubleValue, 0.47942553860420301, accuracy: 0)
        XCTAssertEqual(vector[2].doubleValue, 0.87758256189037276, accuracy: 0)
        XCTAssertEqual(vector[3].doubleValue, 0.69314718055994529, accuracy: 0)
        XCTAssertEqual(vector[4].doubleValue, 1.2840254166877414, accuracy: 0)
    }

    func testIeee754FlagsAreTrueOnAConformingRuntime() throws {
        let signedZero = try XCTUnwrap(signals["signedZeroPreserved"] as? NSNumber)
        let subnormal = try XCTUnwrap(signals["subnormalPreserved"] as? NSNumber)

        XCTAssertTrue(signedZero.boolValue)
        XCTAssertTrue(subnormal.boolValue)
        XCTAssertEqual(signedZero.intValue, 1)
        XCTAssertEqual(subnormal.intValue, 1)
    }

    func testIeee754FlagsBoxAsIntNotBoolAsTheyDidBeforeTheMove() throws {
        // Pre-existing behavior, pinned here deliberately so the extraction stays byte-for-byte
        // identical. Both fields are built from C `&&` expressions, whose result type is `int`, so
        // `@(...)` boxes an `NSNumber` with objCType "i" rather than the "c" that `@YES`/`@NO`
        // produce. A CFBoolean is what the React Native bridge turns into a JS `true`/`false`, so
        // these currently reach JavaScript as 1/0 even though `NativeDeviceIntel.ts` declares them
        // `boolean`. Changing that is a contract change for the binding to make, not something this
        // source move may do silently.
        let signedZero = try XCTUnwrap(signals["signedZeroPreserved"] as? NSNumber)
        let subnormal = try XCTUnwrap(signals["subnormalPreserved"] as? NSNumber)

        XCTAssertEqual(String(cString: signedZero.objCType), "i")
        XCTAssertEqual(String(cString: subnormal.objCType), "i")
    }

    func testResultIsDeterministic() throws {
        let first = NumericConsistencyProvider().numericConsistencySignals() as? [String: Any]
        let second = NumericConsistencyProvider().numericConsistencySignals() as? [String: Any]
        XCTAssertEqual(
            try XCTUnwrap(first) as NSDictionary,
            try XCTUnwrap(second) as NSDictionary
        )
    }
}
