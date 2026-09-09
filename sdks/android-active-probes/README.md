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
- No Android permission is required, requested or declared; the module's manifest declares nothing.
- Calls block. Run them on a background thread; a main-thread call raises
  `NetworkOnMainThreadException`.
- Raw observations only. Nothing here scores, aggregates or returns a trusted/untrusted verdict, and
  no persistent identifier is produced.

## Currently extracted

- `DeviceRiskActiveProbes.collectFridaScan()`

```kotlin
// Inside the host's background worker, after explicit opt-in:
val activeProbes = DeviceRiskActiveProbes()
val raw = activeProbes.collectFridaScan().toRawMap()
```

It attempts one TCP connect to `127.0.0.1:27042` and an unauthenticated `AUTH` handshake, bounded by
a 700 ms connect and read timeout, and reports `scanPerformed`, `scannedPort`, `defaultPortOpen` and
`fridaHandshakeReject`.

## What these observations do not prove

Behavior was ported unchanged from the React Native provider, including its defects. Read them
before interpreting a result:

- A `REJECT`-like reply authenticates nothing. Any listener can answer with those bytes.
- The handshake performs exactly one read of up to six bytes. A partial or slow response reads as
  "no REJECT"; the prefix is never reassembled across reads.
- `fridaHandshakeReject = false` collapses read timeout, reset, EOF and write failure together with
  a listener that simply answered something else.
- `defaultPortOpen = false` collapses connection refused, connect timeout and any other socket
  failure. It does not distinguish "nothing listens" from "we could not find out".

The passive core separately reports a LISTEN socket on 27042/27043 seen in `/proc/net/tcp`
(`fridaListenerPortFound`) without any socket I/O. Prefer that observation when a host does not want
an active probe at all.

## Build and test

From the repository root, reusing the existing Gradle wrapper:

```sh
example/android/gradlew -p sdks/android-active-probes :testDebugUnitTest :assembleRelease --no-daemon
```

JVM tests drive the collector against a local `ServerSocket` on an ephemeral port through internal
host/port/timeout seams; the production defaults are never changed and port 27042 is not touched by
the suite. Physical-device QA and the publication gates in
[the migration checklist](../../docs/MIGRATION_ROADMAP.md) still apply.
