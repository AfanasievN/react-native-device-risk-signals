package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericConsistencyCollectorTest {
  @Test
  fun `collects stable unsigned integer and ordered finite floating point observations`() {
    val result: NumericConsistencySignals = NumericConsistencyCollector.collect()

    assertEquals(3373885893L, result.integerVectorResult)
    val expected = listOf(
      1.4142135623730951,
      0.479425538604203,
      0.8775825618903728,
      0.6931471805599453,
      1.2840254166877414,
    )
    assertEquals(expected.size, result.floatVector.size)
    expected.zip(result.floatVector).forEach { (expectedValue, actualValue) ->
      assertEquals(expectedValue, actualValue, 1e-15)
    }
    assertTrue(result.floatVector.all { it.isFinite() })
    assertTrue(result.signedZeroPreserved)
    assertTrue(result.subnormalPreserved)
  }

  @Test
  fun `repeated collection produces the same raw values`() {
    assertEquals(NumericConsistencyCollector.collect(), NumericConsistencyCollector.collect())
  }

  @Test
  fun `raw map retains field names numeric precision vector and false observations`() {
    val signals = NumericConsistencySignals(3373885893L, listOf(1.0, 2.0), false, false)

    assertEquals(
      mapOf(
        "integerVectorResult" to 3373885893L,
        "floatVector" to listOf(1.0, 2.0),
        "signedZeroPreserved" to false,
        "subnormalPreserved" to false,
      ),
      signals.toRawMap(),
    )
  }
}
