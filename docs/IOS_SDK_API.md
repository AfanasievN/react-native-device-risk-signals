# Standalone iOS API — experimental 0.2.0-beta.1

Published Swift Package 0.1.0 has nine collectors. The sixteen-method API below is available in
0.2.0-beta.1 from the same distribution repository. Select the exact prerelease version in Xcode.
Physical-iPhone QA is pending: this is an evaluation release, not a production-readiness claim.

```swift
import IOSDeviceRiskSignals

// UIKit-bound calls, normally in a main-actor UI action:
let signals = DeviceRiskSignals()
let hardware = signals.collectHardware()
let posture = signals.collectDeviceSecurityPosture()
// Nothing runs during initialization. Invoke only fields/categories justified by your use case.
```

| Methods | Thread | Purpose / privacy |
| --- | --- | --- |
| collectHardware | main | CPU, memory, screen, battery; briefly enables/restores battery monitoring |
| collectFonts | any, prefer worker | High-entropy font digest; explicitly opt in |
| collectOsIntegrity | main | Process/image/file/scheme and sandbox-write observations; no socket or fork |
| collectGeolocation | main | Sensitive cached fix only with existing authorization; no prompt or updates |
| collectMediaBluetoothApps | main | Audio route, screen capture, accessibility and finite URL-scheme observations |
| collectDeviceSecurityPosture | main | Local authentication capability and protected-data state; no authentication UI |
| collectTransactionSafety | main | Point-in-time capture/mirroring/accessibility; no observer or event history |
| collectDeviceIdentity | any | Device/OS metadata; may synchronously hop to main |
| collectApplication, collectLocale | any | App identity and high-entropy locale/context |
| collectNetwork, collectTelephony | any | Sensitive local addresses/proxy and opportunistic carrier data |
| collectRuntimeTiming, collectNumericConsistency | any, prefer worker | Native-only measurements; not persistent fingerprints |
| collectAudioLatency | any | Session property reads; no activation/recording |
| collectGpuBenchmark | worker only | Expensive explicit workload; skips on Simulator |

Main-thread requirements raise NSInternalInconsistencyException before collection if violated,
including Release builds. Swift do/catch does not catch Objective-C exceptions: obey the contract.
Hosts must not synchronously wait on main for a worker invoking collectDeviceIdentity. Calls are
synchronous; no cancellation, timeout envelope or automatic retry is promised. Keep expensive
measurements out of transaction/UI-critical paths and calibrate on physical devices.

## Privacy and limitations

No new permissions, usage keys, persistent IDs, upload, score or verdict. Location never starts
updates or requests authorization. The native SDK has no default collection schedule; all methods
are opt-in. Missing observations remain omitted. Font digests, image names, app visibility,
accessibility, location and local network data require purpose, minimization and retention review;
accessibility use, VPN use or missing carrier data are not fraud verdicts.

URL scheme reads depend on the host's LSApplicationQueriesSchemes; undeclared/hidden apps are not
distinguishable from absence. See src/knownAppsSchemes.ts for the finite lists. No list is inserted
into the host automatically. The sandbox write check uses the inherited temporary path and cleans
it up after successful writes; do not treat socket-free collection as strictly side-effect-free.

SwiftPM copies PrivacyInfo.xcprivacy into its resource bundle. This SDK transmits nothing and
declares no tracking or Required-Reason API use. The host remains responsible for disclosures and
any collection/transmission it adds. Existing RN privacy packaging remains unchanged.

Core has no active-port or fork API. RN's legacy active implementation remains outside the package
for compatibility; an independently reviewed optional component is future work. JS engine facts,
JS/native comparisons and deriveConsistencySignals are not reproduced as misleading native values.
