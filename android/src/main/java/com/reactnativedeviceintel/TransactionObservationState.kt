package com.reactnativedeviceintel

internal data class TransactionObservationSnapshot(
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
)

internal class TransactionObservationState(private val startedElapsedMs: Long) {
  private var observedTouchCount = 0
  private var obscuredTouchObserved = false
  private var partiallyObscuredTouchObserved = false
  private var lastObscuredTouchElapsedMs: Long? = null
  private var lastPartiallyObscuredTouchElapsedMs: Long? = null
  private var screenshotObservationActive = false
  private var screenshotDetected = false
  private var lastScreenshotDetectedElapsedMs: Long? = null
  private var isVisibleInScreenRecording: Boolean? = null

  @Synchronized
  fun recordTouch(isObscured: Boolean, isPartiallyObscured: Boolean, elapsedMs: Long) {
    observedTouchCount += 1
    if (isObscured) {
      obscuredTouchObserved = true
      lastObscuredTouchElapsedMs = elapsedMs
    }
    if (isPartiallyObscured) {
      partiallyObscuredTouchObserved = true
      lastPartiallyObscuredTouchElapsedMs = elapsedMs
    }
  }

  @Synchronized
  fun markScreenshotObservationActive() {
    screenshotObservationActive = true
  }

  @Synchronized
  fun recordScreenshot(elapsedMs: Long) {
    if (!screenshotObservationActive) return
    screenshotDetected = true
    lastScreenshotDetectedElapsedMs = elapsedMs
  }

  @Synchronized
  fun setScreenRecordingVisibility(isVisible: Boolean) {
    isVisibleInScreenRecording = isVisible
  }

  @Synchronized
  fun snapshot() = TransactionObservationSnapshot(
    startedElapsedMs = startedElapsedMs,
    observedTouchCount = observedTouchCount,
    obscuredTouchObserved = if (observedTouchCount > 0) obscuredTouchObserved else null,
    partiallyObscuredTouchObserved = if (observedTouchCount > 0) partiallyObscuredTouchObserved else null,
    lastObscuredTouchElapsedMs = lastObscuredTouchElapsedMs,
    lastPartiallyObscuredTouchElapsedMs = lastPartiallyObscuredTouchElapsedMs,
    screenshotObservationActive = screenshotObservationActive,
    screenshotDetectedSinceObservationStart = if (screenshotObservationActive) screenshotDetected else null,
    lastScreenshotDetectedElapsedMs = lastScreenshotDetectedElapsedMs,
    isVisibleInScreenRecording = isVisibleInScreenRecording,
  )
}
