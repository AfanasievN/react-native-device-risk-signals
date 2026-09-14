import XCTest

@testable import IOSDeviceRiskSignals

/// Exercises the summarization helpers through the package's public C surface.
///
/// Every expectation here is a value computed by hand from the algorithm the React Native package
/// shipped before the move (nearest-rank percentile with `ceil(p * n) - 1`, median absolute
/// deviation against that median, and a population standard deviation over the finite samples).
/// Asserting real numbers rather than mere presence is what makes this a regression guard for the
/// extraction.
final class SignalStatisticsTests: XCTestCase {
    private let accuracy = 1e-12

    private func summarize(_ values: [Double]) -> [String: NSNumber]? {
        RNDISummarize(values.map { NSNumber(value: $0) })
    }

    private func slope(_ values: [Double]) -> NSNumber? {
        RNDIWarmupSlope(values.map { NSNumber(value: $0) })
    }

    // MARK: - RNDISummarize

    func testSummarizeReturnsNilForEmptyInput() {
        XCTAssertNil(summarize([]))
    }

    func testSummarizeReturnsNilWhenEverySampleIsNonFinite() {
        XCTAssertNil(summarize([.nan, .infinity, -.infinity]))
    }

    func testSummarizeSingleSampleReportsZeroDispersion() throws {
        let result = try XCTUnwrap(summarize([42]))

        XCTAssertEqual(Set(result.keys), ["sampleCount", "median", "p95", "mad", "coefficientOfVariation"])
        XCTAssertEqual(result["sampleCount"]?.intValue, 1)
        XCTAssertEqual(try XCTUnwrap(result["median"]).doubleValue, 42, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(result["p95"]).doubleValue, 42, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(result["mad"]).doubleValue, 0, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(result["coefficientOfVariation"]).doubleValue, 0, accuracy: accuracy)
    }

    func testSummarizeDropsNonFiniteSamplesBeforeComputing() throws {
        // Finite subset is [1, 2, 3]: median index ceil(0.5*3)-1 = 1, p95 index ceil(0.95*3)-1 = 2,
        // deviations [1, 0, 1] sorted to [0, 1, 1] so mad index 1, mean 2, population sd sqrt(2/3).
        let result = try XCTUnwrap(summarize([1, .infinity, .nan, 2, 3]))

        XCTAssertEqual(result["sampleCount"]?.intValue, 3)
        XCTAssertEqual(try XCTUnwrap(result["median"]).doubleValue, 2, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(result["p95"]).doubleValue, 3, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(result["mad"]).doubleValue, 1, accuracy: accuracy)
        XCTAssertEqual(
            try XCTUnwrap(result["coefficientOfVariation"]).doubleValue,
            (2.0 / 3.0).squareRoot() / 2.0,
            accuracy: accuracy
        )
    }

    func testSummarizeSortsBeforePercentiles() throws {
        // Unsorted [5, 1, 4, 2, 3] must behave exactly like [1, 2, 3, 4, 5].
        let unsorted = try XCTUnwrap(summarize([5, 1, 4, 2, 3]))
        let sorted = try XCTUnwrap(summarize([1, 2, 3, 4, 5]))

        XCTAssertEqual(unsorted, sorted)
        XCTAssertEqual(unsorted["sampleCount"]?.intValue, 5)
        XCTAssertEqual(try XCTUnwrap(unsorted["median"]).doubleValue, 3, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(unsorted["p95"]).doubleValue, 5, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(unsorted["mad"]).doubleValue, 1, accuracy: accuracy)
        XCTAssertEqual(
            try XCTUnwrap(unsorted["coefficientOfVariation"]).doubleValue,
            Double(2).squareRoot() / 3.0,
            accuracy: accuracy
        )
    }

    func testSummarizeEvenSampleCountUsesUpperNearestRank() throws {
        // [1, 2, 3, 4]: median index ceil(2)-1 = 1 -> 2 (not the 2.5 interpolated midpoint),
        // p95 index ceil(3.8)-1 = 3 -> 4, deviations sorted [0, 1, 1, 2] so mad = 1,
        // mean 2.5, population sd sqrt(5/4).
        let result = try XCTUnwrap(summarize([1, 2, 3, 4]))

        XCTAssertEqual(result["sampleCount"]?.intValue, 4)
        XCTAssertEqual(try XCTUnwrap(result["median"]).doubleValue, 2, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(result["p95"]).doubleValue, 4, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(result["mad"]).doubleValue, 1, accuracy: accuracy)
        XCTAssertEqual(
            try XCTUnwrap(result["coefficientOfVariation"]).doubleValue,
            (1.25).squareRoot() / 2.5,
            accuracy: accuracy
        )
    }

    func testSummarizeOmitsCoefficientOfVariationWhenMeanIsNotPositive() throws {
        let negativeMean = try XCTUnwrap(summarize([-2, -1, 0, 1]))
        XCTAssertNil(negativeMean["coefficientOfVariation"])
        XCTAssertEqual(Set(negativeMean.keys), ["sampleCount", "median", "p95", "mad"])
        XCTAssertEqual(try XCTUnwrap(negativeMean["median"]).doubleValue, -1, accuracy: accuracy)

        let zeroMean = try XCTUnwrap(summarize([0, 0, 0]))
        XCTAssertNil(zeroMean["coefficientOfVariation"])
        XCTAssertEqual(try XCTUnwrap(zeroMean["mad"]).doubleValue, 0, accuracy: accuracy)
    }

    // MARK: - RNDIWarmupSlope

    func testWarmupSlopeNeedsAtLeastFourSamples() {
        XCTAssertNil(slope([]))
        XCTAssertNil(slope([1, 2, 3]))
        XCTAssertNotNil(slope([1, 2, 3, 4]))
    }

    func testWarmupSlopeIsZeroForAFlatSeries() throws {
        XCTAssertEqual(try XCTUnwrap(slope([1, 1, 1, 1])).doubleValue, 0, accuracy: accuracy)
    }

    func testWarmupSlopeComparesSecondHalfAgainstFirstHalf() throws {
        XCTAssertEqual(try XCTUnwrap(slope([1, 1, 2, 2])).doubleValue, 1, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(slope([4, 4, 2, 2])).doubleValue, -0.5, accuracy: accuracy)
    }

    func testWarmupSlopeSplitsOddCountsWithTheLargerSecondHalf() throws {
        // count 5 -> midpoint 2: first mean (1+2)/2 = 1.5, second mean (3+4+5)/3 = 4.
        XCTAssertEqual(
            try XCTUnwrap(slope([1, 2, 3, 4, 5])).doubleValue,
            (4.0 - 1.5) / 1.5,
            accuracy: accuracy
        )
    }

    func testWarmupSlopeReturnsNilWhenTheFirstHalfIsNotPositive() {
        XCTAssertNil(slope([0, 0, 1, 1]))
        XCTAssertNil(slope([-1, -1, 1, 1]))
    }

    func testWarmupSlopeReturnsNilForNonFiniteHalves() {
        XCTAssertNil(slope([.nan, 1, 1, 1]))
        XCTAssertNil(slope([1, 1, .infinity, 1]))
    }

    func testWarmupSlopeDoesNotSortOrFilterItsInput() throws {
        // Unlike RNDISummarize this reads the series in arrival order, which is the whole point of a
        // warm-up measurement. Reversing the series must flip the sign of the slope.
        XCTAssertEqual(try XCTUnwrap(slope([2, 2, 4, 4])).doubleValue, 1, accuracy: accuracy)
        XCTAssertEqual(try XCTUnwrap(slope([4, 4, 2, 2])).doubleValue, -0.5, accuracy: accuracy)
    }
}
