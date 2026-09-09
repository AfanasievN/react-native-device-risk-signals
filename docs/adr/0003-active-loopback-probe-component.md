# ADR-0003: Active loopback observation lives in a separate optional component

- Status: accepted for the in-development components; publication, defaults and device QA remain gated
- Date: 2026-09-09
- Follows: [ADR-0002](0002-explicit-android-transaction-session.md)

## Decision

The legacy React Native localhost Frida scan does not move into `sdks/android/`, and the passive
core does not gain a socket API. Active observation becomes its own optional Android component,
`android-active-probes-device-risk-signals` under `sdks/android-active-probes/`, whose contract
explicitly permits loopback socket I/O. `DeviceRiskActiveProbes.collectFridaScan()` is its only
call. The React Native binding consumes that component and keeps `getFridaScanSignals()` as a thin
delegation, so `android/src/main/java/com/reactnativedeviceintel/FridaScanProvider.kt` is deleted
rather than relocated into the core.

The boundary is now stated per component instead of per repository. The passive core performs no
socket I/O of any kind and keeps reporting a LISTEN socket on 27042/27043 read from `/proc/net/tcp`
(`fridaListenerPortFound`). The active component may connect, but only to `127.0.0.1`, only to the
single documented port, with no hostname resolution beyond that address, no off-device request, no
vendor endpoint and no port scanning. It declares no Android permission and no manifest entry. A host that wants passive collection only adopts the core and never takes on
this component. Both components remain unpublished and in development.

Its calls block, so the caller runs them on a background thread; a main-thread call raises
`NetworkOnMainThreadException`. React Native already dispatches native probes to a background
executor, which is why the existing probe never tripped that. No `INTERNET` declaration, runtime
dependency, persistent identifier, score, verdict or non-loopback address is introduced by this
decision, and the component adds no AndroidX or instrumented-test runtime.

This decision covers Android only. iOS still performs an active loopback connect inside
`ios/JailbreakDetector.m` for `openReverseEngineeringPorts` (ports 27042/4444/22/44) as part of the
`os_integrity` probe. That is the same boundary conflict and is deliberately left open: it must be
resolved when iOS extraction starts, under this ADR's component rule, before `sdks/ios/` exists.

## Raw contract and compatibility

Nothing observable changes in this step. The `os_integrity_frida_scan` probe id, its four fields
(`scanPerformed`, `defaultPortOpen`, `scannedPort`, `fridaHandshakeReject`), their types, the
`FridaScanSignals` TypeScript type, the 19-method TurboModule contract, the event envelope and
schema version, the iOS `scanPerformed: false` stub and the probe's current React Native default all
stay exactly as they are. The Kotlin behavior was ported byte-for-byte: same host, same port
27042, the same 700 ms value used as both connect and read timeout, the same single read of up to
six bytes, the same `REJECT` prefix check and the same swallow-to-false on any failure.

The probe's known ambiguities are not fixed by relocation and remain open work, now recorded on the
component itself: a partial or slow response reads as no REJECT because the prefix is never
reassembled across reads; `fridaHandshakeReject = false` collapses read timeout, reset, EOF and
write failure together with a listener that answered something else; `defaultPortOpen = false`
collapses connection refused, connect timeout and any other socket failure, so it does not separate
"nothing listens" from "we could not find out"; and a `REJECT`-like reply authenticates no service.
Fixing any of these changes emitted meaning and therefore requires its own tests, contract and
privacy updates, and breaking-release notes.

Two questions are explicitly not decided here. Whether an active probe should stay enabled by
default is a product decision, deferred so this change flips no default silently. Whether the
component is ever published to Maven Central is gated on the same release gates as the core;
`published` stays false, no component-prefixed tag is created, and no workspace is enabled.

## Correction, 2026-09-09

This ADR originally stated that no Android permission is required. That is wrong, and a native demo
built for the component proved it: an application that has not declared `INTERNET` is not in the
`inet` group and cannot open a socket at all, loopback included. With a `REJECT` listener running on
127.0.0.1:27042, a host without `INTERNET` reported `defaultPortOpen: false` and
`fridaHandshakeReject: false`; the same host with `INTERNET` declared reported both true. The
component still declares no permission, which the AAR package-content gate enforces, but the probe
is inert without a host declaration and its failure is indistinguishable from nothing listening.
This makes the collapsed-false defect above materially worse and is now recorded in the probe
catalog, the data dictionary and the component README. `INTERNET` is a normal permission and raises
no prompt; React Native hosts almost always declare it, which is why the existing probe appeared to
work. Whether the SDK should surface "cannot open a socket" as a distinct unavailable outcome is
part of the open defect work, not decided here.

## Verification and remaining gates

The component ships with JVM tests that drive the collector against a local `ServerSocket` on an
ephemeral port through internal host/port/timeout seams: REJECT reply, non-REJECT reply, an accepted
connection that never answers, an immediate close after accept, and a closed port, plus raw-model
omission behavior. Production defaults are never altered by the suite and port 27042 is not touched.
A mutation check confirmed the REJECT assertion is load-bearing. React Native compilation, the
native-contract parity check and the repository verification ring pass with the provider deleted.

JVM socket tests are not device validation. Behavior against a real frida-server, OEM builds,
IPv6-only loopback stacks, hosts with a captive local proxy, and latency under load still require
physical-device QA. The component has no CI job of its own beyond the build and test command added
to the existing Android workflow step, no instrumented tests, and no published artifact. Neither
Maven publication nor iOS resolution is implied by this decision.
