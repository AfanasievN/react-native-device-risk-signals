package com.reactnativedeviceintel

import android.os.Looper
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactApplicationContext
import io.github.afanasievn.devicerisksignals.TransactionObservationSession
import io.github.afanasievn.devicerisksignals.TransactionObservationSnapshot

/** Framework lifecycle and bounded UI dispatch only; Android collection belongs to the SDK. */
internal class TransactionSafetyObserver(private val context: ReactApplicationContext) : LifecycleEventListener {
  private val session = TransactionObservationSession(context)
  @Volatile private var disposed = false
  // RN reports an already-resumed host through the listener as well; do not attach before that.
  @Volatile private var paused = true

  init { context.addLifecycleEventListener(this) }

  fun attachAndSnapshot(): TransactionObservationSnapshot? {
    if (disposed || paused) return null
    val task = PendingObservationTask {
      if (disposed || paused) null else {
        context.currentActivity?.let { activity ->
          session.attach(activity)
          session.snapshot()
        }
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) task.run()
    else context.runOnUiQueueThread(task)
    return task.await(1000)
  }

  override fun onHostResume() { paused = false }

  override fun onHostPause() {
    paused = true
    onMain { session.detach() }
  }

  override fun onHostDestroy() {
    paused = true
    onMain { session.detach() }
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    context.removeLifecycleEventListener(this)
    // Cleanup must not be canceled if a caller stops waiting or its worker is interrupted.
    onMain { session.close() }
  }

  private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block()
    else context.runOnUiQueueThread { block() }
  }
}
