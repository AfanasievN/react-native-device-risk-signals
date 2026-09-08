package io.github.afanasievn.devicerisksignals

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * Explicit, initially inactive observation of the host's UI. Call [attach], [detach] and [close]
 * on the main thread. No permissions are requested and no background dispatch is performed.
 * Detach on host pause/destroy; a new session is required to reset cumulative event history.
 */
class TransactionObservationSession(context: Context) : AutoCloseable {
  private val context = context.applicationContext
  private val lifecycle = TransactionSessionLifecycle()
  @Volatile private var state: TransactionObservationState? = null
  private var attachedActivity = WeakReference<Activity>(null)
  private var originalCallback = WeakReference<Window.Callback>(null)
  private var observingCallback = WeakReference<Window.Callback>(null)
  private var screenshotRegistration: TransactionRegistration? = null
  private var recordingRegistration: TransactionRegistration? = null

  /** Begins (or resumes) observation; repeated attachment to the same installed wrapper is a no-op. */
  fun attach(activity: Activity) {
    requireMainThread()
    check(!lifecycle.isClosed) { "Transaction observation session is closed" }
    if (attachedActivity.get() === activity && observingCallback.get() != null &&
      activity.window.callback === observingCallback.get()) return
    detach()
    val observationState = state ?: TransactionObservationState(SystemClock.elapsedRealtime()).also {
      state = it
    }
    val token = lifecycle.attach()
    attachedActivity = WeakReference(activity)
    activity.window.callback?.let { original ->
      val observer = ObservingCallback(original, observationState, lifecycle, token)
      activity.window.callback = observer
      originalCallback = WeakReference(original)
      observingCallback = WeakReference(observer)
    }
    if (Build.VERSION.SDK_INT >= 34 && hasPermission(Manifest.permission.DETECT_SCREEN_CAPTURE)) {
      screenshotRegistration = safe {
        TransactionScreenshotRegistration(activity, context, observationState, lifecycle, token)
      }
      observationState.setScreenshotObservationActive(screenshotRegistration != null)
    }
    if (Build.VERSION.SDK_INT >= 35 && hasPermission(Manifest.permission.DETECT_SCREEN_RECORDING)) {
      recordingRegistration = safe {
        TransactionRecordingRegistration(context, observationState, lifecycle, token)
      }
    }
  }

  /** Returns null before first attachment; reading a snapshot never activates observation. */
  fun snapshot(): TransactionObservationSnapshot? = state?.snapshot()

  /** Stops all registrations and clears current coverage, retaining historical observations. */
  fun detach() {
    requireMainThread()
    lifecycle.detach()
    screenshotRegistration?.let { safe { it.close() } }
    recordingRegistration?.let { safe { it.close() } }
    screenshotRegistration = null
    recordingRegistration = null
    state?.setScreenshotObservationActive(false)
    state?.setScreenRecordingVisibility(null)
    val activity = attachedActivity.get()
    val observer = observingCallback.get()
    safe {
      if (activity != null && observer != null && activity.window.callback === observer) {
        originalCallback.get()?.let { activity.window.callback = it }
      }
    }
    attachedActivity = WeakReference(null)
    originalCallback = WeakReference(null)
    observingCallback = WeakReference(null)
  }

  /** Terminal and idempotent. Subsequent [attach] calls fail; historical [snapshot] stays readable. */
  override fun close() {
    requireMainThread()
    lifecycle.close()
    detach()
  }

  private fun hasPermission(permission: String): Boolean = safe {
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
  } == true

  private fun requireMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) {
      "Transaction observation lifecycle must run on the main thread"
    }
  }

  private class ObservingCallback(
    private val delegate: Window.Callback,
    private val state: TransactionObservationState,
    private val lifecycle: TransactionSessionLifecycle,
    private val token: Long,
  ) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
      if (lifecycle.isActive(token) && event.actionMasked == MotionEvent.ACTION_DOWN) {
        state.recordTouch(
          isObscured = event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0,
          isPartiallyObscured = if (Build.VERSION.SDK_INT >= 29) {
            event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0
          } else null,
          elapsedMs = SystemClock.elapsedRealtime(),
        )
      }
      return delegate.dispatchTouchEvent(event)
    }
  }

  private inline fun <T> safe(block: () -> T): T? = try { block() } catch (_: Throwable) { null }
}

private fun interface TransactionRegistration {
  fun close()
}

// New platform callback types are isolated from the session's signatures for the minSdk 24 floor.
@TargetApi(34)
@SuppressLint("MissingPermission") // Host grant is checked by the caller; no permission prompt.
private class TransactionScreenshotRegistration(
  activity: Activity,
  context: Context,
  state: TransactionObservationState,
  lifecycle: TransactionSessionLifecycle,
  token: Long,
) : TransactionRegistration {
  private val activityReference = WeakReference(activity)
  private val callback = Activity.ScreenCaptureCallback {
    if (lifecycle.isActive(token)) state.recordScreenshot(SystemClock.elapsedRealtime())
  }

  init {
    activity.registerScreenCaptureCallback(context.mainExecutor, callback)
  }

  override fun close() {
    activityReference.get()?.unregisterScreenCaptureCallback(callback)
  }
}

@TargetApi(35)
@SuppressLint("MissingPermission") // Host grant is checked by the caller; no permission prompt.
private class TransactionRecordingRegistration(
  context: Context,
  state: TransactionObservationState,
  lifecycle: TransactionSessionLifecycle,
  token: Long,
) : TransactionRegistration {
  private val manager = context.getSystemService(WindowManager::class.java)
    ?: throw IllegalStateException("WindowManager unavailable")
  private val callback = java.util.function.Consumer<Int> { value ->
    if (lifecycle.isActive(token)) {
      state.setScreenRecordingVisibility(value == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
    }
  }

  init {
    callback.accept(manager.addScreenRecordingCallback(context.mainExecutor, callback))
  }

  override fun close() {
    manager.removeScreenRecordingCallback(callback)
  }
}
