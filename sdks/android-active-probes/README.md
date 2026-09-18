# android-active-probes-device-risk-signals (in development)

Optional Android component for **active** device-risk observations. It is separate from
[`android-device-risk-signals`](../android/README.md) for one reason: it performs loopback socket
I/O, and the passive core never opens a socket. A host that wants passive collection only can adopt
the core and never take this component's behavior. The decision and its limits are recorded in
[ADR-0003](../../docs/adr/0003-active-loopback-probe-component.md).

This component is in development and unpublished. There is no Maven artifact to install.

## Contract

- Loopback only: `127.0.0.1`, the single documented port, no hostname resolution beyond that, no
  off-device request, no vendor endpoint and no port scanning.
- The module declares, requests and needs no permission of its own, but the probe only works if the
  host application already declares `INTERNET`. An app that is not in the `inet` group cannot open a
  socket at all, loopback included, so the scan then reports `defaultPortOpen: false` and
  `fridaHandshakeReject: false` even while a listener is up. `INTERNET` is a normal permission and
  raises no prompt; React Native applications almost always declare it already.
- Calls block. Run them on a background thread; a main-thread call raises
  `NetworkOnMainThreadException`.
- Raw observations only. Nothing here scores, aggregates or returns a trusted/untrusted verdict, and
  no persistent identifier is produced.

## Declared default: the frida scan is ENABLED

This component has no probe registry and no configuration object, so a native host simply gets what
it calls. The default is therefore declared rather than switched, in code and here:

| Probe | Declared default | Constant |
| --- | --- | --- |
| `os_integrity_frida_scan` | **enabled** | `DeviceRiskActiveProbes.FRIDA_SCAN_ENABLED_BY_DEFAULT` |

`DeviceRiskActiveProbes.FRIDA_SCAN_PROBE_ID` carries the catalog id. A host assembling its own probe
set should read those constants instead of hard-coding `true`. The declaration is pinned by
`DeviceRiskActiveProbesDefaultsTest` against `enabledByDefault` for the same probe in
[`contract/source/probe-catalog.source.json`](../../contract/source/probe-catalog.source.json), so
the component and the React Native package cannot drift apart silently; the catalog file is a
declared input of the test task, so editing it re-runs the pin.

### Why this is an exception to "new sensitive probes ship disabled"

[`AGENTS.md`](../../AGENTS.md) requires a new sensitive or expensive probe to ship **disabled** until
representative physical-device QA and a documented benchmark justify enabling it. This probe is
exempt for exactly one reason: **it is not new.** It shipped enabled in the React Native package
before extraction, and [ADR-0003](../../docs/adr/0003-active-loopback-probe-component.md)
deliberately preserved that default so the extraction flipped nothing silently. Switching it off
here would itself be the behavior change — made by a refactor, not by a product decision.

Be equally plain about what that exemption does not claim:

- **Representative physical-device QA has NOT happened.** Behavior against a real frida-server, OEM
  builds, IPv6-only loopback stacks, hosts with a captive local proxy and latency under load is
  still unverified. JVM socket tests are not device validation.
- **No benchmark justifies the cost.** What exists is a documented worst case (below), not a
  measurement.
- **"Enabled by default" is not a recommendation.** This is the only socket I/O in the ecosystem, it
  is inert without host-declared `INTERNET`, and its observations prove less than they look like
  they prove — see "What these observations do not prove".

Whether an active probe *should* stay on by default is still an open product question in
[the migration checklist](../../docs/MIGRATION_ROADMAP.md). Until that is answered, the answer in
code is the constant above.

## Currently extracted

- `DeviceRiskActiveProbes.collectFridaScan()`

```kotlin
// Inside the host's background worker, after explicit opt-in:
val activeProbes = DeviceRiskActiveProbes()
val raw = activeProbes.collectFridaScan().toRawMap()
```

It attempts one TCP connect to `127.0.0.1:27042` and an unauthenticated `AUTH` handshake, and
reports `scanPerformed`, `scannedPort`, `defaultPortOpen` and `fridaHandshakeReject`.

### Timeout budget

Connect and read have separate budgets, so a slow connect cannot eat the time available to read the
reply and a silent listener cannot hold the call for the connect budget:

| Phase | Budget | Notes |
| --- | --- | --- |
| Connect | 700 ms | The TCP connect only. Unchanged from the React Native provider. |
| Read | 800 ms | A budget for the **whole** handshake read, not per `read` call. Each read gets whatever is left of it, so a listener that trickles bytes cannot extend the call. |

