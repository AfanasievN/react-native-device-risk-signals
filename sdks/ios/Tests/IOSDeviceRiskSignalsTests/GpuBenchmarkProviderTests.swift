import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key vocabulary, the skip shape, the statistics fields borrowed from
/// `SignalStatistics`, and the worker-thread precondition of `GpuBenchmarkProvider`.
///
/// **What this suite deliberately does not assert.** No timing, no draw-call count, and no
/// benchmark budget. `gpu_benchmark` ships **disabled by default**
/// (`src/probes/gpuBenchmarkProbe.ts`), its numbers are a device fingerprint rather than a
/// threshold, and every one of them depends on hardware, thermal state and scheduler luck. A test
/// asserting "at least N draw calls" or "under 50 ms" would be pinning the CI machine, not the
/// provider, and would fail on a loaded agent while a genuinely broken provider still passed. What
/// *is* pinned is structural: which keys may appear, which appear together, and which are forbidden
/// when the benchmark did not run.
///
/// **What a simulator can and cannot exercise.** The whole Metal path sits inside
/// `#if TARGET_OS_SIMULATOR` … `#else`, so a simulator run returns the two-key skip dictionary and
/// the measurement code is not even in the binary — the same situation `TelephonyInfoProviderTests`
/// faces. The measurement-shape tests are therefore written as "if the benchmark ran, it must look
/// like this": silent on a simulator, load-bearing the moment the suite is pointed at hardware.
///
/// **Why every call here goes through a background queue.** XCTest runs test methods on the main
/// thread and `-gpuBenchmark` now rejects the main thread outright (see
/// `GpuExecutionPolicyTests`). That is not an inconvenience to work around — it is the contract
/// under test, so the rejecting direction gets its own test rather than being avoided.
final class GpuBenchmarkProviderTests: XCTestCase {
    /// Every key `-gpuBenchmark` is allowed to emit. `GpuBenchmarkSignals` in
    /// `src/NativeDeviceIntel.ts` is shared with Android and declares more; `vendorName`,
    /// `shadingLanguageVersion` and `maxTextureSize` are GLES reads with no Metal counterpart and
    /// must stay absent here.
    private static let allowedKeys: Set<String> = [
        "benchmarkPerformed",
        "skippedReason",
        "rendererName",
        "drawCallsCompleted",
        "durationMs",
        "gpuTimeMs",
        "operationTimeP50Ms",
        "operationTimeP95Ms",
        "operationTimeMadMs",
        "operationTimeCoefficientOfVariation",
        "warmupSlope",
    ]

    /// The three reasons the provider can report, one per skip path: the compile-time simulator
    /// branch, a nil `MTLDevice`/queue/buffer, and the `@catch`.
    private static let skipReasons: Set<String> = ["emulator", "unsupported", "error"]

    /// The keys that only a completed benchmark may produce.
    private static let measurementKeys: Set<String> = [
        "drawCallsCompleted",
        "durationMs",
        "gpuTimeMs",
        "operationTimeP50Ms",
        "operationTimeP95Ms",
        "operationTimeMadMs",
        "operationTimeCoefficientOfVariation",
        "warmupSlope",
    ]

    /// Runs the provider the way the binding does — off the main thread — and returns its
    /// dictionary. A deadlock or a violated precondition surfaces as a timeout or a recorded
    /// exception rather than as a hang in an unrelated test.
    private func benchmarkOffTheMainThread() -> [String: Any] {
        var result: [String: Any] = [:]
        var failure: NSException?
        let done = expectation(description: "gpuBenchmark on a worker thread")
        DispatchQueue.global(qos: .userInitiated).async {
            failure = RNDIExecutionPolicyFailure {
                result = GpuBenchmarkProvider().gpuBenchmark() as? [String: Any] ?? [:]
            }
            done.fulfill()
        }
        wait(for: [done], timeout: 30)
        XCTAssertNil(failure, "a worker thread is the supported caller: \(String(describing: failure))")
        return result
    }

    // MARK: - The worker-thread precondition, from both sides

