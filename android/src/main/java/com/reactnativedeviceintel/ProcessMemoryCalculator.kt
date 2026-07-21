package com.reactnativedeviceintel

/** Converts Linux /proc/self/statm resident pages to bytes without inventing unavailable values. */
internal object ProcessMemoryCalculator {
  fun residentBytes(statm: String, pageSizeBytes: Long): Long? {
    if (pageSizeBytes <= 0) return null
    val residentPages = statm.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: return null
    if (residentPages < 0) return null
    return try {
      Math.multiplyExact(residentPages, pageSizeBytes)
    } catch (e: ArithmeticException) {
      null
    }
  }
}
