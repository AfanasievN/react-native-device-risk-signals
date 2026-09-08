# ADR-0002: Explicit Android transaction observation lifecycle

- Status: accepted for the in-development SDK; publication and device QA remain gated
- Date: 2026-09-08
- Follows: [ADR-0001](0001-platform-sdk-monorepo.md)

## Decision

The Android SDK owns both the synchronous `collectTransactionSafety()` raw snapshot and an
independent `TransactionObservationSession`. The snapshot does not start observers. The session
constructor is inactive; hosts call `attach(Activity)`, `detach()` and terminal, idempotent `close()`
on the main thread. `snapshot()` is thread-safe and nullable until first attachment. One session
retains cumulative history across detach/reattach; create a new session to begin a new history.

The React Native binding retains lazy opt-in collection, supplies the current Activity and UI
dispatch, detaches on host pause/destroy, and closes on module invalidation. Resuming the host does
not automatically restart collection: a subsequent enabled collection attaches again. Queued UI
collection is canceled if the binding's wait times out or is interrupted before execution begins.
Already running platform work is not forcibly canceled; cleanup remains queued independently.

Callbacks carry a per-attachment generation. Detach/close invalidates old callbacks even if a
third-party window wrapper retains them. Touch callbacks still forward to the previous callback
without filtering input or changing the returned result. New API 34/35 classes remain isolated
from the session's field signatures for the API 24 floor.

No network operations, keys, persistent identifiers, prompts, new runtime dependencies or manifest
permissions are added. Capture registration uses only host-declared permissions. A screenshot may
produce the operating system's standard detection notice; that is not a permission prompt.

## Raw contract and compatibility

The eight point-in-time and eleven session wire keys retain their names/types, including
`isScreenCaptured` as the Android recording-visibility alias. The event envelope/schema version,
19-method TurboModule contract, component names and disabled transaction default do not change.

Intentional behavior corrections must be called out in the next breaking migration release:

- Android below API 29 omits partial-obscuration fields instead of synthesizing false after a touch.
- Detach clears current screenshot coverage and recording visibility; an unobserved screenshot
  negative is omitted without coverage. Historical screenshot positives/timestamps and touch
  evidence survive detach/close and are not proof of current capture or continuous coverage.
- Background/paused hosts no longer keep transaction registrations active; the next collection
  after resume reattaches while retaining the original history start timestamp.
- Timed-out queued attachment and stale callbacks no longer extend observation silently.

Consumers must distinguish omission from false and must not treat session-start timestamps as
proof of continuous observation. RN currently has no public per-payment reset method; do not
promise an independent session per payment. The standalone native API supplies that ownership.
Do not publish these migration semantics as an undocumented patch to 0.8.1.

## Verification and remaining gates

Pure JVM regressions cover serialization, unsupported partial flags, stale coverage, cumulative
history, generation invalidation and terminal close. RN queue tests cover timeout/cancel and
single execution. A native example exposes explicit start/read/stop actions and cleans up on
stop/destroy. Compilation is not physical-device validation: callback registration failures,
permission revocation, nested window wrappers, activity recreation and OEM behavior still require
instrumented/physical-device QA before release. Neither native SDK publication nor additional
framework binding implementation is implied by this decision.
