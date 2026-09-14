#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Execution-thread contract for GPU collection.
 *
 * This is the inverse of the rule the transaction-time providers follow, and the iOS mirror of
 * `GpuExecutionPolicy.requireWorker` in the Android core
 * (`sdks/android/src/main/kotlin/io/github/afanasievn/devicerisksignals/GpuExecutionPolicy.kt`).
 * ADR-0005 decided that the binding owns dispatch and that a provider here declares its thread
 * requirement and never dispatches on its own behalf: this function asserts a precondition and
 * returns, it never hops to another queue to repair a bad call, and it never downgrades the call to
 * a fabricated "skipped" result. GPU work on the main thread would block the UI for the whole
 * benchmark budget and would measure a contended thread, so a rejected call is the honest answer.
 *
 * The thread is a parameter rather than an internal `+[NSThread isMainThread]` read, exactly as
 * Kotlin's `requireWorker(isMainThread: Boolean)`. That keeps the observation at the call site,
 * where ADR-0005 puts dispatch, and it is what makes both directions of the contract testable from
 * a single thread.
 *
 * `NSAssert` is deliberately not used: it is unavailable in a C function, and `NS_BLOCK_ASSERTIONS`
 * compiles `NSCAssert` out of a release build, so the contract would evaporate in the configuration
 * that ships. The raised `NSInternalInconsistencyException` is unconditional and is the same
 * category of failure as Kotlin's `IllegalStateException` from `check`, carrying the same message.
 */
FOUNDATION_EXPORT void RNDIRequireWorkerThread(BOOL isMainThread);

/**
 * Invokes `block` and returns the `NSException` it raised, or `nil` if it returned normally.
 *
 * A test seam, and scoped by name to this policy so it is not mistaken for a general-purpose
 * exception swallower — nothing in this package's collection paths calls it. It exists because
 * Swift cannot catch an Objective-C exception (`do`/`catch` and `XCTAssertThrowsError` see only
 * Swift `Error`s) and this package's test target is Swift-only, so without a catch on the
 * Objective-C side the rejecting direction of the contract above would be unobservable from the
 * tests and the policy would be no better than the comment it replaces.
 */
FOUNDATION_EXPORT NSException *_Nullable RNDIExecutionPolicyFailure(void (NS_NOESCAPE ^block)(void));

NS_ASSUME_NONNULL_END
