package io.github.afanasievn.devicerisksignals

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-driver counterpart to `GpuResourceManagementTest`, which only exercises the cleanup ordering
 * with a fake driver. These tests run the production EGL path against the device driver and assert
 * ownership invariants of the `finally` block in `GpuBenchmarkCollector.collect()`: the caller's
 * prior binding is restored, no binding is leaked when the caller had none, and the process-shared
 * display is never terminated. `emulatorObserved = false` forces the real path on emulators too.
 *
 * No timing, throughput or draw-call assertions: the forced path may legitimately report
 * `unsupported` or `error` on emulated GL, and restoration is documented as best-effort.
 *
 * Every test body runs on its own worker thread that owns no application rendering state, and
 * releases only the EGL objects it created itself. `eglTerminate` is never called.
 */
@RunWith(AndroidJUnit4::class)
class GpuEglOwnershipInstrumentedTest {

  @Test
  fun forcedCollectRestoresCallersPriorEglBinding() {
    onWorkerThread("gpu-egl-restore") {
      val own = TestEglBinding.createAndMakeCurrent()
      try {
        val signals = GpuBenchmarkCollector(emulatorObserved = false).collect()
        assertDocumentedResultShape(signals)
        assertOwnBindingIsCurrentAndUsable(own)
      } finally {
        own.release()
      }
    }
  }

