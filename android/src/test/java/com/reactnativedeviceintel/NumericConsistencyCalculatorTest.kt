package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericConsistencyCalculatorTest {
  @Test
  fun `produces stable integer vector and finite float vector`() {
    val result = NumericConsistencyCalculator.calculate()

    assertEquals(3373885893L, result.integerVectorResult)
    assertEquals(5, result.floatVector.size)
    assertTrue(result.floatVector.all { it.isFinite() })
    assertTrue(result.signedZeroPreserved)
    assertTrue(result.subnormalPreserved)
  }
}
