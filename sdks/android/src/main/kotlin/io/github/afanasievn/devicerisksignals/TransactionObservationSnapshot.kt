package io.github.afanasievn.devicerisksignals

/**
 * Immutable raw observations from one explicitly started transaction session.
 * Elapsed timestamps use the device monotonic clock, not wall-clock time.
 * Historical positives remain available after observation detaches; current recording
 * visibility and unobserved screenshot negatives require active callback coverage.
 */
data class TransactionObservationSnapshot(
  val startedElapsedMs: Long,
  val observedTouchCount: Int,
  val obscuredTouchObserved: Boolean?,
  val partiallyObscuredTouchObserved: Boolean?,
  val lastObscuredTouchElapsedMs: Long?,
  val lastPartiallyObscuredTouchElapsedMs: Long?,
  val screenshotObservationActive: Boolean,
  val screenshotDetectedSinceObservationStart: Boolean?,
  val lastScreenshotDetectedElapsedMs: Long?,
  val isVisibleInScreenRecording: Boolean?,
) {
  /** Existing wire names and numeric representations; unavailable fields are omitted. */
  fun toRawMap(): Map<String, Any> = buildMap {
    put("transactionObservationStartedElapsedMs", startedElapsedMs.toDouble())
    put("observedTouchCount", observedTouchCount)
    obscuredTouchObserved?.let { put("obscuredTouchObserved", it) }
    partiallyObscuredTouchObserved?.let { put("partiallyObscuredTouchObserved", it) }
    lastObscuredTouchElapsedMs?.let { put("lastObscuredTouchElapsedMs", it.toDouble()) }
    lastPartiallyObscuredTouchElapsedMs?.let { put("lastPartiallyObscuredTouchElapsedMs", it.toDouble()) }
    if (screenshotObservationActive) put("screenshotObservationActive", true)
    screenshotDetectedSinceObservationStart?.let { put("screenshotDetectedSinceObservationStart", it) }
    lastScreenshotDetectedElapsedMs?.let { put("lastScreenshotDetectedElapsedMs", it.toDouble()) }
    isVisibleInScreenRecording?.let {
      put("isVisibleInScreenRecording", it)
      put("isScreenCaptured", it)
    }
  }
}
