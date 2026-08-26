package io.github.afanasievn.devicerisksignals

import android.content.Context
import android.text.format.DateFormat
import android.view.inputmethod.InputMethodManager
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

internal class LocaleCollector(private val context: Context) {
  fun collect(): LocaleSignals {
    val locale = Locale.getDefault()
    val timezone = read { TimeZone.getDefault() }
    val symbols = read { DecimalFormatSymbols(locale) }
    val keyboards = keyboardLanguages()

    return LocaleSignals(
      language = readString { locale.language },
      country = readString { locale.country },
      languages = preferredLanguages(),
      timezoneId = timezone?.id?.takeIf(String::isNotEmpty),
      timezoneOffsetMinutes = timezone?.getOffset(System.currentTimeMillis())?.div(60_000),
      uses24HourClock = read { DateFormat.is24HourFormat(context) },
      currencyCode = readString { Currency.getInstance(locale).currencyCode },
      decimalSeparator = symbols?.decimalSeparator?.toString(),
      groupingSeparator = symbols?.groupingSeparator?.toString(),
      firstDayOfWeek = read { Calendar.getInstance(locale).firstDayOfWeek },
      keyboardLanguages = keyboards.takeIf(List<String>::isNotEmpty),
    )
  }

  private fun preferredLanguages(): List<String> {
    val result = linkedSetOf<String>()
    try {
      val locales = context.resources.configuration.locales
      for (index in 0 until locales.size()) {
        locales[index]?.toLanguageTag()?.takeIf(String::isNotEmpty)?.let(result::add)
      }
    } catch (_: Throwable) {
      readString { Locale.getDefault().toLanguageTag() }?.let(result::add)
    }
    return result.toList()
  }

  private fun keyboardLanguages(): List<String> {
    val result = linkedSetOf<String>()
    try {
      val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return emptyList()
      for (inputMethod in manager.enabledInputMethodList) {
        try {
          for (subtype in manager.getEnabledInputMethodSubtypeList(inputMethod, true).orEmpty()) {
            if (subtype.languageTag.isNotEmpty()) {
              result.add(subtype.languageTag)
            } else {
              @Suppress("DEPRECATION")
              subtype.locale.takeIf(String::isNotEmpty)?.let(result::add)
            }
          }
        } catch (_: Exception) {
          // Skip a misbehaving input method and preserve the remaining observations.
        }
      }
    } catch (_: Throwable) {
      // Input-method visibility is unavailable; omit keyboardLanguages.
    }
    return result.toList()
  }

  private fun readString(block: () -> String?): String? = read(block)?.takeIf(String::isNotEmpty)

  private inline fun <T> read(block: () -> T): T? = try {
    block()
  } catch (_: Throwable) {
    null
  }
}
