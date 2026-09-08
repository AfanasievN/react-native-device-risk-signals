package io.github.afanasievn.devicerisksignals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GpuResourceManagementTest {
  private class Driver : GpuProgramDriver {
    var nextShader = 0
    var failedShader = 0
    var program = 3
    var linked = true
    var throwAt = ""
    val deletedShaders = mutableListOf<Int>()
    val deletedPrograms = mutableListOf<Int>()
    override fun createShader(type: Int): Int = ++nextShader
    override fun shaderSource(shader: Int, source: String) {
      if (throwAt == "source") error("source unavailable")
    }
    override fun compileShader(shader: Int) = Unit
    override fun isShaderCompiled(shader: Int): Boolean = shader != failedShader
    override fun deleteShader(shader: Int) {
      deletedShaders.add(shader)
      if (throwAt == "delete") error("delete unavailable")
    }
    override fun createProgram(): Int = program
    override fun attachShader(program: Int, shader: Int) {
      if (throwAt == "attach") error("attach unavailable")
    }
    override fun linkProgram(program: Int) = Unit
    override fun isProgramLinked(program: Int): Boolean = linked
    override fun deleteProgram(program: Int) { deletedPrograms.add(program) }
  }

  @Test fun successfulProgramTransfersOwnershipAndDeletesShaders() {
    val driver = Driver()
    assertEquals(3, GpuProgramBuilder.build("vertex", "fragment", driver))
    assertEquals(listOf(1, 2), driver.deletedShaders.sorted())
    assertEquals(emptyList<Int>(), driver.deletedPrograms)
  }

  @Test fun failedFragmentAlsoReleasesSuccessfulVertex() {
    val driver = Driver().apply { failedShader = 2 }
    assertEquals(0, GpuProgramBuilder.build("vertex", "fragment", driver))
    assertEquals(listOf(1, 2), driver.deletedShaders.sorted())
    assertEquals(emptyList<Int>(), driver.deletedPrograms)
  }

  @Test fun zeroProgramDoesNotDeleteUnownedProgram() {
    val driver = Driver().apply { program = 0 }
    assertEquals(0, GpuProgramBuilder.build("vertex", "fragment", driver))
    assertEquals(listOf(1, 2), driver.deletedShaders.sorted())
    assertEquals(emptyList<Int>(), driver.deletedPrograms)
  }

  @Test fun failedLinkDeletesAllOwnedObjects() {
    val driver = Driver().apply { linked = false }
    assertEquals(0, GpuProgramBuilder.build("vertex", "fragment", driver))
    assertEquals(listOf(1, 2), driver.deletedShaders.sorted())
    assertEquals(listOf(3), driver.deletedPrograms)
  }

  @Test fun exceptionsReleasePartiallyCreatedShaderAndProgram() {
    val sourceFailure = Driver().apply { throwAt = "source" }
    assertThrows(IllegalStateException::class.java) {
      GpuProgramBuilder.build("vertex", "fragment", sourceFailure)
    }
    assertEquals(listOf(1), sourceFailure.deletedShaders)
    val attachFailure = Driver().apply { throwAt = "attach" }
    assertThrows(IllegalStateException::class.java) {
      GpuProgramBuilder.build("vertex", "fragment", attachFailure)
    }
    assertEquals(listOf(1, 2), attachFailure.deletedShaders.sorted())
    assertEquals(listOf(3), attachFailure.deletedPrograms)
  }

  @Test fun failingShaderDeletionDoesNotSkipOtherCleanup() {
    val driver = Driver().apply { throwAt = "delete"; linked = false }
    assertEquals(0, GpuProgramBuilder.build("vertex", "fragment", driver))
    assertEquals(listOf(1, 2), driver.deletedShaders.sorted())
    assertEquals(listOf(3), driver.deletedPrograms)
  }

  @Test fun cleanupDeletesProgramBeforeRestoreThenReleasesOwnedEglObjects() {
    val calls = mutableListOf<String>()
    GpuResourceCleanup.release(true, { calls.add("program") }, { calls.add("restore") },
      listOf({ calls.add("surface"); Unit }, { calls.add("context"); Unit }))
    assertEquals(listOf("program", "restore", "surface", "context"), calls)
  }

  @Test fun unchangedBindingIsNotRestoredAndEveryCleanupSurvivesEarlierFailure() {
    val calls = mutableListOf<String>()
    GpuResourceCleanup.release(false, { calls.add("program"); error("failure") },
      { calls.add("restore") }, listOf({ calls.add("surface"); error("failure") },
        { calls.add("context"); Unit }))
    assertEquals(listOf("program", "surface", "context"), calls)
    calls.clear()
    GpuResourceCleanup.release(true, { error("program failure") }, { calls.add("restore") },
      listOf({ calls.add("context"); Unit }))
    assertEquals(listOf("restore", "context"), calls)
    calls.clear()
    GpuResourceCleanup.release(true, {}, { error("restore failure") },
      listOf({ calls.add("context"); Unit }))
    assertEquals(listOf("context"), calls)
  }
}
