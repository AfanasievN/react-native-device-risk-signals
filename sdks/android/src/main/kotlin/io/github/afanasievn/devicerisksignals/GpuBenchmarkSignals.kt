package io.github.afanasievn.devicerisksignals

/** Raw, high-entropy GPU observations from an explicitly requested offscreen benchmark. */
data class GpuBenchmarkSignals(
  val benchmarkPerformed: Boolean? = null,
  val skippedReason: String? = null,
  val rendererName: String? = null,
  val vendorName: String? = null,
  val apiVersion: String? = null,
  val shadingLanguageVersion: String? = null,
  val maxTextureSize: Int? = null,
  val drawCallsCompleted: Int? = null,
  val durationMs: Int? = null,
  val operationTimeP50Ms: Double? = null,
  val operationTimeP95Ms: Double? = null,
  val operationTimeMadMs: Double? = null,
  val operationTimeCoefficientOfVariation: Double? = null,
  val warmupSlope: Double? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    benchmarkPerformed?.let { put("benchmarkPerformed", it) }
    skippedReason?.let { put("skippedReason", it) }
    rendererName?.let { put("rendererName", it) }
    vendorName?.let { put("vendorName", it) }
    apiVersion?.let { put("apiVersion", it) }
    shadingLanguageVersion?.let { put("shadingLanguageVersion", it) }
    maxTextureSize?.let { put("maxTextureSize", it) }
    drawCallsCompleted?.let { put("drawCallsCompleted", it) }
    durationMs?.let { put("durationMs", it) }
    operationTimeP50Ms?.let { put("operationTimeP50Ms", it) }
    operationTimeP95Ms?.let { put("operationTimeP95Ms", it) }
    operationTimeMadMs?.let { put("operationTimeMadMs", it) }
    operationTimeCoefficientOfVariation?.let { put("operationTimeCoefficientOfVariation", it) }
    warmupSlope?.let { put("warmupSlope", it) }
  }
}
