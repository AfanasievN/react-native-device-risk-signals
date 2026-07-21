package com.reactnativedeviceintel

/** Pure normalization for documented BatteryManager integer observations. */
internal object BatterySignalClassifier {
  fun healthName(value: Int): String? = when (value) {
    1 -> "unknown"
    2 -> "good"
    3 -> "overheat"
    4 -> "dead"
    5 -> "overVoltage"
    6 -> "unspecifiedFailure"
    7 -> "cold"
    else -> null
  }

  fun powerSourceName(value: Int): String? = when (value) {
    0 -> "battery"
    1 -> "ac"
    2 -> "usb"
    4 -> "wireless"
    8 -> "dock"
    else -> null
  }

  fun nonNegative(value: Long): Long? = value.takeIf { it >= 0 }
}
