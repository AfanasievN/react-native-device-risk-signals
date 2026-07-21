package com.reactnativedeviceintel

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

internal data class DistributionSummary(
  val sampleCount: Int,
  val median: Double,
  val p95: Double,
  val mad: Double,
  val coefficientOfVariation: Double?,
)

internal object SignalStatistics {
  fun summarize(values: List<Double>): DistributionSummary? {
    val sorted = values.filter { it.isFinite() }.sorted()
    if (sorted.isEmpty()) return null
    val median = percentile(sorted, 0.5)
    val deviations = sorted.map { kotlin.math.abs(it - median) }.sorted()
    val mean = sorted.average()
    val standardDeviation = sqrt(sorted.sumOf { (it - mean).pow(2) } / sorted.size)
    return DistributionSummary(
      sampleCount = sorted.size,
      median = median,
      p95 = percentile(sorted, 0.95),
      mad = percentile(deviations, 0.5),
      coefficientOfVariation = if (mean > 0.0) standardDeviation / mean else null,
    )
  }

  fun warmupSlope(values: List<Double>): Double? {
    val finite = values.filter { it.isFinite() }
    if (finite.size < 4) return null
    val midpoint = finite.size / 2
    val firstMean = finite.take(midpoint).average()
    if (firstMean <= 0.0) return null
    val secondMean = finite.drop(midpoint).average()
    return (secondMean - firstMean) / firstMean
  }

  private fun percentile(sorted: List<Double>, percentile: Double): Double {
    val index = (ceil(percentile * sorted.size).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
  }
}
