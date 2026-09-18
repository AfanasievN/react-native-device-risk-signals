package io.github.afanasievn.devicerisksignals

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android-framework coverage for [TransactionObservationSession] that the JVM suites cannot provide:
 * a real main [android.os.Looper], real Activities with a real `PhoneWindow` callback chain, real
 * `MotionEvent`s, and the real API 34/35 capture-detection registrations.
 *
 * What is asserted here is ownership and lifecycle behaviour, never timing and never a verdict:
 * which callback object sits in the window after each transition, which events are counted, which
 * fields the snapshot reports, and that concurrent readers observe a consistent snapshot while the
 * main thread attaches, detaches and closes.
 *
 * Touches are dispatched through the window callback that is actually installed in the Activity, so
 * every test walks the same chain the platform walks, including any third-party wrapper the test
 * installed around ours. Input injection through the input system is deliberately not used: it
 * depends on window focus and would make these assertions flaky without testing anything more.
 */
@RunWith(AndroidJUnit4::class)
class TransactionObservationSessionInstrumentedTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val openedSessions = mutableListOf<TransactionObservationSession>()

  @After fun closeOpenedSessions() {
    openedSessions.forEach { session -> runCatching { onMain { session.close() } } }
    openedSessions.clear()
  }

  // --- no activity ----------------------------------------------------------------------------

  @Test fun sessionWithoutAnyActivityObservesNothingAndStillDetachesAndCloses() {
    val session = newSession()

    assertNull("a session that never attached must not expose a snapshot", session.snapshot())
    onMain { session.detach() }
    assertNull("detach without an activity must not create observation state", session.snapshot())
    onMain { session.close() }
    onMain { session.close() }
    assertNull("close without an activity must not create observation state", session.snapshot())
  }

  @Test fun lifecycleCallsFromABackgroundThreadAreRejectedOnTheRealLooper() {
    val session = newSession()
    withActivity { activity ->
      assertIsIllegalState("attach", offMain { session.attach(activity) })
      assertIsIllegalState("detach", offMain { session.detach() })
      assertIsIllegalState("close", offMain { session.close() })
      assertNull("a rejected attach must not start observation", session.snapshot())

      onMain { session.attach(activity) }
      assertNotNull("the session must still be usable from the main thread", session.snapshot())
    }
  }

  @Test fun attachAfterCloseIsRejectedWhileHistoryStaysReadable() {
    val session = newSession()
    withActivity { activity ->
      onMain { session.attach(activity) }
      dispatchTouchDown(activity)
      onMain { session.close() }

      val closed = session.snapshot()
      assertNotNull("history must survive close", closed)
      assertEquals(1, closed!!.observedTouchCount)

      assertIsIllegalState("attach after close", catchOnMain { session.attach(activity) })
      dispatchTouchDown(activity)
      assertEquals(
        "a closed session must not count further touches",
        1,
        session.snapshot()!!.observedTouchCount,
      )
    }
  }

  // --- repeated attach ------------------------------------------------------------------------

  @Test fun repeatedAttachToTheSameActivityKeepsOneWrapperAndCountsEachTouchOnce() {
    val session = newSession()
    withActivity { activity ->
      val original = onMain { activity.window.callback }
      onMain { session.attach(activity) }
      val installed = onMain { activity.window.callback }
      assertTrue("attach must install its own callback", installed !== original)

      onMain { session.attach(activity) }
      onMain { session.attach(activity) }
      assertSame(
        "repeated attach must not wrap the window callback again",
        installed,
        onMain { activity.window.callback },
      )

      dispatchTouchDown(activity)
      assertEquals(
        "a single touch must be counted once after repeated attach",
        1,
        session.snapshot()!!.observedTouchCount,
      )

      onMain { session.detach() }
      assertSame(
        "detach must restore exactly the callback that was installed before attach",
        original,
        onMain { activity.window.callback },
      )
    }
  }

  @Test fun detachAndReattachKeepsTheOriginalHistoryAndStartTimestamp() {
    val session = newSession()
    withActivity { activity ->
      onMain { session.attach(activity) }
      dispatchTouchDown(activity)
      val first = session.snapshot()!!

      onMain { session.detach() }
      dispatchTouchDown(activity)
      assertEquals(
        "touches after detach must not be counted",
        1,
        session.snapshot()!!.observedTouchCount,
      )

      onMain { session.attach(activity) }
      dispatchTouchDown(activity)
      val second = session.snapshot()!!
      assertEquals("reattach must keep the original history", 2, second.observedTouchCount)
      assertEquals(
        "reattach must not restart the session clock",
        first.startedElapsedMs,
        second.startedElapsedMs,
      )
    }
  }

  // --- activity switch and destroy --------------------------------------------------------------

  @Test fun switchingActivitiesReleasesTheFirstWindowAndKeepsCumulativeHistory() {
    val session = newSession()
    withActivity { first ->
      val firstOriginal = onMain { first.window.callback }
      onMain { session.attach(first) }
      dispatchTouchDown(first)

      withActivity(SecondTransactionTestActivity::class.java) { second ->
        onMain { session.attach(second) }

        assertSame(
          "attaching elsewhere must restore the previous activity's window callback",
          firstOriginal,
          onMain { first.window.callback },
        )
        dispatchTouchDown(first)
        assertEquals(
          "the released activity must no longer feed the session",
          1,
          session.snapshot()!!.observedTouchCount,
        )

        dispatchTouchDown(second)
        val switched = session.snapshot()!!
        assertEquals("the new activity must feed the same history", 2, switched.observedTouchCount)
      }
    }
  }

  @Test fun activityDestroyedMidSessionLeavesTheSessionUsableForANewActivity() {
    val session = newSession()
    val scenario = ActivityScenario.launch(TransactionTestActivity::class.java)
    val destroyed = AtomicReference<Activity>()
    scenario.onActivity { destroyed.set(it) }
    onMain { session.attach(destroyed.get()) }
    dispatchTouchDown(destroyed.get())
    val beforeDestroy = session.snapshot()!!

    scenario.close()

    onMain { session.detach() }
    val afterDestroy = session.snapshot()!!
    assertEquals(
      "history must survive the destruction of the observed activity",
      beforeDestroy.observedTouchCount,
      afterDestroy.observedTouchCount,
    )
    assertEquals(beforeDestroy.startedElapsedMs, afterDestroy.startedElapsedMs)
    assertFalse(
      "coverage must not be reported after detach",
      afterDestroy.screenshotObservationActive,
    )

    withActivity(SecondTransactionTestActivity::class.java) { replacement ->
      onMain { session.attach(replacement) }
      dispatchTouchDown(replacement)
      assertEquals(
        "a new activity must resume the same history",
        beforeDestroy.observedTouchCount + 1,
        session.snapshot()!!.observedTouchCount,
      )
    }
  }

  @Test fun attachToAnAlreadyDestroyedActivityNeverThrowsAndLeavesNoStaleCoverage() {
    val session = newSession()
    val scenario = ActivityScenario.launch(TransactionTestActivity::class.java)
    val destroyed = AtomicReference<Activity>()
    scenario.onActivity { destroyed.set(it) }
    scenario.close()

    onMain { session.attach(destroyed.get()) }
    val snapshot = session.snapshot()
    assertNotNull("attach must have started observation state", snapshot)
    assertEquals(0, snapshot!!.observedTouchCount)

    // Observed on API 35: neither registerScreenCaptureCallback nor addScreenRecordingCallback
    // rejects a destroyed Activity, so the session reports coverage it cannot really have. The
    // host, not the SDK, owns the activity lifecycle - this pins what the platform does today so
    // that a change in either direction is visible instead of silent.
    if (Build.VERSION.SDK_INT >= 34 && isGranted(Manifest.permission.DETECT_SCREEN_CAPTURE)) {
      assertTrue(
        "screenshot registration on a destroyed activity changed behaviour",
        snapshot.screenshotObservationActive,
      )
    }
    if (Build.VERSION.SDK_INT >= 35 && isGranted(Manifest.permission.DETECT_SCREEN_RECORDING)) {
      assertEquals(
        "recording registration on a destroyed activity changed behaviour",
        false,
        snapshot.isVisibleInScreenRecording,
      )
    }

    onMain { session.detach() }
    assertFalse(
      "detach must drop coverage even when the activity is gone",
      session.snapshot()!!.screenshotObservationActive,
    )
    assertNull(session.snapshot()!!.isVisibleInScreenRecording)
  }

  // --- third-party window callback wrappers -----------------------------------------------------

  @Test fun wrapperInstalledBeforeAttachIsRestoredExactlyAndKeepsReceivingTouches() {
    val session = newSession()
    withActivity { activity ->
      val platform = onMain { activity.window.callback }
      val thirdParty = CountingCallback(platform)
      onMain { activity.window.callback = thirdParty }

      onMain { session.attach(activity) }
      dispatchTouchDown(activity)

      assertEquals("our wrapper must forward to the third-party wrapper", 1, thirdParty.count.get())
      assertEquals(1, session.snapshot()!!.observedTouchCount)

      onMain { session.detach() }
      assertSame(
        "detach must restore the third-party wrapper, not the platform callback",
        thirdParty,
        onMain { activity.window.callback },
      )
      onMain { activity.window.callback = platform }
    }
  }

  @Test fun wrapperInstalledAroundOursAfterAttachStopsCountingOnDetachButKeepsForwarding() {
    val session = newSession()
    withActivity { activity ->
      val platform = onMain { activity.window.callback }
      val inner = CountingCallback(platform)
      onMain { activity.window.callback = inner }
      onMain { session.attach(activity) }

      // A third party wraps our callback after we installed it, so the window no longer holds ours.
      val outer = CountingCallback(onMain { activity.window.callback })
      onMain { activity.window.callback = outer }

      dispatchTouchDown(activity)
      assertEquals(1, outer.count.get())
      assertEquals("the nested chain must reach the inner wrapper", 1, inner.count.get())
      assertEquals("the nested chain must reach the session", 1, session.snapshot()!!.observedTouchCount)

      onMain { session.detach() }
      assertSame(
        "detach must not rip a foreign callback out of the window",
        outer,
        onMain { activity.window.callback },
      )

      dispatchTouchDown(activity)
      assertEquals("the foreign chain must keep working after detach", 2, outer.count.get())
      assertEquals("the foreign chain must keep forwarding after detach", 2, inner.count.get())
      assertEquals(
        "a retained callback must stop feeding the session after detach",
        1,
        session.snapshot()!!.observedTouchCount,
      )

      onMain { activity.window.callback = platform }
    }
  }

  @Test fun aRetainedCallbackFromAPriorAttachNeverFeedsTheNextAttachment() {
    val session = newSession()
    withActivity { activity ->
      val platform = onMain { activity.window.callback }
      onMain { session.attach(activity) }
      // A third party captured our first-generation callback and keeps calling it.
      val stale = onMain { activity.window.callback }
      onMain { session.detach() }
      onMain { session.attach(activity) }

      dispatchTouchDown(activity, through = stale)
      assertEquals(
        "a stale generation must not contribute to the current attachment",
        0,
        session.snapshot()!!.observedTouchCount,
      )

      dispatchTouchDown(activity)
      assertEquals(1, session.snapshot()!!.observedTouchCount)
      onMain { session.detach() }
      assertSame(platform, onMain { activity.window.callback })
    }
  }

  // --- permissions and API gates ---------------------------------------------------------------

  @Test fun captureRegistrationFollowsTheApiGatesAndTheGrantedPermissions() {
    val session = newSession()
    withActivity { activity ->
      onMain { session.attach(activity) }
      val attached = session.snapshot()!!

      if (Build.VERSION.SDK_INT >= 34) {
        assertTrue(
          "androidTest manifest regression: DETECT_SCREEN_CAPTURE is not granted",
          isGranted(Manifest.permission.DETECT_SCREEN_CAPTURE),
        )
        assertTrue(
          "API ${Build.VERSION.SDK_INT} with the permission granted must register screenshot detection",
          attached.screenshotObservationActive,
        )
        assertEquals(
          "coverage without a screenshot must report an observed negative",
          false,
          attached.screenshotDetectedSinceObservationStart,
        )
      } else {
        assertFalse(
          "API ${Build.VERSION.SDK_INT} predates screenshot detection",
          attached.screenshotObservationActive,
        )
        assertNull(attached.screenshotDetectedSinceObservationStart)
      }

      if (Build.VERSION.SDK_INT >= 35) {
        assertTrue(
          "androidTest manifest regression: DETECT_SCREEN_RECORDING is not granted",
          isGranted(Manifest.permission.DETECT_SCREEN_RECORDING),
        )
        assertEquals(
          "API ${Build.VERSION.SDK_INT} must report the current recording visibility",
          false,
          attached.isVisibleInScreenRecording,
        )
      } else {
        assertNull(
          "API ${Build.VERSION.SDK_INT} predates recording visibility",
          attached.isVisibleInScreenRecording,
        )
      }

      onMain { session.detach() }
      val detached = session.snapshot()!!
      assertFalse(
        "detach must drop screenshot coverage",
        detached.screenshotObservationActive,
      )
      assertNull(
        "an unobserved screenshot negative must be omitted without coverage",
        detached.screenshotDetectedSinceObservationStart,
      )
      assertNull(
        "detach must drop the recording visibility instead of freezing it",
        detached.isVisibleInScreenRecording,
      )
      assertFalse(
        "coverage fields must be omitted from the raw map after detach",
        detached.toRawMap().containsKey("isVisibleInScreenRecording"),
      )
    }
  }

  @Test fun partialObscurationFollowsTheApi29GateForRealMotionEvents() {
    val session = newSession()
    withActivity { activity ->
      onMain { session.attach(activity) }
      dispatchTouchDown(activity)

      val snapshot = session.snapshot()!!
      assertEquals(1, snapshot.observedTouchCount)
      assertEquals(
        "an unobscured real touch must report an observed negative",
        false,
        snapshot.obscuredTouchObserved,
      )
      if (Build.VERSION.SDK_INT >= 29) {
        assertEquals(
          "API ${Build.VERSION.SDK_INT} must report partial obscuration",
          false,
          snapshot.partiallyObscuredTouchObserved,
        )
      } else {
        assertNull(
          "API ${Build.VERSION.SDK_INT} must omit partial obscuration instead of synthesising false",
          snapshot.partiallyObscuredTouchObserved,
        )
      }
      assertNull(snapshot.lastObscuredTouchElapsedMs)
      assertNull(snapshot.lastPartiallyObscuredTouchElapsedMs)
    }
  }

  @Test fun onlyActionDownIsCountedFromARealGesture() {
    val session = newSession()
    withActivity { activity ->
      onMain { session.attach(activity) }
      dispatchGesture(activity)

      assertEquals(
        "a down/move/up gesture is one observed touch",
        1,
        session.snapshot()!!.observedTouchCount,
      )
    }
  }

  // --- collect / dispose race -------------------------------------------------------------------

  @Test fun concurrentSnapshotReadsDuringAttachDetachAndCloseStayConsistent() {
    val session = newSession()
    withActivity { activity ->
      onMain { session.attach(activity) }
      val start = session.snapshot()!!.startedElapsedMs

      val stop = AtomicBoolean(false)
      val ready = CountDownLatch(READER_THREADS)
      val failures = AtomicReference<Throwable>()
      val reads = AtomicInteger()
      val readers = List(READER_THREADS) { index ->
        Thread({
          ready.countDown()
          var lastCount = 0
          try {
            while (!stop.get()) {
              val snapshot = session.snapshot()
                ?: throw AssertionError("snapshot became unavailable after the first attach")
              assertEquals("the session clock must never be rewritten", start, snapshot.startedElapsedMs)
              if (snapshot.observedTouchCount < lastCount) {
                throw AssertionError(
                  "observed touch count went backwards: $lastCount -> ${snapshot.observedTouchCount}",
                )
              }
              lastCount = snapshot.observedTouchCount
              snapshot.toRawMap()
              reads.incrementAndGet()
            }
          } catch (t: Throwable) {
            failures.compareAndSet(null, t)
          }
        }, "transaction-snapshot-reader-$index")
      }
      readers.forEach { it.start() }
      ready.await()

      repeat(LIFECYCLE_CYCLES) {
        onMain { session.attach(activity) }
        dispatchTouchDown(activity)
        onMain { session.detach() }
        dispatchTouchDown(activity)
      }
      onMain { session.attach(activity) }
      onMain { session.close() }

      stop.set(true)
      readers.forEach { it.join(READER_JOIN_TIMEOUT_MS) }
      readers.forEach { assertFalse("reader ${it.name} did not finish", it.isAlive) }
      failures.get()?.let { throw AssertionError("concurrent reader failed: $it", it) }
      assertTrue("the readers never observed a snapshot", reads.get() > 0)

      val finalSnapshot = session.snapshot()!!
      assertEquals(
        "only touches dispatched while attached may be counted",
        LIFECYCLE_CYCLES,
        finalSnapshot.observedTouchCount,
      )
      assertEquals(start, finalSnapshot.startedElapsedMs)
      assertFalse("close must drop coverage", finalSnapshot.screenshotObservationActive)
    }
  }

  // --- helpers ----------------------------------------------------------------------------------

  private class CountingCallback(private val delegate: Window.Callback) :
    Window.Callback by delegate {
    val count = AtomicInteger()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
      count.incrementAndGet()
      return delegate.dispatchTouchEvent(event)
    }
  }

  private fun newSession(): TransactionObservationSession =
    DeviceRiskSignals(instrumentation.targetContext).createTransactionObservationSession()
      .also { openedSessions.add(it) }

  private fun withActivity(block: (Activity) -> Unit) =
    withActivity(TransactionTestActivity::class.java, block)

  private fun <T : Activity> withActivity(clazz: Class<T>, block: (Activity) -> Unit) {
    ActivityScenario.launch(clazz).use { scenario ->
      val activity = AtomicReference<Activity>()
      scenario.onActivity { activity.set(it) }
      val resumed = activity.get()
      assertNotNull("the scenario did not deliver an activity for ${clazz.simpleName}", resumed)
      block(resumed)
    }
  }

  private fun isGranted(permission: String): Boolean =
    instrumentation.targetContext.checkSelfPermission(permission) ==
      PackageManager.PERMISSION_GRANTED

  private fun dispatchTouchDown(activity: Activity, through: Window.Callback? = null) {
    onMain {
      val now = SystemClock.uptimeMillis()
      val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, TOUCH_X, TOUCH_Y, 0)
      try {
        (through ?: activity.window.callback).dispatchTouchEvent(event)
      } finally {
        event.recycle()
      }
    }
  }

  private fun dispatchGesture(activity: Activity) {
    onMain {
      val down = SystemClock.uptimeMillis()
      val callback = activity.window.callback
      listOf(
        MotionEvent.ACTION_DOWN to 0L,
        MotionEvent.ACTION_MOVE to 10L,
        MotionEvent.ACTION_UP to 20L,
      ).forEach { (action, offset) ->
        val event = MotionEvent.obtain(down, down + offset, action, TOUCH_X, TOUCH_Y + offset, 0)
        try {
          callback.dispatchTouchEvent(event)
        } finally {
          event.recycle()
        }
      }
    }
  }

  private fun <T> onMain(block: () -> T): T {
    val outcome = catchOnMain(block)
    outcome.failure?.let { throw AssertionError("main-thread call failed: $it", it) }
    return outcome.result()
  }

  private class MainThreadOutcome<T>(private val value: T?, val failure: Throwable?) {
    @Suppress("UNCHECKED_CAST")
    fun result(): T = value as T
  }

  private fun <T> catchOnMain(block: () -> T): MainThreadOutcome<T> {
    val result = AtomicReference<T>()
    val failure = AtomicReference<Throwable>()
    // Caught inside the runnable: a throwable escaping runOnMainSync breaks the main looper for
    // every later test instead of failing this one.
    instrumentation.runOnMainSync {
      try {
        result.set(block())
      } catch (t: Throwable) {
        failure.set(t)
      }
    }
    return MainThreadOutcome(result.get(), failure.get())
  }

  private fun offMain(block: () -> Unit): Throwable? {
    val failure = AtomicReference<Throwable>()
    val thread = Thread({
      try {
        block()
      } catch (t: Throwable) {
        failure.set(t)
      }
    }, "transaction-off-main")
    thread.start()
    thread.join(READER_JOIN_TIMEOUT_MS)
    assertFalse("the background lifecycle call never returned", thread.isAlive)
    return failure.get()
  }

  private fun assertIsIllegalState(what: String, failure: Throwable?) {
    if (failure == null) fail("$what off the main thread must be rejected")
    assertTrue(
      "$what must fail with IllegalStateException, got $failure",
      failure is IllegalStateException,
    )
  }

  private fun assertIsIllegalState(what: String, outcome: MainThreadOutcome<*>) =
    assertIsIllegalState(what, outcome.failure)

  private companion object {
    const val TOUCH_X = 10f
    const val TOUCH_Y = 10f
    const val READER_THREADS = 3
    const val LIFECYCLE_CYCLES = 40
    const val READER_JOIN_TIMEOUT_MS = 5_000L
  }
}
