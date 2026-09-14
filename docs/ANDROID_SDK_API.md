# Standalone Android SDK API

This document describes the two standalone Android components for an engineer integrating them
without React Native:

| Component | Gradle module | Maven coordinates | Entry point |
| --- | --- | --- | --- |
| Passive core | `sdks/android/` | `io.github.afanasievn:android-device-risk-signals` | `io.github.afanasievn.devicerisksignals.DeviceRiskSignals` |
| Active probes (optional) | `sdks/android-active-probes/` | `io.github.afanasievn:android-active-probes-device-risk-signals` | `io.github.afanasievn.devicerisksignals.active.DeviceRiskActiveProbes` |

Neither artifact is published to a public registry today. Both are versioned `0.1.0-SNAPSHOT`
(`sdks/android/build.gradle.kts:9`, `sdks/android-active-probes/build.gradle.kts:9`) and are
published only to a file repository inside each component's build output; the registry half of
publication — signing, javadoc, Sonatype namespace verification, a real version — is an open gate in
[`MIGRATION_ROADMAP.md`](MIGRATION_ROADMAP.md). Treat the API below as implemented and tested but not
yet released, and do not assume source or binary stability across snapshots.

The two components are independent. The passive core never opens a socket; the active component is
the single documented exception and performs loopback socket I/O only
(`sdks/android-active-probes/src/main/kotlin/io/github/afanasievn/devicerisksignals/active/DeviceRiskActiveProbes.kt:5-22`).
A host can adopt the core without taking on any socket I/O.

Field-level meaning for every raw key is in [`DATA_DICTIONARY.md`](DATA_DICTIONARY.md). This document
covers the calling contract: signatures, threading, lifecycle, timeouts, version gates, and the
limitations a consumer must design around.

## 1. The surface

### 1.1 `DeviceRiskSignals`

```kotlin
class DeviceRiskSignals(context: Context)
```

The constructor stores `context.applicationContext`
(`sdks/android/src/main/kotlin/io/github/afanasievn/devicerisksignals/DeviceRiskSignals.kt:10`), so
passing an `Activity` does not leak it. Two collectors are instantiated once and reused
(`DeviceIdentityCollector`, `LocaleCollector`, lines 11-12); every other collector is constructed per
call. The facade holds no mutable collection state and no cache — repeated calls re-read the
platform. It is not `Closeable` and has no teardown.

Every `collect*` method is synchronous, returns a Kotlin data class, and never returns `null`. Each
data class exposes `toRawMap(): Map<String, Any>` producing the wire keys listed below. Unavailable
values are omitted from that map rather than defaulted — see §7.1 for where that rule is not held.

