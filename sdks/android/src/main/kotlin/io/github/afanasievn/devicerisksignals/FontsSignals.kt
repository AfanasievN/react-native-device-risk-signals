package io.github.afanasievn.devicerisksignals

/** SHA-256 of the existing sorted font-file-name observation, collected separately from hardware. */
data class FontsSignals(val fontsDigest: String? = null) {
  fun toRawMap(): Map<String, Any> = buildMap {
    fontsDigest?.let { put("fontsDigest", it) }
  }
}
