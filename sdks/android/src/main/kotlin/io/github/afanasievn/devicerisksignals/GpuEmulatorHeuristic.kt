package io.github.afanasievn.devicerisksignals

import android.os.Build

/**
 * Build-string emulator heuristic for the GPU workload. A positive match only means draw-call
 * timing would be meaningless as an observation; it is not evidence about the runtime environment.
 */
internal object GpuEmulatorHeuristic {
  fun isLikelyEmulator(): Boolean = isLikelyEmulator(Build.FINGERPRINT, Build.MODEL, Build.HARDWARE)

  fun isLikelyEmulator(fingerprint: String?, model: String?, hardware: String?): Boolean {
    val fp = (fingerprint ?: "").lowercase()
    val deviceModel = (model ?: "").lowercase()
    val deviceHardware = (hardware ?: "").lowercase()
    return fp.contains("generic") || fp.contains("emulator") || fp.contains("sdk") ||
      deviceModel.contains("emulator") || deviceModel.contains("android sdk") ||
      deviceHardware.contains("goldfish") || deviceHardware.contains("ranchu") ||
      deviceHardware.contains("vbox")
  }
}