| Method | Returns | Raw keys it can emit |
| --- | --- | --- |
| `collectDeviceIdentity()` | `DeviceIdentitySignals` | `manufacturer`, `model`, `brand`, `systemName` (always `"android"`), `systemVersion`, `isTablet`, `osBuild`, `kernelVersion`, `kernelOsRelease`, `kernelOsType`, `androidBuild` (nested map: `board`, `bootloader`, `device`, `display`, `fingerprint`, `hardware`, `host`, `id`, `product`, `tags`, `buildType`, `supportedAbis`, `sdkInt`, `codename`, `incremental`, `securityPatch`, `baseOs`, `socManufacturer`, `socModel`, `buildTimeMs`) |
| `collectLocale()` | `LocaleSignals` | `language`, `country`, `languages`, `timezoneId`, `timezoneOffsetMinutes`, `uses24HourClock`, `currencyCode`, `decimalSeparator`, `groupingSeparator`, `firstDayOfWeek`, `keyboardLanguages` |
| `collectApplication()` | `ApplicationSignals` | `appVersion`, `appBuild`, `bundleId`, `firstInstallTimeMs`, `lastUpdateTimeMs`, `appName`, `processUptimeMs`, `targetSdkVersion`, `minSdkVersion`, `isDebuggable`, `isSystemApp`, `isUpdatedSystemApp`, `isInstantApp`, `hasMultipleSigners`, `signingCertificateSha256`, `signingCertificateHistorySha256`, `grantedPermissions`, `isSplitApks`, `splitNames`, `installerPackage`, `installingPackageName`, `initiatingPackageName`, `initiatingPackageSigningCertificateSha256`, `installPackageSource`, `updateOwnerPackageName`, `isInstalledOnExternalStorage`, `isForeground` |
| `collectHardware()` | `HardwareSignals` | `uptimeMs`, `screenWidthPx`, `screenHeightPx`, `screenDensity`, `screenDpi`, `screenPhysicalWidthPx`, `screenPhysicalHeightPx`, `screenPhysicalDensity`, `screenBrightness`, `processorCount`, `cpuArchitecture`, `cpuMaxFrequencyMhz`, `screenOrientation`, `storageTotalBytes`, `storageFreeBytes`, `totalMemoryBytes`, `freeMemoryBytes`, `isLowMemory`, `isLowRamDevice`, `processResidentMemoryBytes`, `runtimeMaxMemoryBytes`, `lowPowerModeEnabled`, `batteryLevel`, `batteryState`, `batteryTemperatureC`, `batteryHealth`, `batteryVoltageMv`, `batteryTechnology`, `batteryPresent`, `batteryLow`, `powerSource`, `batteryCycleCount`, `chargeTimeRemainingMs`, `nfcAvailable`, `nfcEnabled` |
| `collectFonts()` | `FontsSignals` | `fontsDigest` |
| `collectOsIntegrity()` | `OsIntegritySignals` | 58 keys covering root/hook/emulator evidence — `isEmulator`, `isDebuggerAttached`, `isDebuggerWaiting`, `developerModeEnabled`, `suBinaryFound`, `suOnPath`, `rootManagementAppFound`, `dangerousAppFound`, `rootCloakingAppFound`, `suspiciousFilePathsFound`, `suspiciousFilePaths`, `suspiciousPathCount`, `writableSystemPathFound`, `dangerousPropsPresent`, `dangerousSystemProperties`, `injectedLibrariesFound`, `injectedLibraryNames`, `injectedLibraryCount`, `loadedHookClassNames`, `hookStackFrameFound`, `hookStackFrames`, `hookFrameworkFound`, `magiskMountsFound`, `suspiciousMountsFound`, `zygiskIndicatorsFound`, `magiskAbstractSocketFound`, `magicMountModulesFound`, `fridaThreadNamesFound`, `fridaInjectorPipeFound`, `fridaListenerPortFound`, `suspiciousExecutableMappingsFound`, `tracerPid`, `tracedByOtherProcess`, `testKeysBuild`, `suspiciousEnvironmentVariablesFound`, `suspiciousEnvironmentVariableNames`, `verifiedBootState`, `bootloaderLocked`, `selinuxEnforcing`, `ldPreloadSet`, `ldPreloadValue`, `hiddenApiPolicy`, `emulatorFingerprintMatch`, `emulatorFilesFound`, `emulatorBuildMarkers`, `emulatorFilePaths`, `emulatorSystemPropertyMarkers`, `emulatorCpuMarkers`, `emulatorVendorMarkers`, `sensorCount`, `hasAccelerometer`, `hasGyroscope`, `hasMagnetometer`, `hasProximitySensor`, `isRunningInUserTestHarness`, `deviceFarmMarkers`, `emulatorChecksPerformed`, `abi` |
| `collectNetwork()` | `NetworkSignals` | `isConnected`, `connectionType`, `isMetered`, `isVpnActive`, `isInternetValidated`, `hasCaptivePortal`, `networkTransportTypes`, `linkDownstreamKbps`, `linkUpstreamKbps`, `interfaceNames`, `localIpAddresses`, `isProxyConfigured`, `proxyHost`, `proxyPort`, `dnsServerAddresses`, `isPrivateDnsActive`, `privateDnsServerName`, `activeNetworkMtu`, `mobileRxBytes`, `mobileTxBytes`, `wifiRxBytes`, `wifiTxBytes` |
| `collectTelephony()` | `TelephonySignals` | `phoneType`, `networkOperatorName`, `simOperatorName`, `networkCountryIso`, `simCountryIso`, `simState`, `dataState`, `hasIccCard`, `isNetworkRoaming`, `simCount` |
| `collectGeolocation()` | `GeolocationSignals` | `hasCoarsePermission`, `authorizationStatus`, `locationServicesEnabled`, `gnssSupported`, `latitude`, `longitude`, `accuracyMeters`, `altitudeMeters`, `provider`, `isFromMockProvider`, `locationAgeMs` |
| `collectMediaBluetoothApps()` | `MediaBluetoothAppsSignals` | `isMusicActive`, `audioOutputRoute`, `bluetoothBondedDeviceCount`, `displayCount`, `presentationDisplayCount`, `installedFlaggedApps`, `enabledAccessibilityServices` |
| `collectDeviceSecurityPosture()` | `DeviceSecurityPostureSignals` | `hasSecureLockScreen`, `isDeviceLocked`, `isUserUnlocked`, `fingerprintHardwarePresent`, `faceHardwarePresent`, `biometryAvailable`, `biometryType`, `strongBoxAvailable`, `automaticTimeEnabled`, `automaticTimeZoneEnabled`, `deviceProvisioned`, `securityPatch` |
| `collectTransactionSafety()` | `TransactionSafetySignals` | `isDeviceLocked`, `isInteractive`, `enabledAccessibilityServiceCount`, `accessibilityRunning`, `remoteAccessAppsFound`, `remoteAccessAppCount`, `audioMode`, `isCallActive` |
| `createTransactionObservationSession()` | `TransactionObservationSession` | see §1.2 |
| `collectRuntimeTiming()` | `RuntimeTimingSignals` | `nativeClockSource` (always `"elapsed_realtime_nanos"`), `nativeSampleCount`, `nativeTimerResolutionNs`, `nativeIntervalMedianNs`, `nativeIntervalP95Ns`, `nativeIntervalMadNs` |
| `collectNumericConsistency()` | `NumericConsistencySignals` | `integerVectorResult`, `floatVector`, `signedZeroPreserved`, `subnormalPreserved` — all four always present (`NumericConsistencySignals.kt:10-15` uses `mapOf`, not the omit-on-null builder) |
| `collectAudioLatency()` | `AudioLatencySignals` | `framesPerBuffer`, `nativeSampleRate`, `outputLatencyMs`, `measured` (always present) |
| `collectGpuBenchmark()` | `GpuBenchmarkSignals` | `benchmarkPerformed`, `skippedReason`, `rendererName`, `vendorName`, `apiVersion`, `shadingLanguageVersion`, `maxTextureSize`, `drawCallsCompleted`, `durationMs`, `operationTimeP50Ms`, `operationTimeP95Ms`, `operationTimeMadMs`, `operationTimeCoefficientOfVariation`, `warmupSlope` |

`collectFonts()` is deliberately separate from `collectHardware()` even though both are served by
`HardwareCollector`: font-directory enumeration and hashing is the more expensive read and the host
schedules it independently (`HardwareCollector.kt:49-51`).

### 1.2 `TransactionObservationSession`