    func testGpuWorkIsRejectedOnTheMainThread() {
        // The inverse of Android's `GpuBenchmarkCollector`, which calls
        // `GpuExecutionPolicy.requireWorker(mainThread())` as its first statement with the comment
        // "Guard here, not only in the facade, so no internal caller can start GL work on the UI
        // thread". The iOS guard is in the same position — *before* the simulator skip — so this
        // test is meaningful on a simulator too, where the Metal path does not exist.
        XCTAssertTrue(Thread.isMainThread, "XCTest runs test methods on the main thread")

        let failure = RNDIExecutionPolicyFailure { _ = GpuBenchmarkProvider().gpuBenchmark() }
        XCTAssertEqual(
            failure?.name.rawValue,
            NSExceptionName.internalInconsistencyException.rawValue,
            "GPU collection on the main thread must be rejected, not quietly performed"
        )
        XCTAssertEqual(failure?.reason, "GPU collection requires a worker thread")
    }

    func testTheRejectionHappensBeforeAnyResultIsProduced() {
        // A guard that ran *after* the work would still raise, but would have blocked the main
        // thread for the whole budget first — the exact harm the policy exists to prevent. The
        // provider's `NSMutableDictionary` is created after the guard, so nothing is emitted on the
        // rejected path: the raise is the only outcome.
        var produced: Any?
        let failure = RNDIExecutionPolicyFailure { produced = GpuBenchmarkProvider().gpuBenchmark() }
        XCTAssertNotNil(failure)
        XCTAssertNil(produced, "a rejected call produces no dictionary at all, not an empty one")
    }

    func testGpuWorkIsAcceptedOnAWorkerThread() {
        // The permitted direction, which is also how `ios/DeviceIntel.mm` calls it: the module
        // declares its own concurrent `methodQueue`, so the TurboModule method never runs on the
        // main thread. The dictionary is non-empty on every path — even both skip paths write
        // `benchmarkPerformed`.
        let signals = benchmarkOffTheMainThread()
        XCTAssertFalse(signals.isEmpty, "a worker-thread call always returns a dictionary")
        XCTAssertNotNil(signals["benchmarkPerformed"])
    }

    // MARK: - Key vocabulary

    func testEmitsNothingOutsideTheContractedKeyVocabulary() {
        let keys = Set(benchmarkOffTheMainThread().keys)
        XCTAssertTrue(
            keys.isSubset(of: Self.allowedKeys),
            "unexpected keys: \(keys.subtracting(Self.allowedKeys).sorted())"
        )
    }

    func testAndroidOnlyContractFieldsAreNeverEmittedFromIOS() {
        // `vendorName`, `shadingLanguageVersion` and `maxTextureSize` are `glGetString`/`glGetIntegerv`
        // reads with no Metal equivalent, and `apiVersion` is declared as "Metal GPU family" in the
        // shared type but is not read by this provider. Absent means not observed.
        let signals = benchmarkOffTheMainThread()
        for key in ["vendorName", "shadingLanguageVersion", "maxTextureSize", "apiVersion"] {
            XCTAssertNil(signals[key], "\(key) has no Metal counterpart and must stay absent on iOS")
        }
    }

    func testBenchmarkPerformedIsWrittenOnEveryPath() throws {
        // Three `return` points plus the `@catch`, and every one of them writes this key. The JS
        // side branches on it unconditionally.
        let signals = benchmarkOffTheMainThread()
        XCTAssertNotNil(signals["benchmarkPerformed"], "written on the simulator, skip, success and error paths")
        _ = try XCTUnwrap(signals["benchmarkPerformed"] as? NSNumber)
    }

    // MARK: - The skip shape

    func testASkippedRunCarriesAReasonAndNoMeasurements() throws {
        // The privacy-neutral but contract-relevant half: a skipped benchmark must not leave
        // half-written measurement keys behind, or a consumer would read a `drawCallsCompleted` that
        // no GPU produced. Each skip path returns immediately after writing its two keys.
        let signals = benchmarkOffTheMainThread()
        let performed = try XCTUnwrap(signals["benchmarkPerformed"] as? NSNumber).boolValue
        guard !performed else { return }

        let reason = try XCTUnwrap(signals["skippedReason"] as? String, "a skip always states its reason")
        XCTAssertTrue(Self.skipReasons.contains(reason), "unexpected skippedReason \(reason)")
        for key in Self.measurementKeys.sorted() {
            XCTAssertNil(signals[key], "\(key) must not appear when the benchmark did not run")
        }
    }

