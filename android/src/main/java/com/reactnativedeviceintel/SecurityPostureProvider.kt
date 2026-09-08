package com.reactnativedeviceintel

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import io.github.afanasievn.devicerisksignals.DeviceRiskSignals

/** Combines SDK point-in-time and session maps at the React Native boundary. */
class SecurityPostureProvider(context: ReactApplicationContext) {
  private val signals = DeviceRiskSignals(context)
  private val transactionObserver = TransactionSafetyObserver(context)

  fun getTransactionSafetySignals(): WritableMap {
    val raw = signals.collectTransactionSafety().toRawMap().toMutableMap()
    transactionObserver.attachAndSnapshot()?.let { raw.putAll(it.toRawMap()) }
    return ReactNativeValueConverter.toWritableMap(raw)
  }

  fun dispose() = transactionObserver.dispose()
}