```kotlin
class TransactionObservationSession(context: Context) : AutoCloseable {
  fun attach(activity: Activity)
  fun snapshot(): TransactionObservationSnapshot?
  fun detach()
  override fun close()
}
```

`createTransactionObservationSession()` returns a **new, inactive** session on every call
(`DeviceRiskSignals.kt:49-50`); the facade caches nothing, so the host owns the instance and must not
create one per collection.

`snapshot()` returns `null` until the first `attach()` (`TransactionObservationSession.kt:64`). Its
`toRawMap()` emits:

`transactionObservationStartedElapsedMs`, `observedTouchCount`, `obscuredTouchObserved`,
`partiallyObscuredTouchObserved`, `lastObscuredTouchElapsedMs`,
`lastPartiallyObscuredTouchElapsedMs`, `screenshotObservationActive`,
`screenshotDetectedSinceObservationStart`, `lastScreenshotDetectedElapsedMs`,
`isVisibleInScreenRecording`, `isScreenCaptured`.

Three details of that map are easy to get wrong:

- `transactionObservationStartedElapsedMs` and every `last*ElapsedMs` are monotonic
  (`SystemClock.elapsedRealtime`), not wall-clock (`TransactionObservationSnapshot.kt:4-5`).
- `screenshotObservationActive` is emitted **only when true** (`TransactionObservationSnapshot.kt:29`).
  Its absence means "not observing", not "observing and saw nothing".
- `isVisibleInScreenRecording` and `isScreenCaptured` are two keys written from one value
  (`TransactionObservationSnapshot.kt:32-35`). `isScreenCaptured` is a compatibility alias, not an
  independent observation.

### 1.3 `DeviceRiskActiveProbes`

```kotlin
class DeviceRiskActiveProbes {
  fun collectFridaScan(): FridaScanSignals
}
```

One TCP connect to `127.0.0.1:27042` plus an unauthenticated `AUTH` handshake, with 700 ms used as
both connect and read timeout (`FridaScanCollector.kt:26-28`, `:79-84`). Raw keys: `scanPerformed`,
`scannedPort`, `defaultPortOpen`, `fridaHandshakeReject`. Host, port and timeout are constructor
seams on the internal collector for tests only; the public class always uses production defaults
(`FridaScanCollector.kt:21-24`).

### 1.4 Permissions the host must already hold

Neither component declares a permission. `sdks/android/src/main/AndroidManifest.xml:1` is a bare
`<manifest/>`; the active component's manifest declares nothing and says so explicitly
(`sdks/android-active-probes/src/main/AndroidManifest.xml:1-3`). No call ever requests a permission
or shows a prompt.

| Permission | Used by | API gate | Result without the host grant |
| --- | --- | --- | --- |
| `ACCESS_NETWORK_STATE` | `collectNetwork()` (`NetworkCollector.kt:31-34`) | none | All `ConnectivityManager`-derived fields omitted: `isConnected`, `connectionType`, `isMetered`, `isInternetValidated`, `hasCaptivePortal`, `networkTransportTypes`, link bandwidths, DNS/MTU. `interfaceNames`, `localIpAddresses`, proxy and traffic counters still collected; `isVpnActive` falls back to an interface-name heuristic (`NetworkCollector.kt:66-72`) |
| `READ_PHONE_STATE` | `collectTelephony()` (`TelephonyCollector.kt:38`) | none | `simCount` omitted; every other telephony field unaffected |
| `ACCESS_COARSE_LOCATION` or `ACCESS_FINE_LOCATION` | `collectGeolocation()` (`GeolocationCollector.kt:19-20`) | none | `authorizationStatus` reads `"denied"`, `hasCoarsePermission` false, and no fix is read, so `latitude`, `longitude`, `accuracyMeters`, `altitudeMeters`, `provider`, `isFromMockProvider`, `locationAgeMs` are all omitted. `locationServicesEnabled` and `gnssSupported` still collected |
| `BLUETOOTH_CONNECT` | `collectMediaBluetoothApps()` (`MediaBluetoothAppsCollector.kt:72-75`) | checked only on API 31+ | `bluetoothBondedDeviceCount` omitted. Only a count is ever read — never a peripheral name or address (`MediaBluetoothAppsCollector.kt:79-82`) |
| `DETECT_SCREEN_CAPTURE` | `TransactionObservationSession.attach()` (`TransactionObservationSession.kt:50`) | API 34+ | No screenshot callback registered; `screenshotObservationActive` omitted and `screenshotDetectedSinceObservationStart` stays `null`/omitted (`TransactionObservationState.kt:60`) |
| `DETECT_SCREEN_RECORDING` | `TransactionObservationSession.attach()` (`TransactionObservationSession.kt:56`) | API 35+ | `isVisibleInScreenRecording` and `isScreenCaptured` omitted |
| `INTERNET` | `DeviceRiskActiveProbes.collectFridaScan()` | none | Every flag reads `false`. This is device-verified: an app outside the `inet` group cannot open a socket at all, loopback included, so the result is indistinguishable from "nothing is listening" (`DeviceRiskActiveProbes.kt:13-17`). `INTERNET` is a normal permission and raises no prompt |

### 1.5 Which probes ship disabled

The standalone facade has no configuration object and no enable/disable switch: every method is a
direct call the host chooses to make. "Disabled by default" is therefore a **policy the host must
implement itself**, expressed in the React Native probe catalog
(`contract/source/probe-catalog.source.json`) and in the repository's product rules — new sensitive
or expensive probes ship disabled until representative physical-device QA and a documented benchmark
justify enabling them.

The collections that are off by default in that catalog, and which a native host should likewise not
call without a deliberate decision:

