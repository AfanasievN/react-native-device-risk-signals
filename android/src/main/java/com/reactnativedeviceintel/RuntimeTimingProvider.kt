package com.reactnativedeviceintel

import android.os.SystemClock
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

internal class RuntimeTimingProvider {
  fun getRuntimeTimingSignals(): WritableMap {
    val intervals = mutableListOf<Double>()
    var previous = SystemClock.elapsedRealtimeNanos()
    repeat(SAMPLE_COUNT) {
      val current = SystemClock.elapsedRealtimeNanos()
      val delta = current - previous
      if (delta > 0) intervals.add(delta.toDouble())
      previous = current
    }
    val summary = SignalStatistics.summarize(intervals)
    return Arguments.createMap().apply {
      putString("nativeClockSource", "elapsed_realtime_nanos")
      putInt("nativeSampleCount", intervals.size)
      putDouble("nativeTimerResolutionNs", intervals.minOrNull() ?: 0.0)
      putDouble("nativeIntervalMedianNs", summary?.median ?: 0.0)
      putDouble("nativeIntervalP95Ns", summary?.p95 ?: 0.0)
      putDouble("nativeIntervalMadNs", summary?.mad ?: 0.0)
    }
  }

  private companion object {
    const val SAMPLE_COUNT = 256
  }
}
