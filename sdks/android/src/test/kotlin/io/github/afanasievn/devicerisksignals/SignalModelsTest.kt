package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SignalModelsTest {
  @Test
  fun `device identity serializes typed values and nested build`() {
    val signals = DeviceIdentitySignals(
      manufacturer = "Google",
      model = "Pixel 8",
      brand = "google",
      systemVersion = "15",
      isTablet = false,
      kernelVersion = "build-version",
      androidBuild = AndroidBuildSignals(
        fingerprint = "google/pixel/release-keys",
        supportedAbis = listOf("arm64-v8a"),
        sdkInt = 35,
        buildTimeMs = 1_725_000_000_000,
      ),
    )

    assertEquals(
      mapOf(
        "manufacturer" to "Google",
        "model" to "Pixel 8",
        "brand" to "google",
        "systemName" to "android",
        "systemVersion" to "15",
        "isTablet" to false,
        "kernelVersion" to "build-version",
        "androidBuild" to mapOf(
          "fingerprint" to "google/pixel/release-keys",
          "supportedAbis" to listOf("arm64-v8a"),
          "sdkInt" to 35,
          "buildTimeMs" to 1_725_000_000_000,
        ),
      ),
      signals.toRawMap(),
    )
  }

  @Test
  fun `device identity omits unavailable optional values`() {
    val raw = DeviceIdentitySignals(
      manufacturer = "Google",
      model = "Pixel",
      brand = "google",
      systemVersion = "15",
      isTablet = false,
      androidBuild = AndroidBuildSignals(),
    ).toRawMap()

    assertFalse(raw.containsKey("kernelVersion"))
    assertEquals(emptyMap<String, Any>(), raw["androidBuild"])
  }

  @Test
  fun `locale serializes lists and omits unavailable scalar values`() {
    val raw = LocaleSignals(
      language = "en",
      languages = listOf("en-US", "ru-RU"),
      timezoneId = "Europe/Moscow",
      timezoneOffsetMinutes = 180,
      uses24HourClock = true,
      keyboardLanguages = listOf("en-US"),
    ).toRawMap()

    assertEquals(listOf("en-US", "ru-RU"), raw["languages"])
    assertEquals(listOf("en-US"), raw["keyboardLanguages"])
    assertFalse(raw.containsKey("country"))
    assertFalse(raw.containsKey("currencyCode"))
  }
}