The worst case for one `collectFridaScan()` is **1500 ms** (`FridaScanCollector.WORST_CASE_TIMEOUT_MS`)
plus scheduling. A connect that fails or times out never goes on to spend the read budget.

### Breaking behavior change: `fridaHandshakeReject` means more than it used to

The ported implementation performed exactly one `read` of up to six bytes with a single 700 ms value
used as both connect and read timeout. The reply is a TCP stream, not a message, so a reply that
arrived in pieces — or later than that one shared value — read as "no REJECT".

The handshake reply is now reassembled across reads until the six-byte prefix is decided or the read
budget is spent, and the two budgets are separate. **`fridaHandshakeReject` is therefore `true` for
responses that previously reported `false`:**

- a `REJECT` line delivered across more than one TCP segment, or trickled out byte by byte;
- a `REJECT` line that arrives later than the old shared 700 ms value but within the 800 ms read
  budget.

Nothing else changed: the emitted field names, their types, the host, the port and the `REJECT`
prefix itself are all unchanged, and a mismatching byte still ends the read immediately. Consumers
that stored historical values must not compare them across this change — a `false` recorded before
it and a `true` recorded after it can describe the same device and the same listener. The observed
rate of `fridaHandshakeReject = true` is expected to rise.

## What these observations do not prove

Behavior was ported unchanged from the React Native provider, including its defects. Read them
before interpreting a result:

- A `REJECT`-like reply authenticates nothing. Any listener can answer with those bytes.
- `fridaHandshakeReject = false` collapses read timeout, reset, EOF and write failure together with
  a listener that simply answered something else.
- `defaultPortOpen = false` collapses connection refused, connect timeout and any other socket
  failure. It does not distinguish "nothing listens" from "we could not find out".

One of the ported defects is now **fixed**: the prefix is reassembled across reads and connect and
read have separate budgets, so a partial or slow reply is no longer misread as "no REJECT". That
changed emitted meaning; see "Breaking behavior change" above.

The passive core separately reports a LISTEN socket on 27042/27043 seen in `/proc/net/tcp`
(`fridaListenerPortFound`) without any socket I/O. Prefer that observation when a host does not want
an active probe at all.

## Native example

`example/` is a one-button Android app that consumes this component directly, with no React Native.
The passive core's example stays socket-free by design, so the active probe is demonstrated here
instead. The demo host declares `INTERNET` itself, with a comment saying why; the component still
declares nothing. Collection runs on a worker thread and the button is disabled until it returns.

```sh
example/android/gradlew -p sdks/android-active-probes :example:installDebug --no-daemon
```

To see the positive branch on a device or emulator, put a listener on the port first and press the
button again:

```sh
adb shell "echo 'REJECT frida' | nc -L -p 27042 -s 127.0.0.1"
```

## Local publication

The component publishes a release AAR, a sources jar, a Dokka-rendered javadoc jar and full POM
metadata to a file repository
inside its own build output, so artifact consumption can be verified without any registry:

```sh
example/android/gradlew -p sdks/android-active-probes :publishReleasePublicationToLocalBuildRepository --no-daemon
```

The result lands in `sdks/android-active-probes/build/local-maven` under ``io.github.afanasievn:android-active-probes-device-risk-signals``.
Nothing is written to `~/.m2` and no credentials are involved. The javadoc jar is rendered from the
KDoc rather than being an empty placeholder, so it satisfies Maven Central's requirement with real
documentation. A real Maven Central release still needs signing, Sonatype namespace verification,
the staging flow and a version other than `0.1.0-SNAPSHOT`; those live outside this repository and
are tracked in [the migration checklist](../../docs/MIGRATION_ROADMAP.md).

## Build and test

From the repository root, reusing the existing Gradle wrapper:

```sh
example/android/gradlew -p sdks/android-active-probes :testDebugUnitTest :assembleRelease --no-daemon
example/android/gradlew -p sdks/android-active-probes :example:assembleDebug --no-daemon
```

JVM tests drive the collector against a local `ServerSocket` on an ephemeral port through internal
host/port/connect-timeout/read-timeout seams; the production defaults are never changed and port
27042 is not touched by the suite. They cover a `REJECT` reply dribbled out one byte at a time, a
reply that arrives long after the connect budget, a silent listener that must end on the read budget
rather than the connect budget, a connect that never completes (a backlog of one that is never
accepted) and must not go on to spend the read budget, and a reply truncated by EOF mid-prefix. Physical-device QA and the publication gates in
[the migration checklist](../../docs/MIGRATION_ROADMAP.md) still apply.