    func testSkippedReasonAppearsOnlyOnASkippedRun() throws {
        // The inverse. `skippedReason` is written on no successful path, so a completed benchmark
        // never carries one.
        let signals = benchmarkOffTheMainThread()
        let performed = try XCTUnwrap(signals["benchmarkPerformed"] as? NSNumber).boolValue
        if performed {
            XCTAssertNil(signals["skippedReason"], "a completed benchmark states no skip reason")
        }
    }

    func testTheSimulatorSelfSkipsAsAnEmulatorWithExactlyTwoKeys() throws {
        // The `#if TARGET_OS_SIMULATOR` branch, pinned exactly because it is the branch the routine
        // CI destination actually compiles. Its reason is "emulator" rather than "simulator": the
        // shared contract uses one vocabulary across platforms and Android reports the same word.
        #if targetEnvironment(simulator)
            let signals = benchmarkOffTheMainThread()
            XCTAssertEqual(Set(signals.keys), ["benchmarkPerformed", "skippedReason"])
            XCTAssertEqual(try XCTUnwrap(signals["benchmarkPerformed"] as? NSNumber).boolValue, false)
            XCTAssertEqual(signals["skippedReason"] as? String, "emulator")
            XCTAssertNil(signals["rendererName"], "no GPU name is claimed for a simulator")
        #else
            throw XCTSkip("the simulator self-skip branch is compiled out on this destination")
        #endif
    }

    // MARK: - The measurement shape (device-only, written so it is silent on a simulator)

    func testACompletedBenchmarkCarriesItsUnconditionalMeasurementKeys() throws {
        // Presence and type only — see the suite comment on why no value is asserted. On a device
        // these three are written together immediately after the loop.
        let signals = benchmarkOffTheMainThread()
        let performed = try XCTUnwrap(signals["benchmarkPerformed"] as? NSNumber).boolValue
        guard performed else { return }

        XCTAssertTrue(signals["drawCallsCompleted"] is NSNumber)
        XCTAssertTrue(signals["durationMs"] is NSNumber)
        if let renderer = signals["rendererName"] {
            let name = try XCTUnwrap(renderer as? String)
            XCTAssertFalse(name.isEmpty, "an empty device name is dropped rather than emitted")
        }
    }

    func testConditionalMeasurementKeysNeverAppearWithoutACompletedBenchmark() throws {
        // `gpuTimeMs` is written only when `GPUEndTime > GPUStartTime`, and the four statistics keys
        // only when `RNDISummarize` returned a summary. All of them are inside the success path, so
        // their appearance implies `benchmarkPerformed`.
        let signals = benchmarkOffTheMainThread()
        let performed = try XCTUnwrap(signals["benchmarkPerformed"] as? NSNumber).boolValue
        for key in Self.measurementKeys.sorted() where signals[key] != nil {
            XCTAssertTrue(performed, "\(key) appeared on a run that reported benchmarkPerformed = false")
        }
    }

    func testTheStatisticsKeysAppearAsAGroup() throws {
        // `operationTimeP50Ms`, `operationTimeP95Ms` and `operationTimeMadMs` are assigned together
        // inside `if (summary != nil)`, so two-out-of-three is not a reachable state and would mean
        // a key was renamed on one side of the borrow.
        let signals = benchmarkOffTheMainThread()
        let present = ["operationTimeP50Ms", "operationTimeP95Ms", "operationTimeMadMs"]
            .filter { signals[$0] != nil }
        XCTAssertTrue(present.isEmpty || present.count == 3, "partial statistics group: \(present)")
        if signals["operationTimeCoefficientOfVariation"] != nil {
            XCTAssertEqual(present.count, 3, "the coefficient of variation comes from the same summary")
        }
    }

    // MARK: - The fields borrowed from SignalStatistics

    func testTheProviderIndexesExactlyTheKeysSignalStatisticsPublishes() throws {
        // The provider reads `summary[@"median"]`, `summary[@"p95"]`, `summary[@"mad"]` and
        // `summary[@"coefficientOfVariation"]` by string. A rename inside `SignalStatistics` would
        // not break the build — the lookups would simply return nil and the four statistics fields
        // would silently vanish from the payload on real hardware, where no simulator test could see
        // it. Pinning the helper's published vocabulary is what catches that here.
        let summary = try XCTUnwrap(RNDISummarize([1, 2, 3, 4].map { NSNumber(value: $0) }))
        for key in ["median", "p95", "mad", "coefficientOfVariation"] {
            XCTAssertNotNil(summary[key], "GpuBenchmarkProvider indexes summary[\"\(key)\"]")
        }
        XCTAssertEqual(
            Set(summary.keys),
            ["sampleCount", "median", "p95", "mad", "coefficientOfVariation"],
            "the summary's vocabulary changed; check which borrowers still index the old names"
        )
    }

