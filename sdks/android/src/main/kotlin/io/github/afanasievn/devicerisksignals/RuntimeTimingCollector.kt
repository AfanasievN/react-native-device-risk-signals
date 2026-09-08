package io.github.afanasievn.devicerisksignals

import android.os.SystemClock

internal class RuntimeTimingCollector(
  private val clock: () -> Long = SystemClock::elapsedRealtimeNanos,
) {
  fun collect(): RuntimeTimingSignals {
    val intervals = mutableListOf<Double>()
    var previous = clock()
    repeat(256) {
      val current = clock()
      val delta = current - previous
      if (delta > 0) intervals.add(delta.toDouble())
      previous = current
    }
    val summary = SignalStatistics.summarize(intervals)
    return RuntimeTimingSignals(
      nativeSampleCount = intervals.size,
      nativeTimerResolutionNs = intervals.minOrNull(),
      nativeIntervalMedianNs = summary?.median,
      nativeIntervalP95Ns = summary?.p95,
      nativeIntervalMadNs = summary?.mad,
    )
  }
}