| Collection | Catalog probe | Reason recorded |
| --- | --- | --- |
| `collectGpuBenchmark()` | `gpu_benchmark` | Disabled pending calibration; high-entropy, expensive, worker-only |
| `collectAudioLatency()` | `audio_latency` | Disabled pending calibration |
| `collectTransactionSafety()` + `createTransactionObservationSession()` | `transaction_safety` | Accessibility and remote-control observations are not yet calibrated on representative physical devices |
| `collectRuntimeTiming()` | `runtime_timing` | Bounded active workload |
| `collectNumericConsistency()` | `numeric_consistency` | Bounded active workload |

`os_integrity_frida_scan` — the active component's only probe — is **on** by default in the React
Native catalog, but its own component defaults are still an open decision in the migration roadmap.
A native host adopting the active component should make that call explicitly.

## 2. Threading

The SDK never dispatches work on the caller's behalf. There is no internal executor, handler, or
coroutine scope in either component. Every method runs entirely on the thread that calls it, and the
host owns thread selection, back-pressure and cancellation.

| Call | Thread requirement | Enforcement |
| --- | --- | --- |
| `collectGpuBenchmark()` | Worker thread only | `GpuExecutionPolicy.requireWorker(isMainThread)` throws `IllegalStateException("GPU collection requires a worker thread")` (`GpuExecutionPolicy.kt:4-6`), called first thing in `GpuBenchmarkCollector.collect()` (`GpuBenchmarkCollector.kt:35`) so no internal caller can start GL work on the UI thread |
| `DeviceRiskActiveProbes.collectFridaScan()` | Worker thread only | Not asserted by the SDK; Android itself throws `NetworkOnMainThreadException` on a main-thread socket call (`DeviceRiskActiveProbes.kt:18-19`) |
| `TransactionObservationSession.attach()` / `detach()` / `close()` | Main thread only | Private `requireMainThread()` throws `IllegalStateException("Transaction observation lifecycle must run on the main thread")` (`TransactionObservationSession.kt:99-103`), called at the top of all three (`:34`, `:68`, `:90`) |
| `TransactionObservationSession.snapshot()` | Any thread | No assertion. State is guarded by `@Synchronized` on every mutator and on `snapshot()` itself (`TransactionObservationState.kt:14`, `:29`, `:34`, `:39`, `:46`, `:51`) |
| All other `collect*` | Any thread | No assertion. They are synchronous platform reads with no shared mutable state |

"Any thread" is not "free". Several collections perform blocking filesystem and IPC work and should
not run on the UI thread in a latency-sensitive app:

- `collectOsIntegrity()` reads `/proc/cpuinfo`, `/proc/tty/drivers`, `/proc/self/maps`,
  `/proc/self/mountinfo`, `/proc/net/unix`, `/proc/net/tcp`, `/proc/net/tcp6`, system properties, and
  probes dozens of filesystem paths and package names in one synchronous pass
  (`OsIntegrityCollector.kt:37-175`).
- `collectFonts()` lists four font directories and hashes the sorted filenames
  (`HardwareCollector.kt:198-212`).
- `collectMediaBluetoothApps()` and `collectTransactionSafety()` each call
  `PackageManager.getPackageInfo` once per entry in a finite package list
  (`MediaBluetoothAppsCollector.kt:38`, `TransactionSafetyCollector.kt:18`).
- `collectRuntimeTiming()` reads the monotonic clock 257 times in a tight loop
  (`RuntimeTimingCollector.kt:10-16`).
- `collectNumericConsistency()` runs a 1024-iteration integer mix plus five transcendental
  evaluations (`NumericConsistencyCollector.kt:11-21`).
- `collectHardware()` calls `registerReceiver(null, ACTION_BATTERY_CHANGED)` — a sticky-broadcast read
  that is a binder round trip (`HardwareCollector.kt:131`).

### Worked example: the React Native binding's executor

The React Native binding is a host like any other, and it owns its worker pool explicitly. Every
Spec method is wrapped in `resolveOrReject`, which submits the SDK call to a cached daemon thread
pool (`android/src/main/java/com/reactnativedeviceintel/DeviceIntelModule.kt:149-162`):

```kotlin
private val probeExecutor =
  Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "device-intel-probe").apply { isDaemon = true }
  }
```

The comment at `DeviceIntelModule.kt:142-148` records why: React Native dispatches native-module
methods serially on a single thread, so without a pool every probe queued behind a slow one and blew
its short JS timeout while merely waiting its turn. A native host that fans probes out concurrently
needs the same arrangement. Collectors are stateless per call, so concurrent invocation of different
`collect*` methods on one `DeviceRiskSignals` instance is safe.

The one exception is the transaction session, whose lifecycle must be marshalled to the main thread.
The binding does that explicitly rather than relying on the SDK
(`android/src/main/java/com/reactnativedeviceintel/TransactionSafetyObserver.kt:28-30`, `:53-56`).

## 3. Lifecycle and cleanup

Only `TransactionObservationSession` has a lifecycle. Everything else is a call that returns.

### 3.1 The contract

**`attach(activity)`** — main thread. Throws `IllegalStateException` if the session is closed
(`TransactionObservationSession.kt:35`). Re-attaching to the same activity whose window still carries
our wrapper is a no-op (`:36-37`). Otherwise it calls `detach()` first, then:

1. Creates the observation state on first attach only, stamping `startedElapsedMs` (`:39-41`). A
   later re-attach reuses the same state, so cumulative history survives.
2. Bumps the lifecycle generation and takes a token (`:42`, `TransactionSessionLifecycle.kt:10-15`).
3. Wraps `activity.window.callback` with a delegating `Window.Callback` that records `ACTION_DOWN`
   touches and their obscured flags (`:44-49`, `:105-123`). **If `activity.window.callback` is null,
   no touch observation is installed** and `observedTouchCount` stays 0 while the rest of the session
   still functions.
