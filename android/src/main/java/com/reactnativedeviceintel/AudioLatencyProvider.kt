package com.reactnativedeviceintel

import android.content.Context
import android.media.AudioManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap

/**
 * audio_latency (Android) — cheap AudioManager property reads (no engine, no permission). Reports the
 * native output frames-per-buffer and sample rate, plus a derived output-latency estimate
 * (framesPerBuffer / sampleRate). A full AAudio loopback measurement is a possible future upgrade if
 * these coarse values prove insufficient. Never runs until config enables the probe (ships disabled).
 */
class AudioLatencyProvider(private val context: Context) {

  fun getAudioLatency(): WritableMap {
    val map = Arguments.createMap()
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio == null) {
      map.putBoolean("measured", false)
      return map
    }

    val framesPerBuffer = readIntProperty(audio, AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
    val sampleRate = readIntProperty(audio, AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)

    if (framesPerBuffer != null) map.putInt("framesPerBuffer", framesPerBuffer)
    if (sampleRate != null) map.putInt("nativeSampleRate", sampleRate)
    if (framesPerBuffer != null && sampleRate != null && sampleRate > 0) {
      map.putDouble("outputLatencyMs", framesPerBuffer.toDouble() / sampleRate.toDouble() * 1000.0)
    }

    map.putBoolean("measured", framesPerBuffer != null || sampleRate != null)
    return map
  }

  private fun readIntProperty(audio: AudioManager, key: String): Int? = try {
    audio.getProperty(key)?.toIntOrNull()
  } catch (e: Throwable) {
    null
  }
}
