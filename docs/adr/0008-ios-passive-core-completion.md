# ADR-0008: Complete the socket-free iOS core without changing RN observations

- Status: accepted; experimental release authorized, production gate unchanged
- Date: 2026-09-29

## Decision

Move hardware/fonts, geolocation, media/app visibility and security/transaction posture to the
existing iOS core. Extract OsIntegrityProvider without loopback connections or fork. Add a
synchronous DeviceRiskSignals facade with sixteen explicit methods, no automatic collection,
no score, transport, default collect-all, or framework types. Existing published provider APIs
remain available. No package rename or new component is introduced.

The newly extracted UI-bound methods require the main thread and fail before collection on a
worker, following ADR-0005. Fonts are callable off-main; GPU remains worker-only. The published
DeviceInfoProvider keeps its main-thread hop for compatibility. Hosts must not block main while
waiting for a worker that calls it. RN owns dispatch for the newly extracted methods.

The existing RN JailbreakDetector becomes a compatibility compositor: shared core observations,
then the legacy loopback observations. Its separate fork method stays disabled by the JS probe's
existing default. Neither active implementation is included in the Swift Package. Moving them
into a separately distributed optional iOS component, improving bounded connect behavior and
physical-device QA remain release gates, not a reason to silently change existing RN output.
This resolves the passive core boundary from ADR-0003 but not the legacy RN active-probe boundary.

## Privacy and behavior

No new observation fields or permission prompts are introduced. Location remains cached and
requires an existing host grant; missing data is omitted. Finite URL scheme visibility requires
host Info.plist declarations and is not a complete installed-app inventory. Accessibility is
sensitive context, not evidence of dishonesty. Fonts, location, media, posture and transaction
collection are explicit native calls only. No new default-on probe is added.

OS integrity preserves existing file, process, image, scheme and sandbox-write observations,
but omits openReverseEngineeringPorts (not an empty list or false) and does not expose fork.
Socket-free does not mean side-effect-free: the existing sandbox write test attempts a temporary
file outside the container and removes it on success; hardware briefly enables battery monitoring
and restores its previous state. These are inherited behaviors, not new signals.

Published iOS 0.1.0 has nine collectors. On 2026-09-29 the maintainer explicitly authorized an
experimental beta release without physical-iPhone QA. iOS 0.2.0-beta.1 and React Native
0.10.0-beta.1 are evaluation releases, marked prerelease on GitHub; npm uses next, never latest.
Native/RN compilation, tests and package verification remain required. Physical-device QA is
still a gate for promotion to a non-prerelease version; it is not claimed or waived for production.
