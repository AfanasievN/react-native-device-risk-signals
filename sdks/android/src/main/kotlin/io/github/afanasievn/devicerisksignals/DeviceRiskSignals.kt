package io.github.afanasievn.devicerisksignals

import android.content.Context

/**
 * Standalone Android entry point. Collection is local and synchronous, never requests permissions,
 * and returns raw observations rather than a risk verdict. Protected fields require host grants.
 */
class DeviceRiskSignals(context: Context) {
  private val applicationContext = context.applicationContext
  private val deviceIdentityCollector = DeviceIdentityCollector(applicationContext)
  private val localeCollector = LocaleCollector(applicationContext)

  fun collectDeviceIdentity(): DeviceIdentitySignals = deviceIdentityCollector.collect()

  fun collectLocale(): LocaleSignals = localeCollector.collect()

  /** Observes only the host application's package, signing, install, and process state. */
  fun collectApplication(): ApplicationSignals = ApplicationCollector(applicationContext).collect()

  fun collectHardware(): HardwareSignals = HardwareCollector(applicationContext).collect()

  /** Explicit font-directory read, kept separate from hardware collection. */
  fun collectFonts(): FontsSignals = HardwareCollector(applicationContext).collectFonts()

  /** Passive process/device observations. Does not connect to localhost or open network sockets. */
  fun collectOsIntegrity(): OsIntegritySignals = OsIntegrityCollector(applicationContext).collect()

  /** Reads local connectivity/interface state; does not send a network request. */
  fun collectNetwork(): NetworkSignals = NetworkCollector(applicationContext).collect()

  fun collectTelephony(): TelephonySignals = TelephonyCollector(applicationContext).collect()

  /** Reads cached fixes only when the host already has a location grant. */
  fun collectGeolocation(): GeolocationSignals = GeolocationCollector(applicationContext).collect()

  /** Sensitive finite app/accessibility observations; Bluetooth reads bonded count only. */
  fun collectMediaBluetoothApps(): MediaBluetoothAppsSignals =
    MediaBluetoothAppsCollector(applicationContext).collect()

  /** Point-in-time platform reads only; does not start transaction observers. */
  fun collectDeviceSecurityPosture(): DeviceSecurityPostureSignals =
    DeviceSecurityPostureCollector(applicationContext).collect()

  /** Explicit opt-in measurement: reads the monotonic clock 257 times synchronously. */
  fun collectRuntimeTiming(): RuntimeTimingSignals = RuntimeTimingCollector().collect()

  /** Explicit opt-in numeric workload; comparisons with JavaScript belong to the binding. */
  fun collectNumericConsistency(): NumericConsistencySignals = NumericConsistencyCollector.collect()

  /** Audio output properties only; does not start audio playback or record sound. */
  fun collectAudioLatency(): AudioLatencySignals = AudioLatencyCollector(applicationContext).collect()
}
