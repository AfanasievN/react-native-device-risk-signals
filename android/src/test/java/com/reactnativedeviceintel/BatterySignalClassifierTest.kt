package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatterySignalClassifierTest {
  @Test
  fun `maps documented battery health observations`() {
    assertEquals("good", BatterySignalClassifier.healthName(2))
    assertEquals("overheat", BatterySignalClassifier.healthName(3))
    assertEquals("dead", BatterySignalClassifier.healthName(4))
    assertEquals("overVoltage", BatterySignalClassifier.healthName(5))
    assertEquals("unspecifiedFailure", BatterySignalClassifier.healthName(6))
    assertEquals("cold", BatterySignalClassifier.healthName(7))
    assertEquals("unknown", BatterySignalClassifier.healthName(1))
  }

  @Test
  fun `maps known power sources including running on battery`() {
    assertEquals("battery", BatterySignalClassifier.powerSourceName(0))
    assertEquals("ac", BatterySignalClassifier.powerSourceName(1))
    assertEquals("usb", BatterySignalClassifier.powerSourceName(2))
    assertEquals("wireless", BatterySignalClassifier.powerSourceName(4))
    assertEquals("dock", BatterySignalClassifier.powerSourceName(8))
  }

  @Test
  fun `omits undocumented and unavailable values`() {
    assertNull(BatterySignalClassifier.healthName(-1))
    assertNull(BatterySignalClassifier.healthName(999))
    assertNull(BatterySignalClassifier.powerSourceName(-1))
    assertNull(BatterySignalClassifier.powerSourceName(999))
    assertNull(BatterySignalClassifier.nonNegative(-1))
    assertEquals(0L, BatterySignalClassifier.nonNegative(0))
  }
}
