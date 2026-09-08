package com.reactnativedeviceintel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Cancels queued work, not an Android callback already executing on the UI thread. */
internal class PendingObservationTask<T>(private val block: () -> T) : Runnable {
  private val claimed = AtomicBoolean(false)
  private val done = CountDownLatch(1)
  @Volatile private var result: T? = null

  override fun run() {
    if (!claimed.compareAndSet(false, true)) return
    try {
      result = try { block() } catch (_: Throwable) { null }
    } finally {
      done.countDown()
    }
  }

  fun cancel() {
    if (claimed.compareAndSet(false, true)) done.countDown()
  }

  fun await(timeoutMs: Long): T? {
    return try {
      if (done.await(timeoutMs, TimeUnit.MILLISECONDS)) result else {
        cancel()
        null
      }
    } catch (error: InterruptedException) {
      cancel()
      Thread.currentThread().interrupt()
      null
    }
  }
}
