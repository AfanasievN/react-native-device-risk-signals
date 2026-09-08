package io.github.afanasievn.devicerisksignals

internal class TransactionObservationState(private val startedElapsedMs: Long) {
  private var observedTouchCount = 0
  private var obscuredTouchObserved = false
  private var partiallyObscuredTouchObserved: Boolean? = null
  private var lastObscuredTouchElapsedMs: Long? = null
  private var lastPartiallyObscuredTouchElapsedMs: Long? = null
  private var screenshotObservationActive = false
  private var screenshotDetected = false
  private var lastScreenshotDetectedElapsedMs: Long? = null
  private var isVisibleInScreenRecording: Boolean? = null

  @Synchronized
  fun recordTouch(isObscured: Boolean, isPartiallyObscured: Boolean?, elapsedMs: Long) {
    observedTouchCount += 1
    if (isObscured) {
      obscuredTouchObserved = true
      lastObscuredTouchElapsedMs = elapsedMs
    }
    if (isPartiallyObscured == true) {
      partiallyObscuredTouchObserved = true
      lastPartiallyObscuredTouchElapsedMs = elapsedMs
    } else if (isPartiallyObscured == false && partiallyObscuredTouchObserved == null) {
      partiallyObscuredTouchObserved = false
    }
  }

  @Synchronized
  fun markScreenshotObservationActive() {
    setScreenshotObservationActive(true)
  }

  @Synchronized
  fun setScreenshotObservationActive(active: Boolean) {
    screenshotObservationActive = active
  }

  @Synchronized
  fun recordScreenshot(elapsedMs: Long) {
    if (!screenshotObservationActive) return
    screenshotDetected = true
    lastScreenshotDetectedElapsedMs = elapsedMs
  }

  @Synchronized
  fun setScreenRecordingVisibility(isVisible: Boolean?) {
    isVisibleInScreenRecording = isVisible
  }

  @Synchronized
  fun snapshot() = TransactionObservationSnapshot(
    startedElapsedMs = startedElapsedMs,
    observedTouchCount = observedTouchCount,
    obscuredTouchObserved = if (observedTouchCount > 0) obscuredTouchObserved else null,
    partiallyObscuredTouchObserved = partiallyObscuredTouchObserved,
    lastObscuredTouchElapsedMs = lastObscuredTouchElapsedMs,
    lastPartiallyObscuredTouchElapsedMs = lastPartiallyObscuredTouchElapsedMs,
    screenshotObservationActive = screenshotObservationActive,
    screenshotDetectedSinceObservationStart = if (screenshotDetected || screenshotObservationActive) screenshotDetected else null,
    lastScreenshotDetectedElapsedMs = lastScreenshotDetectedElapsedMs,
    isVisibleInScreenRecording = isVisibleInScreenRecording,
  )
}
