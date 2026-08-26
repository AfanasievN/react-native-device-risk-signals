package io.github.afanasievn.devicerisksignals

import android.content.Context

/**
 * Standalone Android entry point. Collection is local, synchronous, permission-free for the methods
 * currently exposed here, and returns raw observations rather than a risk verdict.
 */
class DeviceRiskSignals(context: Context) {
  private val applicationContext = context.applicationContext
  private val deviceIdentityCollector = DeviceIdentityCollector(applicationContext)
  private val localeCollector = LocaleCollector(applicationContext)

  fun collectDeviceIdentity(): DeviceIdentitySignals = deviceIdentityCollector.collect()

  fun collectLocale(): LocaleSignals = localeCollector.collect()
}