  @Test
  fun forcedCollectLeavesNoBindingWhenCallerHadNone() {
    onWorkerThread("gpu-egl-no-binding") {
      // Fresh worker: nothing was ever made current on this thread.
      assertEquals(EGL14.EGL_NO_CONTEXT, EGL14.eglGetCurrentContext())

      val signals = GpuBenchmarkCollector(emulatorObserved = false).collect()
      assertDocumentedResultShape(signals)

      assertEquals(EGL14.EGL_NO_CONTEXT, EGL14.eglGetCurrentContext())
      assertEquals(EGL14.EGL_NO_SURFACE, EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW))
    }
  }

  @Test
  fun forcedCollectLeavesSharedDisplayUsable() {
    onWorkerThread("gpu-egl-shared-display") {
      GpuBenchmarkCollector(emulatorObserved = false).collect()

      // Regression guard: the collector must never terminate EGL_DEFAULT_DISPLAY, which is shared
      // with the rest of the process.
      val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      assertTrue("shared display connection unavailable", display != EGL14.EGL_NO_DISPLAY)
      val version = IntArray(2)
      assertTrue(
        "eglInitialize failed on the shared display after collect()",
        EGL14.eglInitialize(display, version, 0, version, 1),
      )
      assertNotNull(
        "EGL_VENDOR unavailable on the shared display after collect()",
        EGL14.eglQueryString(display, EGL14.EGL_VENDOR),
      )
      assertNotNull(
        "no pbuffer/ES2 config available on the shared display after collect()",
        choosePbufferConfig(display),
      )
    }
  }

  @Test
  fun repeatedForcedCollectsKeepRestoringCallersBinding() {
    onWorkerThread("gpu-egl-repeated") {
      val own = TestEglBinding.createAndMakeCurrent()
      try {
        // Guard against per-run EGL/GL resource exhaustion across sequential collects.
        repeat(3) {
          val signals = GpuBenchmarkCollector(emulatorObserved = false).collect()
          assertDocumentedResultShape(signals)
          assertOwnBindingIsCurrentAndUsable(own)
        }
      } finally {
        own.release()
      }
    }
  }

  @Test
  fun forcedCollectReportsBenchmarkOrDocumentedSkipReason() {
    onWorkerThread("gpu-egl-result-shape") {
      val signals = GpuBenchmarkCollector(emulatorObserved = false).collect()
      assertDocumentedResultShape(signals)
      // The forced path must not attribute its outcome to the emulator heuristic it bypassed.
      if (signals.benchmarkPerformed == false) {
        assertTrue(
          "forced path reported skippedReason=emulator",
          signals.skippedReason != "emulator",
        )
      }
    }
  }

  /** Asserts the raw result shape only; never how fast or how much work the driver managed. */
  private fun assertDocumentedResultShape(signals: GpuBenchmarkSignals) {
    val performed = signals.benchmarkPerformed
    assertNotNull("benchmarkPerformed missing", performed)
    if (performed == true) {
      val drawCalls = signals.drawCallsCompleted
      val durationMs = signals.durationMs
      assertNotNull("drawCallsCompleted missing on a performed benchmark", drawCalls)
      assertNotNull("durationMs missing on a performed benchmark", durationMs)
      assertTrue("negative drawCallsCompleted: $drawCalls", drawCalls!! >= 0)
      assertTrue("negative durationMs: $durationMs", durationMs!! >= 0)
    } else {
      assertNotNull("skippedReason missing on a skipped benchmark", signals.skippedReason)
      val reason = signals.skippedReason.orEmpty()
      assertTrue("empty skippedReason on a skipped benchmark", reason.isNotEmpty())
      assertTrue("undocumented skippedReason: $reason", reason in DOCUMENTED_SKIP_REASONS)
    }
  }

  /**
   * Asserts the normal restoration path this test set up: the thread's own display, context and
   * draw/read surfaces are current again and the context still renders. Not asserted when a driver
   * refuses restoration — that case is documented as best-effort and is not set up here.
   */
  private fun assertOwnBindingIsCurrentAndUsable(own: TestEglBinding) {
    assertEquals("current context is not the caller's", own.context, EGL14.eglGetCurrentContext())
    assertEquals("current display is not the caller's", own.display, EGL14.eglGetCurrentDisplay())
    assertEquals(
      "current draw surface is not the caller's",
      own.surface,
      EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW),
    )
    assertEquals(
      "current read surface is not the caller's",
      own.surface,
      EGL14.eglGetCurrentSurface(EGL14.EGL_READ),
    )
    assertNotNull("GL_VENDOR unavailable on the caller's context", GLES20.glGetString(GLES20.GL_VENDOR))
    assertEquals("GL error on the caller's context", GLES20.GL_NO_ERROR, GLES20.glGetError())
  }

  /** The test thread's own display connection, 1x1 pbuffer surface and GLES2 context. */
  private class TestEglBinding(
    val display: EGLDisplay,
    val surface: EGLSurface,
    val context: EGLContext,
  ) {
    /** Releases only what this holder created; the shared display is left initialized. */
    fun release() {
      EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
      if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
      if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
    }

    companion object {
      fun createAndMakeCurrent(): TestEglBinding {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue("no EGL display connection for the test binding", display != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        assertTrue("eglInitialize failed for the test binding", EGL14.eglInitialize(display, version, 0, version, 1))
        val config = choosePbufferConfig(display)
        assertNotNull("no pbuffer/ES2 config for the test binding", config)

        val surface = EGL14.eglCreatePbufferSurface(
          display,
          config,
          intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
          0,
        )
        assertTrue("pbuffer surface creation failed for the test binding", surface != EGL14.EGL_NO_SURFACE)

        val context = EGL14.eglCreateContext(
          display,
          config,
          EGL14.EGL_NO_CONTEXT,
          intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
          0,
        )
        if (context == EGL14.EGL_NO_CONTEXT) {
          EGL14.eglDestroySurface(display, surface)
        }
        assertTrue("context creation failed for the test binding", context != EGL14.EGL_NO_CONTEXT)

        val holder = TestEglBinding(display, surface, context)
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
          holder.release()
          throw AssertionError("eglMakeCurrent failed for the test binding")
        }
        // Drain any pre-existing driver error state so a later assertion reports our own errors only.
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) Unit
        return holder
      }
    }
  }

  companion object {
    private val DOCUMENTED_SKIP_REASONS = setOf("unsupported", "error", "emulator")

    private fun choosePbufferConfig(display: EGLDisplay): EGLConfig? {
      val attribs = intArrayOf(
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_NONE,
      )
      val configs = arrayOfNulls<EGLConfig>(1)
      val numConfig = IntArray(1)
      if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfig, 0)) return null
      if (numConfig[0] <= 0) return null
      return configs[0]
    }

    /**
     * Runs the body on a dedicated worker with no application rendering state and rethrows its
     * failure on the test thread. The instrumentation main thread is never touched.
     */
    private fun onWorkerThread(name: String, body: () -> Unit) {
      var outcome: Result<Unit>? = null
      val worker = Thread({ outcome = runCatching(body) }, name)
      worker.start()
      worker.join()
      checkNotNull(outcome) { "worker $name produced no outcome" }.getOrThrow()
    }
  }
}
