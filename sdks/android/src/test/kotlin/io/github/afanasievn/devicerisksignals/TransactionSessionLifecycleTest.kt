package io.github.afanasievn.devicerisksignals

import org.junit.Assert.*
import org.junit.Test

class TransactionSessionLifecycleTest {
  @Test fun `inactive until attach and detached callbacks never become active again`() {
    val lifecycle = TransactionSessionLifecycle()
    assertFalse(lifecycle.isActive(0))
    val first = lifecycle.attach()
    assertTrue(lifecycle.isActive(first))
    lifecycle.detach()
    assertFalse(lifecycle.isActive(first))
    val second = lifecycle.attach()
    assertTrue(lifecycle.isActive(second))
    assertFalse(lifecycle.isActive(first))
  }

  @Test fun `close invalidates callbacks and is terminal and idempotent`() {
    val lifecycle = TransactionSessionLifecycle()
    val generation = lifecycle.attach()
    lifecycle.close()
    lifecycle.close()
    lifecycle.detach()
    assertFalse(lifecycle.isActive(generation))
    try {
      lifecycle.attach()
      fail("Closed session must not attach")
    } catch (_: IllegalStateException) {
      // Expected: a fresh session is required for new observations.
    }
  }

  @Test fun `replacing attachment invalidates prior callback generation`() {
    val lifecycle = TransactionSessionLifecycle()
    val first = lifecycle.attach()
    val second = lifecycle.attach()
    assertFalse(lifecycle.isActive(first))
    assertTrue(lifecycle.isActive(second))
  }
}
