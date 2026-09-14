# ADR-0005: iOS dispatch ownership and the module method queue

- Status: accepted for the queue; the provider-side thread contract is decided but not yet implemented
- Date: 2026-09-14
- Follows: [ADR-0002](0002-explicit-android-transaction-session.md)

## Decision

The React Native iOS module owns thread dispatch, as the Android binding already does. `DeviceIntel`
declares its own concurrent `methodQueue`. Providers in `sdks/ios/` will declare their thread
requirement and never dispatch on their own behalf, mirroring Android, where
`TransactionObservationSession.requireMainThread()` and `GpuExecutionPolicy.requireWorker()` assert a
precondition and the binding satisfies it. The main-thread hops currently living inside the
unextracted `ios/` providers move to the binding as each provider is extracted.

An asynchronous or completion-handler iOS API was considered and rejected for now. It is the better
API in isolation, but the Android facade is synchronous and returns raw models, and diverging the two
public shapes would complicate the shared-contract and conformance-fixture work before it has begun.
It can be layered on later without undoing this decision.

## Why the queue mattered

A module that declares no `methodQueue` is assigned React Native's `_sharedModuleQueue`, a single
serial queue shared with every other module in the host app that also declares none. The JavaScript
runner starts all enabled probes together and begins each probe's timeout at dispatch rather than
when native work starts, so probes were serializing behind one another and spending their budgets
waiting. Android diagnosed and fixed the same defect earlier; its module comment records that only
the first probe used to succeed while the rest reported `timeout` merely from queueing.

Measured on a scratch harness with four 200 ms probes: the shared serial queue took 818 ms with a
maximum concurrency of 1; a module-owned concurrent queue took 205 ms with a maximum concurrency of
4. React Native honours the queue for promise methods: `isMethodSync` excludes `PromiseKind`, and
`invokeAsync` is a plain `dispatch_async` onto the module's queue.

## Consequences

Probes that previously reported `timeout` purely from queueing should now return data. That changes
payload contents for affected consumers, so it is a behavior change rather than an optimization, and
it is recorded in the changelog rather than folded into an extraction commit.

Provider methods can now be entered concurrently. The providers are stateless per call except for one
process-global: `HardwareInfoProvider` enables battery monitoring, reads, and restores the previous
setting. A measurement showed that sequence leaking in 400 of 400 trials once callers overlap, and
also showed that today's main-thread hop already serializes it, so the leak is not reachable yet. The
toggle is now guarded by its own lock anyway, because the serialization is incidental and the hop it
depends on is exactly what this ADR plans to move.

## Open items

The contradiction between `DeviceInfoProvider`, which reads `UIDevice` off the main thread, and
`HardwareInfoProvider`, which documents that `UIDevice` requires the main thread and hops for it,
must be resolved before either is extracted; both cannot be right. `ios/` still has no test target,
so changes there are verified by compilation, scratch harnesses and the example app build rather than
by automated tests. Physical-device validation is still required for the cost of the LaunchServices
`canOpenURL:` reads, font enumeration off the main thread, and whether the tightest probe budgets
hold under a host app doing real UI work. A stale checkout needs `pod install` in `example/ios`
before building, because the Pods project can still reference provider paths from before extraction.
