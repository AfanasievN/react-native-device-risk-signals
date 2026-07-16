package com.reactnativedeviceintel

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

/**
 * telephony — opportunistic reads only. This library does not request READ_PHONE_STATE or trigger a
 * prompt, and IMEI is deliberately NOT attempted (READ_PRIVILEGED_PHONE_STATE is not
 * available to third-party apps since API 29 — it would only ever return null / throw). Everything
 * here is a getter that works without a runtime prompt; each is wrapped so a vendor quirk can't
 * throw the probe.
 */
class TelephonyInfoProvider(private val context: Context) {

  fun getTelephonySignals(): WritableMap {
    val map = Arguments.createMap()
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
      ?: return map

    putStringIfPresent(map, "phoneType", safe { phoneTypeName(tm.phoneType) })
    putStringIfPresent(map, "networkOperatorName", safe { tm.networkOperatorName })
    putStringIfPresent(map, "simOperatorName", safe { tm.simOperatorName })
    putStringIfPresent(map, "networkCountryIso", safe { tm.networkCountryIso })
    putStringIfPresent(map, "simCountryIso", safe { tm.simCountryIso })
    putStringIfPresent(map, "simState", safe { simStateName(tm.simState) })
    putStringIfPresent(map, "dataState", safe { dataStateName(tm.dataState) })

    safe { tm.hasIccCard() }?.let { map.putBoolean("hasIccCard", it) }
    safe { tm.isNetworkRoaming }?.let { map.putBoolean("isNetworkRoaming", it) }
    activeSimCount()?.let { map.putInt("simCount", it) }

    // imei intentionally omitted (see class doc) — the TS contract keeps the optional field so the
    // absence is explicit downstream.
    return map
  }

  @SuppressLint("MissingPermission")
  private fun activeSimCount(): Int? = safe {
    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
      return@safe null
    }
    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    sm?.activeSubscriptionInfoCount
  }

  private fun phoneTypeName(type: Int): String = when (type) {
    TelephonyManager.PHONE_TYPE_GSM -> "gsm"
    TelephonyManager.PHONE_TYPE_CDMA -> "cdma"
    TelephonyManager.PHONE_TYPE_SIP -> "sip"
    else -> "none"
  }

  private fun simStateName(state: Int): String = when (state) {
    TelephonyManager.SIM_STATE_ABSENT -> "absent"
    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "pin_required"
    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "puk_required"
    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "network_locked"
    TelephonyManager.SIM_STATE_READY -> "ready"
    TelephonyManager.SIM_STATE_NOT_READY -> "not_ready"
    TelephonyManager.SIM_STATE_PERM_DISABLED -> "perm_disabled"
    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "card_io_error"
    TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "card_restricted"
    else -> "unknown"
  }

  private fun dataStateName(state: Int): String = when (state) {
    TelephonyManager.DATA_DISCONNECTED -> "disconnected"
    TelephonyManager.DATA_CONNECTING -> "connecting"
    TelephonyManager.DATA_CONNECTED -> "connected"
    TelephonyManager.DATA_SUSPENDED -> "suspended"
    else -> "unknown"
  }

  private fun putStringIfPresent(map: WritableMap, key: String, value: String?) {
    if (!value.isNullOrEmpty()) map.putString(key, value)
  }

  private inline fun <T> safe(block: () -> T): T? = try {
    block()
  } catch (e: Throwable) {
    null
  }
}
