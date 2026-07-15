package com.reactnativedeviceintel

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * gpu_benchmark (Android) — headless EGL pbuffer + GLES 2.0 (not 3.x, for max compatibility with
 * low/mid MediaTek/Unisoc devices). Reports the GPU renderer/vendor/version strings (the high-entropy
 * fingerprint) plus a draw-call-throughput count over a fixed time budget. All EGL/GL resources are
 * torn down in `finally` — a leaked EGL context across repeated calls is a real risk. Self-skips on
 * emulators (draw-call timing there is meaningless as an entropy signal). Never runs until config
 * enables the probe (ships disabled — see gpuBenchmarkProbe.ts).
 */
class GpuBenchmarkProvider {

  fun getGpuBenchmark(): WritableMap {
    val map = Arguments.createMap()

    if (isLikelyEmulator()) {
      map.putBoolean("benchmarkPerformed", false)
      map.putString("skippedReason", "emulator")
      return map
    }

    var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    var context: EGLContext = EGL14.EGL_NO_CONTEXT
    var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    var program = 0

    try {
      display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      if (display == EGL14.EGL_NO_DISPLAY) return skipped(map, "unsupported")
      val version = IntArray(2)
      if (!EGL14.eglInitialize(display, version, 0, version, 1)) return skipped(map, "unsupported")

      val config = chooseConfig(display) ?: return skipped(map, "unsupported")

      surface = EGL14.eglCreatePbufferSurface(
        display,
        config,
        intArrayOf(EGL14.EGL_WIDTH, PBUFFER_SIZE, EGL14.EGL_HEIGHT, PBUFFER_SIZE, EGL14.EGL_NONE),
        0,
      )
      if (surface == EGL14.EGL_NO_SURFACE) return skipped(map, "unsupported")

      context = EGL14.eglCreateContext(
        display,
        config,
        EGL14.EGL_NO_CONTEXT,
        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
        0,
      )
      if (context == EGL14.EGL_NO_CONTEXT) return skipped(map, "unsupported")

      if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return skipped(map, "unsupported")

      // GPU identity strings — the high-entropy fingerprint.
      putStringIfPresent(map, "rendererName", GLES20.glGetString(GLES20.GL_RENDERER))
      putStringIfPresent(map, "vendorName", GLES20.glGetString(GLES20.GL_VENDOR))
      putStringIfPresent(map, "apiVersion", GLES20.glGetString(GLES20.GL_VERSION))
      putStringIfPresent(map, "shadingLanguageVersion", GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION))
      val maxTex = IntArray(1)
      GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
      if (maxTex[0] > 0) map.putInt("maxTextureSize", maxTex[0])

      program = buildProgram()
      if (program == 0) return skipped(map, "unsupported")

      val (drawCalls, durationMs) = runBenchmark(program)
      map.putBoolean("benchmarkPerformed", true)
      map.putInt("drawCallsCompleted", drawCalls)
      map.putInt("durationMs", durationMs)
    } catch (e: Throwable) {
      return skipped(map, "error")
    } finally {
      if (program != 0) {
        try {
          GLES20.glDeleteProgram(program)
        } catch (_: Throwable) {
        }
      }
      if (display != EGL14.EGL_NO_DISPLAY) {
        // Unbind THIS thread only, then destroy ONLY the surface/context we created. Do NOT call
        // eglTerminate(display): EGL_DEFAULT_DISPLAY is a single process-wide connection shared with
        // React Native's renderer and other GL users (react-native-vision-camera, react-native-video).
        // eglInitialize/eglTerminate are not reference-counted, so terminating it would mark EVERY
        // EGL resource in the process for deletion and corrupt/crash a concurrent camera/video preview.
        // Leaving the shared display initialized is correct and harmless (RN keeps it alive anyway).
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
      }
    }

    return map
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

  private fun runBenchmark(program: Int): Pair<Int, Int> {
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
    val start = System.nanoTime()
    val budgetNs = BUDGET_MS * 1_000_000L
    while (System.nanoTime() - start < budgetNs) {
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
      GLES20.glFinish()
      drawCalls++
    }
    val durationMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
    return Pair(drawCalls, durationMs)
  }

  private fun buildProgram(): Int {
    val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SRC)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SRC)
    if (vs == 0 || fs == 0) return 0
    val program = GLES20.glCreateProgram()
    if (program == 0) return 0
    GLES20.glAttachShader(program, vs)
    GLES20.glAttachShader(program, fs)
    GLES20.glLinkProgram(program)
    // Shaders can be detached/deleted once linked; the program keeps its own copy.
    GLES20.glDeleteShader(vs)
    GLES20.glDeleteShader(fs)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    if (status[0] != GLES20.GL_TRUE) {
      GLES20.glDeleteProgram(program)
      return 0
    }
    return program
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    if (shader == 0) return 0
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] != GLES20.GL_TRUE) {
      GLES20.glDeleteShader(shader)
      return 0
    }
    return shader
  }

  private fun skipped(map: WritableMap, reason: String): WritableMap {
    map.putBoolean("benchmarkPerformed", false)
    map.putString("skippedReason", reason)
    return map
  }

  private fun putStringIfPresent(map: WritableMap, key: String, value: String?) {
    if (!value.isNullOrEmpty()) map.putString(key, value)
  }

  private fun isLikelyEmulator(): Boolean {
    val fp = (Build.FINGERPRINT ?: "").lowercase()
    val model = (Build.MODEL ?: "").lowercase()
    val hardware = (Build.HARDWARE ?: "").lowercase()
    return fp.contains("generic") || fp.contains("emulator") || fp.contains("sdk") ||
      model.contains("emulator") || model.contains("android sdk") ||
      hardware.contains("goldfish") || hardware.contains("ranchu") || hardware.contains("vbox")
  }

  companion object {
    private const val PBUFFER_SIZE = 32
    private const val BUDGET_MS = 50L

    private const val VERTEX_SRC = "attribute vec4 aPos;\nvoid main() {\n  gl_Position = aPos;\n}\n"
    private const val FRAGMENT_SRC =
      "precision mediump float;\nvoid main() {\n  gl_FragColor = vec4(1.0, 0.5, 0.2, 1.0);\n}\n"
  }
}
