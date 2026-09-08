package io.github.afanasievn.devicerisksignals

/** Small driver boundary keeps ownership failure paths testable without a live GL context. */
internal interface GpuProgramDriver {
  fun createShader(type: Int): Int
  fun shaderSource(shader: Int, source: String)
  fun compileShader(shader: Int)
  fun isShaderCompiled(shader: Int): Boolean
  fun deleteShader(shader: Int)
  fun createProgram(): Int
  fun attachShader(program: Int, shader: Int)
  fun linkProgram(program: Int)
  fun isProgramLinked(program: Int): Boolean
  fun deleteProgram(program: Int)
}

internal object GpuProgramBuilder {
  private const val VERTEX_SHADER = 35633
  private const val FRAGMENT_SHADER = 35632

  /** Transfers only a successfully linked program to the caller; all other objects stay owned here. */
  fun build(vertexSource: String, fragmentSource: String, driver: GpuProgramDriver): Int {
    val shaders = mutableListOf<Int>()
    var program = 0
    var transferred = false
    fun compile(type: Int, source: String): Int {
      val shader = driver.createShader(type)
      if (shader == 0) return 0
      shaders.add(shader)
      driver.shaderSource(shader, source)
      driver.compileShader(shader)
      return if (driver.isShaderCompiled(shader)) shader else 0
    }

    try {
      val vertex = compile(VERTEX_SHADER, vertexSource)
      if (vertex == 0) return 0
      val fragment = compile(FRAGMENT_SHADER, fragmentSource)
      if (fragment == 0) return 0
      program = driver.createProgram()
      if (program == 0) return 0
      driver.attachShader(program, vertex)
      driver.attachShader(program, fragment)
      driver.linkProgram(program)
      if (!driver.isProgramLinked(program)) return 0
      transferred = true
      return program
    } finally {
      for (shader in shaders) ignoreCleanupFailure { driver.deleteShader(shader) }
      if (program != 0 && !transferred) ignoreCleanupFailure { driver.deleteProgram(program) }
    }
  }
}

internal object GpuResourceCleanup {
  /**
   * Callers pass deletion closures only for owned objects. Delete GL objects while their context is
   * current, restore the caller's binding only when changed, then release owned EGL surface/context.
   * A shared display is not an owned resource and must never be terminated by these closures.
   */
  fun release(
    bindingChanged: Boolean,
    deleteProgram: () -> Unit,
    restoreBinding: () -> Unit,
    releaseResources: List<() -> Unit>,
  ) {
    ignoreCleanupFailure(deleteProgram)
    if (bindingChanged) ignoreCleanupFailure(restoreBinding)
    for (release in releaseResources) ignoreCleanupFailure(release)
  }
}

private fun ignoreCleanupFailure(action: () -> Unit) {
  try {
    action()
  } catch (_: Throwable) {
    // One driver cleanup failure must not prevent release of the remaining owned resources.
  }
}
