package io.github.afanasievn.devicerisksignals

/** Raw results of fixed numeric operations; these values do not assign a risk verdict. */
data class NumericConsistencySignals(
  val integerVectorResult: Long,
  val floatVector: List<Double>,
  val signedZeroPreserved: Boolean,
  val subnormalPreserved: Boolean,
) {
  fun toRawMap(): Map<String, Any> = mapOf(
    "integerVectorResult" to integerVectorResult,
    "floatVector" to floatVector,
    "signedZeroPreserved" to signedZeroPreserved,
    "subnormalPreserved" to subnormalPreserved,
  )
}
