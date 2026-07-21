# Device signal data dictionary

This document describes what each probe can collect and why. The machine-readable source of truth is
the exported `PROBE_CATALOG` constant in `src/probeCatalog.ts`. Applications can use it to build
configuration, consent, disclosure, and data-minimization interfaces.

The SDK performs no upload and requests no permissions. A probe may opportunistically use information
that is already available to the host application, as noted below. Every successful result is nested
under `RawSignalEvent.probes[probeId].data`; unavailable probes return an explicit `skipped`, `timeout`,
or `error` outcome.

| Probe | Platforms | Default | Sensitivity | Data categories | Permission behavior |
| --- | --- | --- | --- | --- | --- |
| `device_identity` | Android, iOS | On | High | Device, operating system | None |
| `hardware` | Android, iOS | On | High | Hardware, display, battery, storage | None |
| `fonts` | Android, iOS | On | High | Installed-font digest | None |
| `os_integrity` | Android, iOS | On | High | Device integrity, runtime security | None |
| `os_integrity_frida_scan` | Android | On | Moderate | Runtime security | None; scans localhost only |
| `os_integrity_fork_test` | iOS | Off | High | Device integrity | None; disabled pending device-lab validation |
| `network` | Android, iOS | On | High | Network, local IP address | Uses host-declared `ACCESS_NETWORK_STATE`; no Wi-Fi location permission requested |
| `telephony` | Android, iOS | On | High | Carrier, SIM, country | Uses already granted `READ_PHONE_STATE` only for SIM count; never requests it |
| `locale` | Android, iOS | On | Moderate | Locale, timezone, keyboard language | None |
| `geolocation` | Android, iOS | On | High | Location, location authorization | Uses only permission already granted to the host app; never prompts |
| `media_bluetooth_apps` | Android, iOS | On | High | Media route, Bluetooth, accessibility, finite known-app list | Uses already granted `BLUETOOTH_CONNECT`; no broad package visibility |
| `gpu_benchmark` | Android, iOS | Off | High | GPU, performance | None; disabled pending calibration |
| `audio_latency` | Android, iOS | Off | High | Audio hardware, performance | None; disabled pending calibration |
| `application` | Android, iOS | On | Moderate | App identity, install provenance, granted permissions | Reads the host application only |
| `device_security_posture` | Android, iOS | On | Moderate | Lock capability, biometrics availability, trusted time, OS security update | None; never displays an authentication prompt |
| `transaction_safety` | Android, iOS | Off | High | Lock/interactive state, screen capture, accessibility, call/audio state, finite known-app list | None; disabled pending physical-device calibration |
| `runtime` | Android, iOS | On | Low | React Native runtime, software version | None |
| `runtime_timing` | Android, iOS | Off | High | Runtime and performance timing | None; bounded active workload |
| `numeric_consistency` | Android, iOS | Off | High | Cross-runtime numerical behavior | None; bounded active workload |

## Field inventory

Each descriptor in `PROBE_CATALOG` contains its complete top-level `fields` array. These are the same
names accepted by `ProbeOverride.fields.include` and `ProbeOverride.fields.exclude`.

```ts
import {getProbeDescriptor, PROBE_CATALOG} from "react-native-device-risk-signals";

const networkFields = getProbeDescriptor("network")?.fields;
const highSensitivity = PROBE_CATALOG.filter((probe) => probe.sensitivity === "high");
```

Field projection happens after a probe collects its native payload. Disable the entire probe when the
application must avoid touching a sensitive or expensive data source at all.

### Application provenance

`application` now exposes Android SDK targets, debuggable/instant-app state, SHA-256 signing
certificate observations and signing history, plus iOS receipt presence/environment, minimum OS
version, executable name, extension state, and simulator-build state. Certificate values describe
the host application's public signing certificates; no private key or device identifier is read.

### OS integrity and transaction context

`os_integrity` includes independent raw observations for tracing, test-key builds, suspicious
mounts, Zygisk indicators, executable mappings, suspicious environment-variable names, and evidence
counts. Emulator observations now include matched build markers, discovered emulator file paths,
QEMU or virtual-hardware property markers, CPU markers, recognized emulator-family markers, sensor
availability, Android Test Harness Mode, Firebase Test Lab presence, and iOS simulator or XCTest
environment presence. Broad observations such as `test-keys`, an `unknown`
manufacturer, or an x86 ABI are retained as raw build markers but do not set `isEmulator` by
themselves. Device-farm markers also do not set `isEmulator`, because a farm may provide a physical
device. Environment-variable and simulator-environment values are deliberately not returned.

Recognized Android emulator-family markers currently include `genymotion`, `bluestacks`, `nox`,
`memu`, `ldplayer`, `andy`, `droid4x`, and `koplayer`. Firebase Test Lab is detected through its
documented system property. Other cloud providers are not named unless they expose a stable,
application-visible signal; provider attribution must not be inferred from ordinary physical-device
build values.

`device_security_posture` reports coarse security capabilities and settings. A positive biometric
capability does not authenticate the current user. `transaction_safety` is intended for collection
immediately before a protected action and ships disabled because accessibility, capture, call, and
known remote-access-app observations require product-specific calibration and false-positive review.

### Consistency observations

`deriveConsistencySignals(event, expectations)` compares already collected locale, network/SIM
country, timezone, bundle id, and app version values. It performs no additional native collection and
returns only boolean equality observations when both values exist. It intentionally produces no
weight, score, or fraud verdict.

`deriveObservationMetrics(event)` performs local arithmetic over an existing event. It can return
screen aspect ratio, memory/storage pressure, a CPU capacity index, outcome/field counts, and
independent consistency booleans for screen geometry, resource bounds, process uptime, ABI, and
emulator evidence. Invalid or missing operands cause that metric to be omitted rather than converted
to zero. The helper performs no additional native read and creates no identifier.

### Active computation profiles

`runtime_timing` measures bounded JavaScript timer/event-loop distributions, native clock-call
distributions, and JS-to-native call duration. `numeric_consistency` compares a fixed integer vector
and a small floating-point vector across JavaScript and native runtimes, returning only agreement and
difference aggregates. Both probes ship disabled because runtime load, thermal state, operating-system
versions, and hardware class require representative physical-device calibration.

`gpu_benchmark` additionally returns per-operation p50, p95, median absolute deviation, coefficient
of variation, and warm-up slope. It remains disabled and must not be interpreted from one run.

## Data minimization guidance

- Start with an explicit `consentFor(...)` allowlist instead of enabling everything implicitly.
- Disable probe groups that do not serve a documented product or security purpose.
- Use `fields.include` to retain only fields accepted by the application backend contract.
- Never interpret missing data as evidence that a device is safe.
- Do not persist raw events longer than required for the documented purpose.
- Do not combine these observations into a covert, persistent cross-install device identifier.
- Keep `transaction_safety` disabled until its fields have been validated on representative physical
  devices and accepted by the application's privacy and accessibility review.

The host application remains responsible for legal basis, disclosure, consent, access control,
retention, transport security, and responding to user rights requests.
