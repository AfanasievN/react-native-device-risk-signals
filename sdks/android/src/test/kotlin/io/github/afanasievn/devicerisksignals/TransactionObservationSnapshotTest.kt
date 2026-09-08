package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionObservationSnapshotTest {
  @Test
  fun emptyObservationOmitsUnavailableFieldsRatherThanInventingNegatives() {
    assertEquals(
      mapOf("transactionObservationStartedElapsedMs" to 100.0, "observedTouchCount" to 0),
      TransactionObservationState(100L).snapshot().toRawMap(),
    )
  }

  @Test
  fun populatedObservationPreservesAllElevenLegacyKeysAndNumericTypes() {
    val state = TransactionObservationState(100L)
    state.recordTouch(true, true, 110L)
    state.setScreenshotObservationActive(true)
    state.recordScreenshot(120L)
    state.setScreenRecordingVisibility(false)
    assertEquals(
      mapOf(
        "transactionObservationStartedElapsedMs" to 100.0,
        "observedTouchCount" to 1,
        "obscuredTouchObserved" to true,
        "partiallyObscuredTouchObserved" to true,
        "lastObscuredTouchElapsedMs" to 110.0,
        "lastPartiallyObscuredTouchElapsedMs" to 110.0,
        "screenshotObservationActive" to true,
        "screenshotDetectedSinceObservationStart" to true,
        "lastScreenshotDetectedElapsedMs" to 120.0,
        "isVisibleInScreenRecording" to false,
        "isScreenCaptured" to false,
      ),
      state.snapshot().toRawMap(),
    )
  }
}