4. Registers the screenshot callback on API 34+ with `DETECT_SCREEN_CAPTURE` (`:50-55`) and the
   screen-recording callback on API 35+ with `DETECT_SCREEN_RECORDING` (`:56-60`). Both registrations
   are wrapped in `safe {}`, so a platform failure leaves the field unobserved rather than throwing.

**`snapshot()`** — any thread, never activates observation (`:63-64`).

**`detach()`** — main thread. Marks the lifecycle inactive, closes both registrations, clears
`screenshotObservationActive` and `isVisibleInScreenRecording`, and restores the original window
callback (`:67-86`).

**`close()`** — main thread, terminal and idempotent. Sets `isClosed`, then calls `detach()`
(`:89-93`). Subsequent `attach()` throws; `snapshot()` remains readable because the state object is
never nulled.

### 3.2 What survives what

| Observation | Survives `detach()` | Survives `close()` | Survives a new session |
| --- | --- | --- | --- |
| `transactionObservationStartedElapsedMs` | yes | yes | no — restamped |
| `observedTouchCount`, `obscuredTouchObserved`, `lastObscuredTouchElapsedMs` and the partial-obscure pair | yes | yes | no |
| `screenshotDetectedSinceObservationStart` = true, `lastScreenshotDetectedElapsedMs` | yes | yes | no |
| `screenshotObservationActive` | no — reset to false (`:74`) | no | n/a |
| `isVisibleInScreenRecording` / `isScreenCaptured` | no — reset to null (`:75`) | no | n/a |

Put differently: **historical positives persist; current coverage does not.** Once detached, a
`false` screenshot observation is no longer meaningful and the SDK stops emitting it
(`TransactionObservationState.kt:60` only reports the negative while observation is active). A new
session is the only way to reset cumulative history (`TransactionObservationSession.kt:20`).

The lifecycle token exists because restoration can fail. If another library wraps the window callback
after we do, `detach()` refuses to restore (`:79`) rather than clobbering the other wrapper, and our
delegating callback may stay installed and keep receiving events. The generation token makes those
events inert: `ObservingCallback` records nothing unless `lifecycle.isActive(token)`
(`:112`, `TransactionSessionLifecycle.kt:17`).

### 3.3 Host cleanup obligations

- Call `detach()` on host pause and `close()` on destroy. The React Native binding does exactly this
  (`TransactionSafetyObserver.kt:35-43`, `:45-51`), and the native example does it in `onStop` and
  `onDestroy` (`sdks/android/example/src/main/kotlin/.../example/MainActivity.kt:92-102`).
- Both must run on the main thread. Marshal them yourself; the SDK will throw, not reschedule.
- Do not let a caller's timeout skip cleanup. The binding routes `close()` through a main-thread post
  that is deliberately not cancellable, with the comment "Cleanup must not be canceled if a caller
  stops waiting or its worker is interrupted" (`TransactionSafetyObserver.kt:49-50`).
- The session holds the activity, the original callback and our wrapper in `WeakReference`s
  (`TransactionObservationSession.kt:26-28`), so forgetting `close()` does not leak the activity — but
  it does leave platform callbacks registered until the process ends.
- `DeviceRiskSignals` itself needs no teardown.
- `DeviceRiskActiveProbes` holds no state; the socket is closed on every path including the throwing
  ones, via `Socket().use { }` (`FridaScanCollector.kt:49-50`).

## 4. Timeouts and cancellation ownership

**The host owns every timeout and every cancellation.** No `collect*` method takes a timeout
parameter, exposes a cancellation handle, or checks an interruption flag mid-collection. There are
exactly two time bounds inside the SDK:

- **GPU draw budget — 50 ms.** `BUDGET_MS = 50L` (`GpuBenchmarkCollector.kt:207`) bounds the
  draw-call loop, which checks the elapsed budget *before* each iteration and then issues
  `glClear`/`glDrawArrays`/`glFinish` (`:169-176`). This is a **budget, not a deadline**: total wall
  time is 50 ms plus one full blocking `glFinish`, plus EGL initialization, config selection, context
  creation, shader compilation and teardown, none of which are timed. The emitted `durationMs`
  measures the loop only (`:177`). On a slow or contended GPU the call can take substantially longer
  than 50 ms, and nothing inside the SDK will stop it.
- **Active probe socket timeout — 700 ms.** `CONNECT_TIMEOUT_MS = 700` is used as both the connect
  timeout and `soTimeout` (`FridaScanCollector.kt:51`, `:54`, `:82`), so worst case is roughly 1.4 s
  of blocking per call.

Everything else is unbounded in principle. `collectOsIntegrity()` in particular performs dozens of
filesystem reads whose latency depends on the ROM and SELinux policy.

A caller-side timeout does **not** cancel native work. This is the central rule, and the binding's
helper is named for it: `PendingObservationTask` "Cancels queued work, not an Android callback
already executing on the UI thread" (`PendingObservationTask.kt:7`). Concretely
(`PendingObservationTask.kt:13-37`):

- `await(timeoutMs)` waits on a latch, then calls `cancel()` on expiry.
- `cancel()` wins only if it claims the `AtomicBoolean` **before** `run()` does. If the task has not
  started, it never will, and the waiter gets `null`.
- If `run()` already claimed it, `cancel()` does nothing. The block runs to completion on the UI
  thread and its result is simply discarded.

