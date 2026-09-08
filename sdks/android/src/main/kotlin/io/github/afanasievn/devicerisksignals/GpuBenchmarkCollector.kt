package io.github.afanasievn.devicerisksignals

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * gpu_benchmark (Android) — headless EGL pbuffer + GLES 2.0 (not 3.x, for max compatibility with
 * low/mid MediaTek/Unisoc devices). Reports the GPU renderer/vendor/version strings (the high-entropy
 * fingerprint) plus a draw-call-throughput count over a fixed time budget. All EGL/GL resources are
 * torn down in `finally`, restoring the calling thread's prior EGL binding where possible. Self-skips
 * on likely emulators. The caller must explicitly request this work on a background thread; no
 * automatic collection is scheduled by the standalone SDK.
 */
internal class GpuBenchmarkCollector(
  private val emulatorObserved: Boolean = GpuEmulatorHeuristic.isLikelyEmulator(),
  private val mainThread: () -> Boolean = { Looper.myLooper() == Looper.getMainLooper() },
) {

  private data class BenchmarkResult(
    val drawCalls: Int,
    val durationMs: Int,
    val operationTimesMs: List<Double>,
  )

  fun collect(): GpuBenchmarkSignals {
    // Guard here, not only in the facade, so no internal caller can start GL work on the UI thread.
    GpuExecutionPolicy.requireWorker(mainThread())

    var signals = GpuBenchmarkSignals()

    if (emulatorObserved) {
      return skipped(signals, "emulator")
    }

    var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    var context: EGLContext = EGL14.EGL_NO_CONTEXT
    var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    var program = 0
    var bindingChanged = false
    var previousDisplay = EGL14.EGL_NO_DISPLAY
    var previousContext = EGL14.EGL_NO_CONTEXT
    var previousDrawSurface = EGL14.EGL_NO_SURFACE
    var previousReadSurface = EGL14.EGL_NO_SURFACE

    try {
      previousDisplay = EGL14.eglGetCurrentDisplay()
      previousContext = EGL14.eglGetCurrentContext()
      previousDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
      previousReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
      display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      if (display == EGL14.EGL_NO_DISPLAY) return skipped(signals, "unsupported")
      val version = IntArray(2)
      if (!EGL14.eglInitialize(display, version, 0, version, 1)) return skipped(signals, "unsupported")

      val config = chooseConfig(display) ?: return skipped(signals, "unsupported")

      surface = EGL14.eglCreatePbufferSurface(
        display,
        config,
        intArrayOf(EGL14.EGL_WIDTH, PBUFFER_SIZE, EGL14.EGL_HEIGHT, PBUFFER_SIZE, EGL14.EGL_NONE),
        0,
      )
      if (surface == EGL14.EGL_NO_SURFACE) return skipped(signals, "unsupported")

      context = EGL14.eglCreateContext(
        display,
        config,
        EGL14.EGL_NO_CONTEXT,
        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
        0,
      )
      if (context == EGL14.EGL_NO_CONTEXT) return skipped(signals, "unsupported")

      if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return skipped(signals, "unsupported")
      bindingChanged = true

      // GPU identity strings — the high-entropy fingerprint.
      signals = signals.copy(rendererName = GLES20.glGetString(GLES20.GL_RENDERER)?.takeIf { it.isNotEmpty() })
      signals = signals.copy(vendorName = GLES20.glGetString(GLES20.GL_VENDOR)?.takeIf { it.isNotEmpty() })
      signals = signals.copy(apiVersion = GLES20.glGetString(GLES20.GL_VERSION)?.takeIf { it.isNotEmpty() })
      signals = signals.copy(shadingLanguageVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION)?.takeIf { it.isNotEmpty() })
      val maxTex = IntArray(1)
      GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
      if (maxTex[0] > 0) signals = signals.copy(maxTextureSize = maxTex[0])

      program = buildProgram()
      if (program == 0) return skipped(signals, "unsupported")

      val benchmark = runBenchmark(program)
      signals = signals.copy(benchmarkPerformed = true, drawCallsCompleted = benchmark.drawCalls,
        durationMs = benchmark.durationMs)
      val summary = SignalStatistics.summarize(benchmark.operationTimesMs)
      if (summary != null) {
        signals = signals.copy(operationTimeP50Ms = summary.median, operationTimeP95Ms = summary.p95,
          operationTimeMadMs = summary.mad, operationTimeCoefficientOfVariation = summary.coefficientOfVariation)
      }
      signals = signals.copy(warmupSlope = SignalStatistics.warmupSlope(benchmark.operationTimesMs))
    } catch (e: Throwable) {
      if (e is InterruptedException) Thread.currentThread().interrupt()
      return skipped(signals, "error")
    } finally {
      // Never terminate the process-shared EGL display. Only release resources created here, after
      // deleting our GL program in its context and restoring this thread's previous EGL binding.
      GpuResourceCleanup.release(
        bindingChanged = bindingChanged,
        deleteProgram = { if (program != 0) GLES20.glDeleteProgram(program) },
        restoreBinding = {
          val restored = if (previousDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(previousDisplay, previousDrawSurface, previousReadSurface, previousContext)
          } else {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
          }
          // A driver can refuse restoration. At least release our own current binding in that case.
          if (!restored) EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        },
        releaseResources = listOf(
          { if (surface != EGL14.EGL_NO_SURFACE) { EGL14.eglDestroySurface(display, surface) }; Unit },
          { if (context != EGL14.EGL_NO_CONTEXT) { EGL14.eglDestroyContext(display, context) }; Unit },
        ),
      )
    }

    return signals
  }

  private fun chooseConfig(display: EGLDisplay): EGLConfig? {
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

  private fun runBenchmark(program: Int): BenchmarkResult {
    GLES20.glViewport(0, 0, PBUFFER_SIZE, PBUFFER_SIZE)
    GLES20.glUseProgram(program)

    val vertices = floatArrayOf(0f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f)
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .put(vertices)
    buffer.position(0)

    val posHandle = GLES20.glGetAttribLocation(program, "aPos")
    GLES20.glEnableVertexAttribArray(posHandle)
    GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, buffer)

    var drawCalls = 0
    val operationTimesMs = mutableListOf<Double>()
    val start = System.nanoTime()
    val budgetNs = BUDGET_MS * 1_000_000L
    while (System.nanoTime() - start < budgetNs) {
      val operationStart = System.nanoTime()
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
      GLES20.glFinish()
      operationTimesMs.add((System.nanoTime() - operationStart) / 1_000_000.0)
      drawCalls++
    }
    val durationMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
    return BenchmarkResult(drawCalls, durationMs, operationTimesMs)
  }

  private fun buildProgram(): Int = GpuProgramBuilder.build(VERTEX_SRC, FRAGMENT_SRC, object : GpuProgramDriver {
    override fun createShader(type: Int): Int = GLES20.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES20.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES20.glCompileShader(shader)
    override fun isShaderCompiled(shader: Int): Boolean {
      val status = IntArray(1)
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
      return status[0] == GLES20.GL_TRUE
    }
    override fun deleteShader(shader: Int) = GLES20.glDeleteShader(shader)
    override fun createProgram(): Int = GLES20.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES20.glAttachShader(program, shader)
    override fun linkProgram(program: Int) = GLES20.glLinkProgram(program)
    override fun isProgramLinked(program: Int): Boolean {
      val status = IntArray(1)
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
      return status[0] == GLES20.GL_TRUE
    }
    override fun deleteProgram(program: Int) = GLES20.glDeleteProgram(program)
  })

  private fun skipped(signals: GpuBenchmarkSignals, reason: String): GpuBenchmarkSignals =
    signals.copy(benchmarkPerformed = false, skippedReason = reason)

  companion object {
    private const val PBUFFER_SIZE = 32
    private const val BUDGET_MS = 50L

    private const val VERTEX_SRC = "attribute vec4 aPos;\nvoid main() {\n  gl_Position = aPos;\n}\n"
    private const val FRAGMENT_SRC =
      "precision mediump float;\nvoid main() {\n  gl_FragColor = vec4(1.0, 0.5, 0.2, 1.0);\n}\n"
  }
}
