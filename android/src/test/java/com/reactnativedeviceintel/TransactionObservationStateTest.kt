package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionObservationStateTest {
  @Test
  fun touchVerdictsAreUnavailableUntilARealTouchWasObserved() {
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
}
