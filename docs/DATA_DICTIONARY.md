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
| `runtime` | Android, iOS | On | Low | React Native runtime, software version | None |

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

## Data minimization guidance

- Start with an explicit `consentFor(...)` allowlist instead of enabling everything implicitly.
- Disable probe groups that do not serve a documented product or security purpose.
- Use `fields.include` to retain only fields accepted by the application backend contract.
- Never interpret missing data as evidence that a device is safe.
- Do not persist raw events longer than required for the documented purpose.
- Do not combine these observations into a covert, persistent cross-install device identifier.

The host application remains responsible for legal basis, disclosure, consent, access control,
retention, transport security, and responding to user rights requests.
