package io.github.afanasievn.devicerisksignals

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * Opportunistic telephony reads only. Protected SIM-count collection requires an already granted
 * READ_PHONE_STATE permission. This library never requests permission or reads persistent identifiers
 * such as IMEI. Individual getter failures omit the corresponding observation.
 */
internal class TelephonyCollector(private val context: Context) {

  fun collect(): TelephonySignals {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
      ?: return TelephonySignals()

    // Persistent identifiers are intentionally outside this collection's scope.
    return TelephonySignals(
      phoneType = safe { phoneTypeName(tm.phoneType) }?.takeIf(String::isNotEmpty),
      networkOperatorName = safe { tm.networkOperatorName }?.takeIf(String::isNotEmpty),
      simOperatorName = safe { tm.simOperatorName }?.takeIf(String::isNotEmpty),
      networkCountryIso = safe { tm.networkCountryIso }?.takeIf(String::isNotEmpty),
      simCountryIso = safe { tm.simCountryIso }?.takeIf(String::isNotEmpty),
      simState = safe { simStateName(tm.simState) }?.takeIf(String::isNotEmpty),
      dataState = safe { dataStateName(tm.dataState) }?.takeIf(String::isNotEmpty),
      hasIccCard = safe { tm.hasIccCard() },
      isNetworkRoaming = safe { tm.isNetworkRoaming },
      simCount = activeSimCount(),
    )
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

  private inline fun <T> safe(block: () -> T): T? = try {
    block()
  } catch (e: Throwable) {
    null
  }
}
