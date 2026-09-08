package io.github.afanasievn.devicerisksignals

import android.content.Context
import android.media.AudioManager

/** Permission-free property reads. Does not start an audio engine. */
internal class AudioLatencyCollector(private val context: Context) {
  fun collect(): AudioLatencySignals {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      ?: return AudioLatencySignals(measured = false)
    return fromProperties(
      readProperty(audio, AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER),
      readProperty(audio, AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),
    )
  }

  private fun readProperty(audio: AudioManager, key: String): String? = try {
    audio.getProperty(key)
  } catch (_: Throwable) {
    null
  }

  internal companion object {
    fun fromProperties(frames: String?, rate: String?): AudioLatencySignals {
      val framesPerBuffer = frames?.toIntOrNull()
      val sampleRate = rate?.toIntOrNull()
      return AudioLatencySignals(
        framesPerBuffer = framesPerBuffer,
        nativeSampleRate = sampleRate,
        outputLatencyMs = if (framesPerBuffer != null && sampleRate != null && sampleRate > 0) {
          framesPerBuffer.toDouble() / sampleRate.toDouble() * 1000.0
        } else null,
        measured = framesPerBuffer != null || sampleRate != null,
      )
    }
  }
}
