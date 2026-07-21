package com.reactnativedeviceintel

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

internal data class NumericConsistencyResult(
  val integerVectorResult: Long,
  val floatVector: List<Double>,
  val signedZeroPreserved: Boolean,
  val subnormalPreserved: Boolean,
)

internal object NumericConsistencyCalculator {
  fun calculate(): NumericConsistencyResult {
    var hash = 2166136261L
    for (index in 0 until 1024) {
      val mixedIndex = (index.toLong() * 2654435761L) and UINT32_MASK
      hash = (hash xor mixedIndex) and UINT32_MASK
      hash = (hash * 16777619L) and UINT32_MASK
    }
    return NumericConsistencyResult(
      integerVectorResult = hash,
      floatVector = listOf(sqrt(2.0), sin(0.5), cos(0.5), ln(2.0), exp(0.25)),
      signedZeroPreserved = 1.0 / -0.0 == Double.NEGATIVE_INFINITY,
      subnormalPreserved = Double.MIN_VALUE > 0.0 && Double.MIN_VALUE * 1.0 == Double.MIN_VALUE,
    )
  }

  private const val UINT32_MASK = 0xffffffffL
}

internal class NumericConsistencyProvider {
  fun getNumericConsistencySignals(): WritableMap {
    val result = NumericConsistencyCalculator.calculate()
    val floats = Arguments.createArray()
    result.floatVector.forEach(floats::pushDouble)
    return Arguments.createMap().apply {
      putDouble("integerVectorResult", result.integerVectorResult.toDouble())
      putArray("floatVector", floats)
      putBoolean("signedZeroPreserved", result.signedZeroPreserved)
      putBoolean("subnormalPreserved", result.subnormalPreserved)
    }
  }
}