The transaction observer applies a 1000 ms bound around exactly that
(`TransactionSafetyObserver.kt:30`). The same asymmetry applies one level up: `invalidate()` calls
`probeExecutor.shutdownNow()` (`DeviceIntelModule.kt:138`), which interrupts worker threads but
cannot abort a platform call already inside the kernel or a GPU driver. The native example spells it
out at the call site: "Does not forcibly cancel a GPU driver call already running"
(`sdks/android/example/src/main/kotlin/.../example/MainActivity.kt:98`) and, for the active probe,
"Stops queued work only; a socket read already in progress ends on its own timeout"
(`sdks/android-active-probes/example/src/main/kotlin/.../example/MainActivity.kt:59`).

Design accordingly: a host timeout bounds how long you *wait*, not how long the device *works*.
`Thread.interrupt()` is honoured only where the JDK honours it; `GpuBenchmarkCollector` re-asserts the
interrupt flag before returning a skipped result if an `InterruptedException` escapes
(`GpuBenchmarkCollector.kt:106-108`).

## 5. Supported versions and per-probe capability gates

Both components declare `minSdk = 24` (Android 7.0) and `compileSdk = 36`
(`sdks/android/build.gradle.kts:20`, `:23`; `sdks/android-active-probes/build.gradle.kts:20`, `:23`), Java 8
source/target compatibility, and Kotlin 2.0.21 with AGP 8.7.2.

Every API-level gate actually applied in the code:

| API | Constant | Affected output |
| --- | --- | --- |
| 26 | `O` | `application.isInstantApp` (`ApplicationCollector.kt:73`); `application.isSplitApks` / `splitNames` (`:122`); `network.networkTransportTypes` can contain `"wifiAware"` (`NetworkCollector.kt:143`) |
| 27 | `O_MR1` | `network.networkTransportTypes` can contain `"lowpan"` (`NetworkCollector.kt:145`) |
| 28 | `P` | Signing reads switch from `GET_SIGNATURES` to `GET_SIGNING_CERTIFICATES`, enabling `hasMultipleSigners` from `signingInfo` and a real `signingCertificateHistorySha256` (`ApplicationCollector.kt:27`, `:81-85`); `appBuild` from `longVersionCode` instead of `versionCode` (`:32-37`); `hardware.batteryLow` (`HardwareCollector.kt:165`); `hardware.chargeTimeRemainingMs` (`:176`); `network.isPrivateDnsActive` / `privateDnsServerName` (`NetworkCollector.kt:123`); `deviceSecurityPosture.strongBoxAvailable` (`DeviceSecurityPostureCollector.kt:29`); `geolocation.locationServicesEnabled` uses `isLocationEnabled` instead of per-provider checks (`GeolocationCollector.kt:83`) |
| 29 | `Q` | `network.activeNetworkMtu` (`NetworkCollector.kt:130`); `osIntegrity.isRunningInUserTestHarness` and the `test_harness` entry in `emulatorChecksPerformed` (`OsIntegrityCollector.kt:163`); `partiallyObscuredTouchObserved` — below API 29 the flag is passed as `null` and the field is never observed (`TransactionObservationSession.kt:115-117`) |
| 30 | `R` | Install provenance switches to `getInstallSourceInfo`, enabling `installingPackageName`, `initiatingPackageName`, `initiatingPackageSigningCertificateSha256`; below 30 only the deprecated `getInstallerPackageName` is read (`ApplicationCollector.kt:131`, `:154-161`) |
| 31 | `S` | `deviceIdentity.androidBuild.socManufacturer` / `socModel` (`DeviceIdentityCollector.kt:55`, `:58`); `geolocation.isFromMockProvider` uses `Location.isMock` instead of the deprecated `isFromMockProvider` (`GeolocationCollector.kt:72`); `network.networkTransportTypes` can contain `"usb"` (`NetworkCollector.kt:147`); `BLUETOOTH_CONNECT` is required for the bonded count (`MediaBluetoothAppsCollector.kt:73`) |
| 33 | `TIRAMISU` | `application.installPackageSource` (`ApplicationCollector.kt:143`) |
| 34 | `UPSIDE_DOWN_CAKE` | `application.updateOwnerPackageName` (`ApplicationCollector.kt:147`); `hardware.batteryCycleCount` (`HardwareCollector.kt:172`); screenshot observation, gated additionally on `DETECT_SCREEN_CAPTURE` (`TransactionObservationSession.kt:50`) |
| 35 | `VANILLA_ICE_CREAM` | `network.networkTransportTypes` can contain `"satellite"` (`NetworkCollector.kt:149`); screen-recording visibility, gated additionally on `DETECT_SCREEN_RECORDING` (`TransactionObservationSession.kt:56`) |

`deviceSecurityPosture.securityPatch` is deliberately ungated: the field exists from API 23 and the
floor is 24, so no guard is needed and an empty string is omitted rather than reported
(`DeviceSecurityPostureCollector.kt:35-37`).

The API-34/35 platform callback types are isolated in `@TargetApi`-annotated private classes so they
never appear in a signature the API-24 verifier must resolve
(`TransactionObservationSession.kt:132-135`, `:156-158`).

## 6. What the SDK will never do

These are product boundaries, not current limitations:

- **No score, no verdict.** Nothing returns a risk score, a trusted/untrusted decision, or a
  blocking recommendation. Collectors emit raw observations only; `DeviceRiskSignals.kt:5-8` states
  this on the facade, and the classifiers it uses (`EmulatorEvidenceClassifier`,
  `IntegrityEvidenceClassifier`, `BatterySignalClassifier`) map platform values to raw markers without
  weighting or aggregating.
