package com.reactnativedeviceintel

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import com.facebook.react.bridge.ReactApplicationContext
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lazily observes transaction-time UI events after `transaction_safety` is first collected. It
 * never requests permissions: Android 14/15 capture callbacks are enabled only when the host app
 * explicitly declared the corresponding install-time permission.
 */
internal class TransactionSafetyObserver(private val context: ReactApplicationContext) {
  private var state: TransactionObservationState? = null
  private var attachedActivity = WeakReference<Activity>(null)
  private var originalWindowCallback = WeakReference<Window.Callback>(null)
  private var observingWindowCallback = WeakReference<ObservingWindowCallback>(null)
  private var screenshotRegistration: ObservationRegistration? = null
  private var screenRecordingRegistration: ObservationRegistration? = null

  fun attachAndSnapshot(): TransactionObservationSnapshot? = onMainThread {
    val activity = context.currentActivity ?: return@onMainThread null
    val observationState = state ?: TransactionObservationState(SystemClock.elapsedRealtime()).also {
      state = it
    }
    attachToActivity(activity, observationState)
    attachScreenRecording(observationState)
    observationState.snapshot()
  }

  fun dispose() {
    onMainThread {
      detachFromActivity()
      screenRecordingRegistration?.close()
      screenRecordingRegistration = null
    }
  }

  private fun attachToActivity(activity: Activity, observationState: TransactionObservationState) {
    if (attachedActivity.get() === activity && activity.window.callback === observingWindowCallback.get()) return
    detachFromActivity()

    val original = activity.window.callback ?: return
    val observer = ObservingWindowCallback(original, observationState)
    activity.window.callback = observer
    attachedActivity = WeakReference(activity)
    originalWindowCallback = WeakReference(original)
    observingWindowCallback = WeakReference(observer)
    attachScreenshotCallback(activity, observationState)
  }

  private fun detachFromActivity() {
    val activity = attachedActivity.get()
    screenshotRegistration?.close()
    val observer = observingWindowCallback.get()
    if (activity != null && observer != null && activity.window.callback === observer) {
      originalWindowCallback.get()?.let { activity.window.callback = it }
    }
    screenshotRegistration = null
    observingWindowCallback = WeakReference(null)
    originalWindowCallback = WeakReference(null)
    attachedActivity = WeakReference(null)
  }

  private fun attachScreenshotCallback(activity: Activity, observationState: TransactionObservationState) {
    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
      context.checkSelfPermission(Manifest.permission.DETECT_SCREEN_CAPTURE) != PackageManager.PERMISSION_GRANTED
    ) return

    val registration = safe { ScreenshotRegistration(activity, context, observationState) }
    if (registration != null) {
      screenshotRegistration = registration
      observationState.markScreenshotObservationActive()
    }
  }

  private fun attachScreenRecording(observationState: TransactionObservationState) {
    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
      screenRecordingRegistration != null ||
      context.checkSelfPermission(Manifest.permission.DETECT_SCREEN_RECORDING) != PackageManager.PERMISSION_GRANTED
    ) return

    screenRecordingRegistration = safe { ScreenRecordingRegistration(context, observationState) }
  }

  private class ObservingWindowCallback(
    private val delegate: Window.Callback,
    private val state: TransactionObservationState,
  ) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
      if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        state.recordTouch(
          isObscured = event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0,
          isPartiallyObscured =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
              event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0,
          elapsedMs = SystemClock.elapsedRealtime(),
        )
      }
      return delegate.dispatchTouchEvent(event)
    }
  }

  private fun <T> onMainThread(block: () -> T): T? {
    if (Looper.myLooper() == Looper.getMainLooper()) return safe(block)
    var result: T? = null
    val latch = CountDownLatch(1)
    context.runOnUiQueueThread {
      result = safe(block)
      latch.countDown()
    }
    return if (latch.await(1, TimeUnit.SECONDS)) result else null
  }

  private inline fun <T> safe(block: () -> T): T? = try { block() } catch (e: Throwable) { null }
}

private fun interface ObservationRegistration {
  fun close()
}

// Keep new platform types out of TransactionSafetyObserver's field signatures so loading the module
// remains safe on the minSdk 24 floor.
@TargetApi(34)
@SuppressLint("MissingPermission") // Caller verifies the host-declared install-time permission.
private class ScreenshotRegistration(
  activity: Activity,
  context: ReactApplicationContext,
  state: TransactionObservationState,
) : ObservationRegistration {
  private val activityReference = WeakReference(activity)
  private val callback = Activity.ScreenCaptureCallback {
    state.recordScreenshot(SystemClock.elapsedRealtime())
  }

  init {
    activity.registerScreenCaptureCallback(context.mainExecutor, callback)
  }

  override fun close() {
    try { activityReference.get()?.unregisterScreenCaptureCallback(callback) } catch (e: Throwable) { /* best effort */ }
  }
}

@TargetApi(35)
@SuppressLint("MissingPermission") // Caller verifies the host-declared install-time permission.
private class ScreenRecordingRegistration(
  context: ReactApplicationContext,
  state: TransactionObservationState,
) : ObservationRegistration {
  private val manager = context.getSystemService(WindowManager::class.java)
    ?: throw IllegalStateException("WindowManager unavailable")
  private val callback = java.util.function.Consumer<Int> { value ->
    state.setScreenRecordingVisibility(value == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
  }

  init {
    callback.accept(manager.addScreenRecordingCallback(context.mainExecutor, callback))
  }

  override fun close() {
    try { manager.removeScreenRecordingCallback(callback) } catch (e: Throwable) { /* best effort */ }
  }
}
