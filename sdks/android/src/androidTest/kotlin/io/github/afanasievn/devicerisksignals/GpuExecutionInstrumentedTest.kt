package io.github.afanasievn.devicerisksignals

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented execution coverage for `collectGpuBenchmark()` on a real Android runtime: UI-thread
 * rejection against an actual Looper, raw-model shape, and repeated/concurrent worker calls. Timing
 * is never asserted — the 50 ms draw loop is a budget, not a deadline — and the expected shape is
 * derived from the observed result, so the same assertions hold on an emulator and on real hardware.
 */
@RunWith(AndroidJUnit4::class)
class GpuExecutionInstrumentedTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private data class UiThreadAttempt(val thrown: Throwable?, val result: GpuBenchmarkSignals?)

  @Test fun uiThreadCallIsRejectedWithoutProducingAResult() {
    val attempt = attemptOnUiThread()

    assertNull("no GPU result may be produced on the UI thread", attempt.result)
    assertNotNull("the UI-thread call must be rejected", attempt.thrown)
    assertTrue(
      "expected IllegalStateException, got ${attempt.thrown}",
      attempt.thrown is IllegalStateException,
    )
  }

  @Test fun workerCallReturnsWellFormedRawMap() {
    withWorker { executor -> assertRawShape(collectOnWorker(executor).toRawMap()) }
  }

  @Test fun repeatedSequentialWorkerCallsKeepTheSameDecisionAndShape() {
    withWorker { executor ->
      val runs = (1..REPEATED_RUNS).map { collectOnWorker(executor).toRawMap() }
      runs.forEach { assertRawShape(it) }

      val first = runs.first()
      runs.drop(1).forEachIndexed { index, run ->
        val label = "run ${index + 2} of $REPEATED_RUNS"
        assertEquals("$label changed the skip/performed decision",
          first["benchmarkPerformed"], run["benchmarkPerformed"])
        assertEquals("$label changed the skip reason", first["skippedReason"], run["skippedReason"])
        assertEquals("$label changed the reported field set", stableKeys(first), stableKeys(run))
      }
    }
  }

  @Test fun concurrentWorkerCallsBothReturnWithoutThrowing() {
    val rendezvous = CountDownLatch(CONCURRENT_CALLS)
    val results = List(CONCURRENT_CALLS) { AtomicReference<GpuBenchmarkSignals>() }
    val failures = List(CONCURRENT_CALLS) { AtomicReference<Throwable>() }

    val threads = List(CONCURRENT_CALLS) { index ->
      Thread({
        rendezvous.countDown()
        try {
          // Both calls must be inside the collector at the same time.
          rendezvous.await()
          results[index].set(deviceRiskSignals().collectGpuBenchmark())
        } catch (t: Throwable) {
          failures[index].set(t)
        }
      }, "gpu-instrumented-$index")
    }
    threads.forEach { it.start() }
    threads.forEach { it.join() }

    failures.forEachIndexed { index, failure ->
      assertNull("concurrent call $index threw ${failure.get()}", failure.get())
    }
    results.forEachIndexed { index, result ->
      assertNotNull("concurrent call $index produced no result", result.get())
      assertRawShape(result.get().toRawMap())
    }
  }

  @Test fun workerCallStillWorksAfterUiThreadRejection() {
    val rejected = attemptOnUiThread()
    assertTrue(
      "precondition: the UI-thread call must have been rejected, got ${rejected.thrown}",
      rejected.thrown is IllegalStateException,
    )

    withWorker { executor -> assertRawShape(collectOnWorker(executor).toRawMap()) }
  }

  private fun deviceRiskSignals() = DeviceRiskSignals(instrumentation.targetContext)

  private fun attemptOnUiThread(): UiThreadAttempt {
    val thrown = AtomicReference<Throwable>()
    val result = AtomicReference<GpuBenchmarkSignals>()
    // Caught inside the runnable: a throwable escaping runOnMainSync would break the main looper
    // and hang the sync barrier instead of failing this test.
    instrumentation.runOnMainSync {
      try {
        result.set(deviceRiskSignals().collectGpuBenchmark())
      } catch (t: Throwable) {
        thrown.set(t)
      }
    }
    return UiThreadAttempt(thrown.get(), result.get())
  }

  private fun collectOnWorker(executor: ExecutorService): GpuBenchmarkSignals =
    executor.submit<GpuBenchmarkSignals> { deviceRiskSignals().collectGpuBenchmark() }.get()

  private fun withWorker(block: (ExecutorService) -> Unit) {
    val executor = Executors.newSingleThreadExecutor()
    try {
      block(executor)
    } finally {
      executor.shutdownNow()
    }
  }

  private fun assertRawShape(raw: Map<String, Any>) {
    assertTrue("benchmarkPerformed must always be reported: $raw", raw.containsKey("benchmarkPerformed"))
    when (val performed = raw["benchmarkPerformed"]) {
      false -> {
        val reason = raw["skippedReason"]
        assertTrue("a skip must name a non-empty reason: $raw", reason is String && reason.isNotEmpty())
        // The emulator gate returns before any EGL call, so it may report nothing else.
        if (reason == EMULATOR_REASON) {
          assertEquals(mapOf("benchmarkPerformed" to false, "skippedReason" to EMULATOR_REASON), raw)
        }
      }
      true -> {
        val drawCalls = raw["drawCallsCompleted"]
        val durationMs = raw["durationMs"]
        assertTrue("a performed benchmark must report drawCallsCompleted: $raw", drawCalls is Int)
        assertTrue("a performed benchmark must report durationMs: $raw", durationMs is Int)
        assertTrue("drawCallsCompleted must not be negative: $drawCalls", (drawCalls as Int) >= 0)
        assertTrue("durationMs must not be negative: $durationMs", (durationMs as Int) >= 0)
      }
      else -> fail("benchmarkPerformed must be a boolean, was $performed")
    }
  }

  /**
   * Presence of the two summary fields below depends on the observed sample count and mean by
   * design, so requiring it to repeat would be a timing assertion rather than a shape assertion.
   */
  private fun stableKeys(raw: Map<String, Any>): Set<String> = raw.keys - SAMPLE_DEPENDENT_KEYS

  companion object {
    private const val REPEATED_RUNS = 3
    private const val CONCURRENT_CALLS = 2
    private const val EMULATOR_REASON = "emulator"
    private val SAMPLE_DEPENDENT_KEYS =
      setOf("operationTimeCoefficientOfVariation", "warmupSlope")
  }
}
