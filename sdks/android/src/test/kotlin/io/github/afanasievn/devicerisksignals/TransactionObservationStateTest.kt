package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionObservationStateTest {
  @Test
  fun touchObservationsAreUnavailableUntilARealTouchWasObserved() {
    val state = TransactionObservationState(startedElapsedMs = 100L)

    val snapshot = state.snapshot()

    assertEquals(100L, snapshot.startedElapsedMs)
    assertEquals(0, snapshot.observedTouchCount)
    assertNull(snapshot.obscuredTouchObserved)
    assertNull(snapshot.partiallyObscuredTouchObserved)
  }

  @Test
  fun recordsCleanAndObscuredTouchesWithoutLosingEarlierEvidence() {
    val state = TransactionObservationState(startedElapsedMs = 100L)

    state.recordTouch(isObscured = false, isPartiallyObscured = false, elapsedMs = 110L)
    val clean = state.snapshot()
    assertFalse(clean.obscuredTouchObserved!!)
    assertFalse(clean.partiallyObscuredTouchObserved!!)

    state.recordTouch(isObscured = true, isPartiallyObscured = false, elapsedMs = 120L)
    state.recordTouch(isObscured = false, isPartiallyObscured = true, elapsedMs = 130L)
    val suspicious = state.snapshot()
    assertEquals(3, suspicious.observedTouchCount)
    assertTrue(suspicious.obscuredTouchObserved!!)
    assertTrue(suspicious.partiallyObscuredTouchObserved!!)
    assertEquals(120L, suspicious.lastObscuredTouchElapsedMs)
    assertEquals(130L, suspicious.lastPartiallyObscuredTouchElapsedMs)
  }

  @Test
  fun screenshotStateIsOnlyAvailableAfterCallbackRegistration() {
    val state = TransactionObservationState(startedElapsedMs = 100L)
    assertNull(state.snapshot().screenshotDetectedSinceObservationStart)

    state.markScreenshotObservationActive()
    assertFalse(state.snapshot().screenshotDetectedSinceObservationStart!!)

    state.recordScreenshot(elapsedMs = 140L)
    val snapshot = state.snapshot()
    assertTrue(snapshot.screenshotDetectedSinceObservationStart!!)
    assertEquals(140L, snapshot.lastScreenshotDetectedElapsedMs)
  }

  @Test
  fun unsupportedPartialObscurationIsOmittedAfterRealTouches() {
    val state = TransactionObservationState(100L)
    state.recordTouch(false, null, 110L)
    assertFalse(state.snapshot().obscuredTouchObserved!!)
    assertNull(state.snapshot().partiallyObscuredTouchObserved)
    state.recordTouch(false, false, 120L)
    assertFalse(state.snapshot().partiallyObscuredTouchObserved!!)
  }

  @Test
  fun screenshotDetachClearsNegativeCoverageAndIgnoresLateCallbacks() {
    val state = TransactionObservationState(100L)
    state.setScreenshotObservationActive(true)
    state.setScreenshotObservationActive(false)
    state.recordScreenshot(120L)
    assertFalse(state.snapshot().screenshotObservationActive)
    assertNull(state.snapshot().screenshotDetectedSinceObservationStart)
    assertNull(state.snapshot().lastScreenshotDetectedElapsedMs)
  }

  @Test
  fun screenshotDetachRetainsEarlierPositiveObservation() {
    val state = TransactionObservationState(100L)
    state.setScreenshotObservationActive(true)
    state.recordScreenshot(110L)
    state.setScreenshotObservationActive(false)
    assertTrue(state.snapshot().screenshotDetectedSinceObservationStart!!)
    assertEquals(110L, state.snapshot().lastScreenshotDetectedElapsedMs)
    assertFalse(state.snapshot().toRawMap().containsKey("screenshotObservationActive"))
  }

  @Test
  fun recordingDetachClearsBothCurrentVisibilityAliases() {
    val state = TransactionObservationState(100L)
    state.setScreenRecordingVisibility(true)
    state.setScreenRecordingVisibility(null)
    assertNull(state.snapshot().isVisibleInScreenRecording)
    assertFalse(state.snapshot().toRawMap().containsKey("isScreenCaptured"))
    assertFalse(state.snapshot().toRawMap().containsKey("isVisibleInScreenRecording"))
  }

  @Test
  fun snapshotIsImmutableAfterLaterEvents() {
    val state = TransactionObservationState(100L)
    val before = state.snapshot()
    state.recordTouch(true, true, 120L)
    assertEquals(0, before.observedTouchCount)
    assertNull(before.obscuredTouchObserved)
  }
}
