package com.reactnativedeviceintel

import android.content.Context
import android.text.format.DateFormat
import android.view.inputmethod.InputMethodManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

/**
 * locale — trivial, permission-free, high-entropy. Includes the enabled input-method (keyboard)
 * languages, which need no permission and are a strong device-farm / identity-mismatch tell.
 */
class LocaleInfoProvider(private val context: Context) {

  fun getLocaleSignals(): WritableMap {
    val map = Arguments.createMap()
    val locale = Locale.getDefault()

    putStringIfPresent(map, "language", safe { locale.language })
    putStringIfPresent(map, "country", safe { locale.country })
    map.putArray("languages", toStringArray(preferredLanguages()))

    val tz = safe { TimeZone.getDefault() }
    if (tz != null) {
      putStringIfPresent(map, "timezoneId", tz.id)
      // getOffset(now) includes DST; divide ms → minutes.
      map.putInt("timezoneOffsetMinutes", tz.getOffset(System.currentTimeMillis()) / 60000)
    }

    safe { DateFormat.is24HourFormat(context) }?.let { map.putBoolean("uses24HourClock", it) }
    putStringIfPresent(map, "currencyCode", safe { Currency.getInstance(locale).currencyCode })

    val symbols = safe { DecimalFormatSymbols(locale) }
    if (symbols != null) {
      putStringIfPresent(map, "decimalSeparator", symbols.decimalSeparator.toString())
      putStringIfPresent(map, "groupingSeparator", symbols.groupingSeparator.toString())
    }

    safe { Calendar.getInstance(locale).firstDayOfWeek }?.let { map.putInt("firstDayOfWeek", it) }

    val keyboards = keyboardLanguages()
    if (keyboards.isNotEmpty()) map.putArray("keyboardLanguages", toStringArray(keyboards))

    // measurementSystem intentionally omitted on Android — no first-class API; iOS reports it.
    return map
  }

  private fun preferredLanguages(): List<String> {
    val result = LinkedHashSet<String>()
    try {
      val locales = context.resources.configuration.locales
      for (i in 0 until locales.size()) {
        locales.get(i)?.toLanguageTag()?.let { if (it.isNotEmpty()) result.add(it) }
      }
    } catch (e: Throwable) {
      safe { Locale.getDefault().toLanguageTag() }?.let { result.add(it) }
    }
    return result.toList()
  }

  private fun keyboardLanguages(): List<String> {
    val result = LinkedHashSet<String>()
    try {
      val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return emptyList()
      for (imi in imm.enabledInputMethodList) {
        try {
          val subtypes = imm.getEnabledInputMethodSubtypeList(imi, true) ?: continue
          for (subtype in subtypes) {
            val tag = subtype.languageTag
            if (!tag.isNullOrEmpty()) {
              result.add(tag)
            } else {
              @Suppress("DEPRECATION")
              val legacy = subtype.locale
              if (!legacy.isNullOrEmpty()) result.add(legacy)
            }
          }
        } catch (e: Exception) {
          // Skip a misbehaving IME; keep the rest.
        }
      }
    } catch (e: Throwable) {
      // No IMM visibility — return whatever we gathered.
    }
    return result.toList()
  }

  private fun toStringArray(values: List<String>): WritableArray {
    val arr = Arguments.createArray()
    for (v in values) arr.pushString(v)
    return arr
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
