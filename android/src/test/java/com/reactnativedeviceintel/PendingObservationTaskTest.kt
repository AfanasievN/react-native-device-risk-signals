package com.reactnativedeviceintel

import org.junit.Assert.*
import org.junit.Test

class PendingObservationTaskTest {
  @Test fun timedOutQueuedWorkCannotAttachLater() {
    var attachments = 0
    val task = PendingObservationTask { attachments++; "snapshot" }
    assertNull(task.await(0))
    task.run()
    assertEquals(0, attachments)
  }

  @Test fun cancelledQueuedWorkCannotRun() {
    var calls = 0
    val task = PendingObservationTask { calls++ }
    task.cancel()
    task.run()
    assertEquals(0, calls)
  }

  @Test fun completedWorkReturnsResultAndRunsOnlyOnce() {
    var calls = 0
    val task = PendingObservationTask { calls++; "snapshot" }
    task.run()
    task.run()
    assertEquals("snapshot", task.await(0))
    assertEquals(1, calls)
  }

  @Test fun failedWorkReleasesWaiterWithUnavailableResult() {
    val task = PendingObservationTask<String> { throw IllegalStateException("unavailable") }
    task.run()
    assertNull(task.await(0))
  }
}
