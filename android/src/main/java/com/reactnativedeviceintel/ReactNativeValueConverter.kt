package com.reactnativedeviceintel

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

/** Framework boundary: converts SDK-owned Kotlin values to React Native bridge containers. */
internal object ReactNativeValueConverter {
  fun toWritableMap(values: Map<String, *>): WritableMap = Arguments.createMap().apply {
    for ((key, value) in values) putValue(key, value)
  }

  private fun WritableMap.putValue(key: String, value: Any?) {
    when (value) {
      null -> putNull(key)
      is Boolean -> putBoolean(key, value)
      is Byte -> putInt(key, value.toInt())
      is Short -> putInt(key, value.toInt())
      is Int -> putInt(key, value)
      is Long -> putDouble(key, value.toDouble())
      is Float -> putDouble(key, value.toDouble())
      is Double -> putDouble(key, value)
      is String -> putString(key, value)
      is Map<*, *> -> putMap(key, toWritableMap(value.stringKeyed()))
      is Iterable<*> -> putArray(key, toWritableArray(value))
      else -> error("Unsupported Device Risk Signals value: ${value::class.java.name}")
    }
  }

  private fun toWritableArray(values: Iterable<*>): WritableArray = Arguments.createArray().apply {
    for (value in values) {
      when (value) {
        null -> pushNull()
        is Boolean -> pushBoolean(value)
        is Byte -> pushInt(value.toInt())
        is Short -> pushInt(value.toInt())
        is Int -> pushInt(value)
        is Long -> pushDouble(value.toDouble())
        is Float -> pushDouble(value.toDouble())
        is Double -> pushDouble(value)
        is String -> pushString(value)
        is Map<*, *> -> pushMap(toWritableMap(value.stringKeyed()))
        is Iterable<*> -> pushArray(toWritableArray(value))
        else -> error("Unsupported Device Risk Signals value: ${value::class.java.name}")
      }
    }
  }

  private fun Map<*, *>.stringKeyed(): Map<String, *> = entries.associate { (key, value) ->
    require(key is String) { "Device Risk Signals map keys must be strings" }
    key to value
  }
}