- **No network, with one scoped exception.** The passive core never opens a socket
  (`DeviceRiskSignals.kt:26`, `:29`). The active component connects to `127.0.0.1` and a single
  documented port — no off-device request, no vendor endpoint, no hostname resolution beyond
  `127.0.0.1`, no port scanning past that one port (`DeviceRiskActiveProbes.kt:11-14`). Neither
  component uploads anything; transport is entirely the host's.
- **No persistent identifier.** No IMEI, no advertising ID, no generated device ID persisted
  anywhere. `TelephonyCollector.kt:21` records that persistent identifiers are out of scope, and
  `HardwareCollector.kt:28` repeats it. Nothing in either component writes to storage.
- **No permission prompts and no permission declarations.** Both manifests are empty (§1.4). Every
  protected read is preceded by a `checkSelfPermission` on a grant the host already owns.
- **No `QUERY_ALL_PACKAGES`.** Package lookups use a finite, curated, source-controlled list
  (`KnownAppLists.kt:8-14`).
- **No Bluetooth peripheral identity.** Only a bonded-device count
  (`MediaBluetoothAppsCollector.kt:79-82`).
- **No Wi-Fi SSID or BSSID** (`NetworkCollector.kt:17`).
- **No fresh location request.** Only cached last-known fixes, and only with a grant the host already
  holds (`GeolocationCollector.kt:11-14`).

## 7. Known limitations a consumer must design around

### 7.1 Omission is not `false` — but the rule is not applied uniformly

The intended contract is that an unavailable observation is omitted, so that "we could not look" is
distinguishable from "we looked and found nothing". `OsIntegrityCollector.kt:113-117` states it
directly for the SELinux-restricted procfs reads, and `magiskAbstractSocketFound()`,
`magicMountModuleCount()` and `fridaListenerFound()` all return `null` on an unreadable file
(`OsIntegrityCollector.kt:257-283`). `NetworkObservationPolicy` exists purely to keep "could not
observe" separate from "observed, disconnected" (`NetworkObservationPolicy.kt:8-22`).

But several collectors use a `safeBool` helper that collapses a thrown exception to `false`:

- `GeolocationCollector.safeBool` (`GeolocationCollector.kt:96-100`) — so `hasCoarsePermission`,
  `gnssSupported` and `isFromMockProvider` report `false` on a platform failure, not omission.
- `MediaBluetoothAppsCollector.safeBool` (`MediaBluetoothAppsCollector.kt:103-107`) — `isMusicActive`
  reports `false` when `AudioManager` throws.
- `OsIntegrityCollector` uses `safeBool` for `isDebuggerAttached`, `isDebuggerWaiting`,
  `developerModeEnabled` and most file-existence checks.

And several boolean fields are derived from a list that is empty either because nothing was found or
because nothing could be read: `suspiciousFilePathsFound`, `injectedLibrariesFound`,
`hookStackFrameFound`, `writableSystemPathFound`, `suspiciousEnvironmentVariablesFound`. The
collector's own doc comment is explicit: callers must not interpret an empty or false observation as
proof that an inaccessible artifact is absent (`OsIntegrityCollector.kt:30-33`).

**Treat every `false` in this SDK as "not observed", never as "confirmed absent".**

### 7.2 Finite package lists and the visibility caveat

`KnownAppLists` is the single source of truth for the package names the SDK looks for: 13
root-manager, 10 hook-framework, 9 remote-access, 6 dangerous-app and 6 root-cloaking packages
(`KnownAppLists.kt:18-82`). It feeds `osIntegrity.rootManagementAppFound` / `dangerousAppFound` /
`rootCloakingAppFound` / `hookFrameworkFound`, `mediaBluetoothApps.installedFlaggedApps`, and
`transactionSafety.remoteAccessAppsFound`.

Two consequences:

1. **The list is finite and curated.** Any package not on it is invisible to these fields by design.
   Absence of a flag says nothing about packages outside the list.
2. **The standalone SDK does not merge `<queries>` for you.** On Android 11+ (API 30),
   `PackageManager.getPackageInfo` cannot see an arbitrary installed package unless it is declared in
   a `<queries>` block in the *merged* manifest, and `QUERY_ALL_PACKAGES` is deliberately not
   requested. The React Native distribution declares all of them in
   `android/src/main/AndroidManifest.xml` and a CI drift check keeps the two in sync. The standalone
   component's manifest is empty: "native hosts own their finite visibility declarations"
   (`KnownAppLists.kt:8-14`). **A native host on API 30+ that does not declare these packages in its
   own manifest will see every app-presence field read `false` or empty**, indistinguishable from a
   clean device. `KnownAppLists.allQueriedPackages` gives you the exact list to declare.

### 7.3 `locationAgeMs` narrows a `Long` to an `Int`

```kotlin
locationAgeMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0).toInt()
```
(`GeolocationCollector.kt:40`)

The age is computed as a `Long` and then narrowed. `Int.MAX_VALUE` milliseconds is about 24.9 days,
so a cached fix older than that — or any fix whose timestamp is far in the past because of a device
clock change — wraps and can surface as a small or negative value. The field is typed `Int?`
(`GeolocationSignals.kt:15`), so the truncation is in the model, not only the conversion. Do not
treat `locationAgeMs` as reliable for long-stale fixes; cross-check against `provider` and
`accuracyMeters`, and reject negative values at ingestion.

### 7.4 The active probe's preserved defects

These are documented at the source and were carried over deliberately from the React Native
implementation, so a consumer must model them rather than assume they will be fixed:

- `scanPerformed` is **always `true`** — it records that the attempt was made, not that it succeeded
  (`FridaScanCollector.kt:34-36`).
- `defaultPortOpen = false` conflates connection refused, connect timeout, a missing `INTERNET`
  permission and any other socket failure. The consumer cannot tell "nothing listens" from "we could
  not find out" (`FridaScanCollector.kt:71-75`).
