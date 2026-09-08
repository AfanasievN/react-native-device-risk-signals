package io.github.afanasievn.devicerisksignals

/** Audio output properties and their coarse latency estimate, not a loopback measurement. */
data class AudioLatencySignals(
  val framesPerBuffer: Int? = null,
  val nativeSampleRate: Int? = null,
  val outputLatencyMs: Double? = null,
  val measured: Boolean,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    framesPerBuffer?.let { put("framesPerBuffer", it) }
    nativeSampleRate?.let { put("nativeSampleRate", it) }
    outputLatencyMs?.let { put("outputLatencyMs", it) }
    put("measured", measured)
  }
}
