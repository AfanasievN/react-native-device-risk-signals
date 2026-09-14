import XCTest

@testable import IOSDeviceRiskSignals

/// The iOS half of the project's thread-ownership contract, and the mirror image of
/// `GpuExecutionPolicyTest.kt` in the Android core.
///
/// **What this policy is for.** ADR-0005 decided that the binding owns dispatch and that providers
/// in this package declare their thread requirement and never dispatch on their own behalf. Android
/// already states both halves of that rule as executable preconditions:
/// `TransactionObservationSession.requireMainThread()` for work that *must* be on the UI thread, and
/// `GpuExecutionPolicy.requireWorker()` for work that must *not* be. `RNDIRequireWorkerThread` is
/// the inverse assertion arriving on iOS, in the same shape: it asserts and returns, it never
/// dispatches, and it never repairs a bad call by hopping to another queue. A GPU benchmark run on
/// the main thread would block the UI for its whole budget and would measure a contended thread, so
/// the honest answer is a rejected call, not a fabricated measurement.
///
/// **Why the thread is a parameter.** `RNDIRequireWorkerThread(BOOL)` takes the observation rather
/// than reading `+[NSThread isMainThread]` itself, exactly as Kotlin's `requireWorker(isMainThread:
/// Boolean)` does. That is what lets both directions be exercised from one thread — the Kotlin test
/// makes the same choice — and it keeps the decision of *how* the thread is observed at the call
/// site, where ADR-0005 puts the dispatch.
///
/// **Why an exception and not `NSAssert`.** `NSAssert` is unavailable in a C function, and
/// `NS_BLOCK_ASSERTIONS` compiles `NSCAssert` out of a release build — a contract that evaporates in
/// the configuration that ships is not a contract. A raised
/// `NSInternalInconsistencyException` is unconditional, and it is the same category of failure as
/// Kotlin's `IllegalStateException` from `check`.
final class GpuExecutionPolicyTests: XCTestCase {
    /// The message, byte for byte the one the Android core raises.
    private static let message = "GPU collection requires a worker thread"

    // MARK: - Both directions of the precondition

    func testAcceptsAWorkerThread() {
        // The permitted direction. Nothing is raised and nothing is returned; a satisfied
        // precondition is silent.
        let failure = RNDIExecutionPolicyFailure { RNDIRequireWorkerThread(false) }
        XCTAssertNil(failure, "a worker thread is the supported caller and must pass silently")
    }

    func testRejectsTheMainThread() {
        // The rejecting direction. `GpuExecutionPolicyTest.rejectsUiThreadBeforeAnyGpuWork` asserts
        // the same thing against `IllegalStateException`.
        let failure = RNDIExecutionPolicyFailure { RNDIRequireWorkerThread(true) }
        let exception = failure
        XCTAssertNotNil(exception, "the main thread must be rejected, not accommodated")
        XCTAssertEqual(exception?.name.rawValue, NSExceptionName.internalInconsistencyException.rawValue)
        XCTAssertEqual(exception?.reason, Self.message)
    }

    func testTheRejectionIsIndependentOfTheThreadTheCheckRunsOn() {
        // The parameter is the whole input: calling it from a real background thread with `true`
        // still fails, and calling it from the main thread with `false` still passes. This is what
        // stops the function from being "fixed" into reading `+[NSThread isMainThread]` internally,
        // which would silently make the Android-shaped unit test unwritable.
        XCTAssertTrue(Thread.isMainThread, "XCTest runs test methods on the main thread")
        XCTAssertNil(RNDIExecutionPolicyFailure { RNDIRequireWorkerThread(false) })

        let done = expectation(description: "policy evaluated on a background thread")
        var backgroundFailure: NSException?
        var backgroundPass: NSException?
        DispatchQueue.global(qos: .userInitiated).async {
            backgroundFailure = RNDIExecutionPolicyFailure { RNDIRequireWorkerThread(true) }
            backgroundPass = RNDIExecutionPolicyFailure { RNDIRequireWorkerThread(false) }
            done.fulfill()
        }
        wait(for: [done], timeout: 5)

        XCTAssertNotNil(backgroundFailure, "the argument decides, not the calling thread")
        XCTAssertNil(backgroundPass)
    }

    func testThePolicyNeverDispatchesToRepairABadCall() {
        // "Assert a precondition, never dispatch" is the load-bearing half of ADR-0005: a policy
        // that quietly hopped to a worker queue would make every caller's thread choice invisible
        // and would turn a synchronous contract into an asynchronous one. Observable proof: the
        // rejecting call returns control on the very thread that made it, before any queue could
        // have run anything, and the accepting call does the same.
        let thread = Thread.current
        _ = RNDIExecutionPolicyFailure { RNDIRequireWorkerThread(true) }
        XCTAssertEqual(Thread.current, thread, "the policy must not move the caller off its thread")
        RNDIRequireWorkerThread(false)
        XCTAssertEqual(Thread.current, thread)
    }

    // MARK: - Keeping the test seam honest

    func testTheSeamReturnsNilWhenTheBlockCompletesNormally() {
        // A seam that always returned nil would make `testAcceptsAWorkerThread` pass by observing
        // nothing, so it is pinned in both directions itself — the same reason
        // `AudioLatencyProviderTests` proves its swizzle really intercepts.
        var ran = false
        let failure = RNDIExecutionPolicyFailure { ran = true }
        XCTAssertTrue(ran, "the block must actually be invoked")
        XCTAssertNil(failure)
    }

    func testTheSeamReturnsTheExceptionAnArbitraryBlockRaises() {
        let failure = RNDIExecutionPolicyFailure {
            NSException(name: .invalidArgumentException, reason: "control", userInfo: nil).raise()
        }
        XCTAssertEqual(failure?.name.rawValue, NSExceptionName.invalidArgumentException.rawValue)
        XCTAssertEqual(failure?.reason, "control")
    }

    func testTheSeamIsWhyThisSuiteExistsInObjectiveCRatherThanSwift() {
        // Documentation with an assertion attached. Swift's `do/catch` cannot catch an Objective-C
        // exception and `XCTAssertThrowsError` only sees Swift `Error`s, so without a catch on the
        // Objective-C side the rejecting direction of this contract would be untestable from a
        // Swift-only test target — and the test target is Swift-only. The seam is therefore part of
        // the package rather than of the tests, and is scoped by name to this policy so it cannot be
        // mistaken for a general-purpose escape hatch.
        XCTAssertNotNil(
            RNDIExecutionPolicyFailure { RNDIRequireWorkerThread(true) },
            "if this ever returns nil the rejecting direction has stopped being observable"
        )
    }
}