    func testTheProviderDoesNotForwardTheSummarysSampleCount() {
        // `sampleCount` is the fifth key `RNDISummarize` publishes and the only one the provider
        // deliberately drops: `drawCallsCompleted` already reports the loop's iteration count, and
        // `GpuBenchmarkSignals` declares no sample-count field. The vocabulary test above would not
        // catch it being forwarded, so this states it.
        XCTAssertFalse(Self.allowedKeys.contains("sampleCount"))
        XCTAssertNil(benchmarkOffTheMainThread()["sampleCount"])
    }

    func testWarmupSlopeIsTheSharedHelpersOptionalResult() throws {
        // `result[@"warmupSlope"] = warmupSlope` runs only `if (warmupSlope != nil)`, so the field's
        // omission rule is the helper's nullability rather than a rule of its own. Pinned against
        // the helper directly, because a simulator never reaches the call site.
        XCTAssertNil(RNDIWarmupSlope([NSNumber(value: 1.0)]), "a single sample has no two halves")
        XCTAssertNotNil(RNDIWarmupSlope([1.0, 1.0, 4.0, 4.0].map { NSNumber(value: $0) }))
    }

    // MARK: - Value types and boxing

    func testBenchmarkPerformedBoxesAsACFBoolean() throws {
        // `@NO`/`@YES` literals, so this is the easy case — but it is the one boolean this provider
        // emits and `node scripts/verify-ios-boolean-boxing.mjs` now scans this file's directory, so
        // the run-time counterpart of that static gate belongs here. CFBoolean identity is the only
        // check that separates the two boxes; `boolValue` agrees on both.
        let number = try XCTUnwrap(benchmarkOffTheMainThread()["benchmarkPerformed"] as? NSNumber)
        XCTAssertTrue(
            number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
            "benchmarkPerformed boxed as objCType \"\(String(cString: number.objCType))\" "
                + "(\(NSStringFromClass(type(of: number)))). It must be a CFBoolean, or it crosses "
                + "the bridge as 1/0 and fails schema validation."
        )
        XCTAssertEqual(String(cString: number.objCType), "c")
    }

    func testNoNumericFieldBoxesAsACFBoolean() throws {
        // The inverse guard: `drawCallsCompleted` and the timing fields are numbers in the contract,
        // and an over-broad "cast everything to BOOL" fix would collapse a draw-call count into
        // `true`. Type-level only — no value is asserted.
        let signals = benchmarkOffTheMainThread()
        for key in Self.measurementKeys.sorted() {
            guard let value = signals[key] else { continue }
            let number = try XCTUnwrap(value as? NSNumber, "\(key) must be an NSNumber")
            XCTAssertFalse(
                number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
                "\(key) is a number in the contract and must not box as a CFBoolean"
            )
        }
    }

    func testEveryEmittedValueHasItsContractedType() {
        let signals = benchmarkOffTheMainThread()
        XCTAssertTrue(signals["benchmarkPerformed"] is NSNumber)
        if let reason = signals["skippedReason"] { XCTAssertTrue(reason is String) }
        if let renderer = signals["rendererName"] { XCTAssertTrue(renderer is String) }
        for key in Self.measurementKeys where signals[key] != nil {
            XCTAssertTrue(signals[key] is NSNumber, "\(key) must be an NSNumber")
        }
    }

    // MARK: - Stability

    func testRepeatedRunsAgreeOnWhetherTheBenchmarkIsPossibleAtAll() throws {
        // Measurements legitimately differ between runs, so only the decision is asserted: a host
        // that can run the benchmark can run it twice, and one that self-skips skips for the same
        // reason. This is the shape assertion that survives on both a simulator and hardware.
        let first = benchmarkOffTheMainThread()
        let second = benchmarkOffTheMainThread()
        XCTAssertEqual(
            (first["benchmarkPerformed"] as? NSNumber)?.boolValue,
            (second["benchmarkPerformed"] as? NSNumber)?.boolValue
        )
        XCTAssertEqual(first["skippedReason"] as? String, second["skippedReason"] as? String)
        XCTAssertEqual(first["rendererName"] as? String, second["rendererName"] as? String)
    }
}
