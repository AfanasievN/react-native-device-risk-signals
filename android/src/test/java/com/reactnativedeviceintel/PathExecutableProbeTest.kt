package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathExecutableProbeTest {
  @Test
  fun `finds an executable by inspecting absolute PATH candidates`() {
    val inspected = mutableListOf<String>()

    val found = PathExecutableProbe.existsOnPath("/system/bin:/vendor/bin", "su") { candidate ->
      inspected.add(candidate)
      candidate == "/vendor/bin/su"
    }

    assertTrue(found == true)
    assertEquals(listOf("/system/bin/su", "/vendor/bin/su"), inspected)
  }

  @Test
  fun `returns false only after every available PATH candidate was inspected`() {
    assertFalse(
      PathExecutableProbe.existsOnPath("/system/bin:/vendor/bin", "su") { false } ?: true,
    )
  }

  @Test
  fun `omits unavailable or failed PATH observations`() {
    assertNull(PathExecutableProbe.existsOnPath(null, "su") { true })
    assertNull(
      PathExecutableProbe.existsOnPath("/system/bin", "su") {
        throw SecurityException("denied")
      },
    )
  }

  @Test
  fun `ignores empty and relative PATH entries`() {
    val inspected = mutableListOf<String>()

    val found = PathExecutableProbe.existsOnPath(":/system/bin:relative:./local:/vendor/bin/", "su") {
      inspected.add(it)
      false
    }

    assertFalse(found ?: true)
    assertEquals(listOf("/system/bin/su", "/vendor/bin/su"), inspected)
  }
}
