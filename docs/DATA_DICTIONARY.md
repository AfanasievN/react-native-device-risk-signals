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
version, executable name, extension state, simulator-build state, and
`embeddedProvisioningProfilePresent`. Certificate values describe
the host application's public signing certificates; no private key or device identifier is read.
Android additionally reports `isInstalledOnExternalStorage` from the host application's own
`ApplicationInfo` flags. It never inspects another application to produce this field.

`getTaskAllowEntitlement` is reserved in the optional contract but is **not populated**. Although
Apple documents `SecTask` entitlement lookup, the current iPhoneOS SDK does not declare those symbols
in its public headers. This package does not hand-declare platform symbols or use a private API to
turn an unavailable observation into `false`.

### Device, hardware, and location context

`device_identity` reports `isIosAppOnMac` on iOS 14 and newer and the compile-time
`isMacCatalystApp` environment flag. Both fields describe the current execution environment and are
not identifiers. Its nested Android `androidBuild.buildTimeMs` is the public `Build.TIME` platform
build timestamp; it is not the app install time and may be absent or inaccurate on vendor builds.

`hardware` reports the system low-power state and current-process resident memory on both platforms.
Android additionally exposes the static low-RAM device class and the current VM heap ceiling. A
failed memory read is omitted; it is never converted to zero.

Android battery context includes the observed health, millivolts, technology string, physical-battery
presence, system low-battery classification, power source, Android 14+ cycle count, and Android 9+
charge-time estimate. Missing intent extras, undocumented enum values, negative cycle counts, and a
failed charge-time computation are omitted. Android also reports `nfcAvailable`; `nfcEnabled` is
included only when an NFC adapter exists and its state can be read. Neither read starts NFC activity
or adds a permission.

### Network and display topology

When the host already declares `ACCESS_NETWORK_STATE`, Android may return all active transport types,
DNS server addresses, Private DNS state/strict hostname, non-default MTU, system Internet validation,
and captive-portal capability. No DNS lookup, socket, or other network request is performed. If the
permission or a platform read is unavailable, the associated fields are omitted. `isConnected: false`
and `connectionType: "none"` are emitted only after a successful read that observed no active network.

`media_bluetooth_apps` exposes Android `displayCount` and `presentationDisplayCount`. On iOS,
`connectedScreenCount` includes the main screen, while `mirroredScreenCount` counts screens whose
public `mirroredScreen` property is non-null. `isScreenMirrored` is derived from that exact raw count;
an attached extended display is no longer mislabeled as mirroring.

`geolocation.locationServicesEnabled` is the system-wide location-services state and is independent
from the host application's authorization. On iOS 15 and newer, a cached location may include
`isSimulatedBySoftware` and `isProducedByAccessory` from `CLLocationSourceInformation`. Those source
fields are omitted when there is no cached location or the OS does not expose source information.

`mockLocationAppsFound` is retained in the optional public type for compatibility but is **not populated**.
Android has no safe complete app-level implementation without broad installed-package enumeration,
which this SDK prohibits. Use the direct `isFromMockProvider` observation when an Android cached
location is available; missing data must not be interpreted as a clean location source.

### OS integrity and transaction context

`os_integrity` includes independent raw observations for tracing, debugger-wait state, test-key
builds, suspicious mounts, Zygisk indicators, executable mappings, suspicious environment-variable
names, matched dangerous Android system properties, loadable hook class names, and evidence counts.
The dangerous-property field returns only observed `key=value` matches; when system properties are
unreadable, both the raw list and its optional aggregate are omitted. Hook classes are looked up
without class initialization. Emulator observations include matched build markers, discovered emulator file paths,
QEMU or virtual-hardware property markers, CPU markers, recognized emulator-family markers, sensor
availability, Android Test Harness Mode, Firebase Test Lab presence, and iOS simulator or XCTest
environment presence. Broad observations such as `test-keys`, an `unknown`
manufacturer, or an x86 ABI are retained as raw build markers but do not set `isEmulator` by
themselves. Device-farm markers also do not set `isEmulator`, because a farm may provide a physical
device. Environment-variable and simulator-environment values are deliberately not returned.

`usbDebuggingEnabled` is retained as an optional compatibility field but is **not populated**.
Modern Android does not expose a trustworthy third-party read of the ADB-enabled setting; returning
the ordinary-app fallback `0` as `false` would incorrectly claim that USB debugging was observed off.

Recognized Android emulator-family markers currently include `genymotion`, `bluestacks`, `nox`,
`memu`, `ldplayer`, `andy`, `droid4x`, and `koplayer`. Firebase Test Lab is detected through its
documented system property. Other cloud providers are not named unless they expose a stable,
application-visible signal; provider attribution must not be inferred from ordinary physical-device
build values.

The filesystem and dyld catalogs include selected modern KernelSU, APatch, resetprop, rootless
jailbreak, Dopamine, palera1n, TrollStore, ElleKit, and Frida artifacts. A hit is returned as a path,
property, class, or image name; it is never converted into an on-device root/jailbreak verdict.

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
