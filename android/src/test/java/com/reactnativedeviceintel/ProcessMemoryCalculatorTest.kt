package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessMemoryCalculatorTest {
  @Test
  fun `converts resident pages from statm to bytes`() {
    assertEquals(
      1_048_576L,
      ProcessMemoryCalculator.residentBytes("1024 256 80 12 0 99 0", 4096L),
    )
  }

  @Test
  fun `omits malformed unavailable and overflowing values`() {
    assertNull(ProcessMemoryCalculator.residentBytes("", 4096L))
    assertNull(ProcessMemoryCalculator.residentBytes("1024 not-a-number", 4096L))
    assertNull(ProcessMemoryCalculator.residentBytes("1024 256", 0L))
    assertNull(ProcessMemoryCalculator.residentBytes("1 ${Long.MAX_VALUE}", 4096L))
  }
}
