package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkObservationPolicyTest {
  @Test
  fun `omits connectivity when permission or reads are unavailable`() {
    assertNull(NetworkObservationPolicy.connectivity(false, null, null))
    assertNull(NetworkObservationPolicy.connectivity(true, null, null))
    assertNull(NetworkObservationPolicy.connectivity(true, true, null))
    assertNull(NetworkObservationPolicy.connectivity(true, true, false))
  }

  @Test
  fun `distinguishes a successfully observed disconnection`() {
    val value = NetworkObservationPolicy.connectivity(true, false, null)
    assertEquals(false, value?.isConnected)
    assertEquals("none", value?.connectionType)
  }

  @Test
  fun `marks connectivity only when capabilities were observed`() {
    val value = NetworkObservationPolicy.connectivity(true, true, true)
    assertTrue(value?.isConnected == true)
    assertNull(value?.connectionType)
  }
}
