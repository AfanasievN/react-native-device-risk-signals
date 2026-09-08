package io.github.afanasievn.devicerisksignals

import org.junit.Assert.fail
import org.junit.Test

class GpuExecutionPolicyTest {
  @Test fun rejectsUiThreadBeforeAnyGpuWork() {
    try {
      GpuExecutionPolicy.requireWorker(true)
      fail("GPU work must not run on the UI thread")
    } catch (_: IllegalStateException) {
      // Expected caller contract violation, not a fabricated benchmark result.
    }
  }

  @Test fun acceptsWorkerThread() {
    GpuExecutionPolicy.requireWorker(false)
  }
}