- `fridaHandshakeReject` reads exactly **one** `read` of up to 6 bytes. A partial or slow response
  reads as "no REJECT"; the prefix is never reassembled across reads
  (`FridaScanCollector.kt:59-63`).
- Every handshake failure — read timeout, reset, EOF, write error — collapses to `false`,
  indistinguishable from a listener that answered something other than `REJECT`
  (`FridaScanCollector.kt:64-68`).
- A `REJECT`-like reply does not authenticate any service. Any listener can answer with those bytes,
  so the flag is evidence, never identification (`FridaScanCollector.kt:39-41`).

### 7.5 GPU benchmark self-skips, and the skip is a build-string guess

`collectGpuBenchmark()` returns `benchmarkPerformed = false` with a `skippedReason` of `"emulator"`,
`"unsupported"` or `"error"` (`GpuBenchmarkCollector.kt:39-41`, `:59-95`, `:106-108`). The emulator
skip is decided before any GL work by a build-string heuristic matching `generic`, `emulator`, `sdk`,
`android sdk`, `goldfish`, `ranchu` or `vbox` (`GpuEmulatorHeuristic.kt:12-20`). Its own doc comment
says a positive match only means draw-call timing would be meaningless — it is **not** evidence about
the runtime environment, and must not be read as an emulator verdict
(`GpuEmulatorHeuristic.kt:5-8`).

Note also that `skipped()` preserves whatever was already collected (`:202-203`), so a
`benchmarkPerformed = false` result can still carry `rendererName`, `vendorName` and `maxTextureSize`
if the skip happened after the identity strings were read.

The collector runs on the caller's thread and temporarily makes an EGL context current there. It
saves and restores the thread's prior EGL binding, deletes only its own program, destroys only its
own surface and context, and never terminates the process-shared EGL display
(`GpuBenchmarkCollector.kt:109-129`). If the driver refuses restoration it at least releases our own
binding (`:121-122`). Do not run it on a thread that owns application rendering state.

### 7.6 Other surprises

- **`nativeSampleCount` can be below 256.** The timing loop reads the clock 257 times but records only
  *positive* deltas (`RuntimeTimingCollector.kt:10-16`). On a coarse clock many deltas are zero and
  are dropped, so a low sample count is itself an observation about timer resolution.
  `nativeTimerResolutionNs` is the minimum recorded interval, not a platform-reported resolution
  (`:20`).
- **`wifiRxBytes` / `wifiTxBytes` are derived, not measured.** They are total minus mobile, so they
  include ethernet, USB tethering and any other non-mobile transport, and are cumulative byte counters
  **since boot** — not a rate (`NetworkCollector.kt:153-170`, `NetworkSignals.kt:3-7`).
  `TrafficStats.UNSUPPORTED` (-1) is guarded and the field omitted (`NetworkCollector.kt:163-169`).
- **`localIpAddresses`, `interfaceNames`, `dnsServerAddresses` and `proxyHost` are sensitive.**
  Loopback and link-local addresses are filtered out, but real LAN addresses are emitted
  (`NetworkCollector.kt:196-199`). A failed interface enumeration returns `null` so the fields are
  omitted rather than reported as an empty inventory (`:205-208`).
- **`installerPackage` is attacker-influenced.** It is an alias of `installingPackageName` kept for
  compatibility, and install-source values are supplied by the package installer — raw context, not a
  Play recognition or licensing verdict (`ApplicationCollector.kt:128-130`).
- **`grantedPermissions` is the host app's own permission set**, read from its own `PackageInfo`
  (`ApplicationCollector.kt:103-117`). It is not a device-wide permission inventory.
- **`enabledAccessibilityServices` is a raw `Settings.Secure` string split on `:`**
  (`MediaBluetoothAppsCollector.kt:89-94`, `TransactionSafetyCollector.kt:37-42`), with no validation
  that the named components exist. In `TransactionSafetyCollector` a read failure yields `null`, so
  both `enabledAccessibilityServiceCount` and `accessibilityRunning` are omitted together.
- **`cpuMaxFrequencyMhz` reads sysfs** and is omitted when SELinux blocks the read
  (`HardwareCollector.kt:82-85`); its absence is not a hardware statement.
- **`screenBrightness` is the raw 0..255 linear setting normalized to 0..1**, not a perceptual value,
  and is not comparable to the iOS field without server-side normalization
  (`HardwareCollector.kt:73-76`).
- **`fontsDigest` hashes filenames, not font contents**, across `/system/fonts`, `/product/fonts`,
  `/system/font` and `/data/fonts`; an unreadable or absent directory contributes nothing and changes
  the digest silently (`HardwareCollector.kt:192-212`).
- **`processResidentMemoryBytes` depends on `/proc/self/statm`** and is omitted when that read fails
  (`HardwareCollector.kt:115-120`).
- **`osIntegrity.fridaListenerPortFound` is passive.** It parses `/proc/net/tcp(6)` and returns `null`
  when neither file is readable — which is the normal case on Android 10+, where those files are
  SELinux-restricted (`OsIntegrityCollector.kt:275-283`). It does not open a socket; that is the
  active component's job.
- **`GpuBenchmarkSignals.operationTimeCoefficientOfVariation` is omitted when the mean is not
  positive**, and `warmupSlope` requires at least four finite samples
  (`SignalStatistics.kt:28`, `:32-39`).
- **Consistency comparisons are not the SDK's job.** `collectNumericConsistency()` returns the native
  results only; comparing them against a JavaScript or other-runtime result belongs to the binding
  (`DeviceRiskSignals.kt:55`).
