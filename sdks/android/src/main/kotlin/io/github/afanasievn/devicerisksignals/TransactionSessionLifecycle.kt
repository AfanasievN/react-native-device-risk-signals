package io.github.afanasievn.devicerisksignals

/** Invalidates callbacks even when another window wrapper retains our delegated callback. */
internal class TransactionSessionLifecycle {
  @Volatile private var generation = 0L
  @Volatile private var active = false
  @Volatile var isClosed = false
    private set

  fun attach(): Long {
    check(!isClosed) { "Transaction observation session is closed" }
    generation += 1
    active = true
    return generation
  }

  fun isActive(token: Long): Boolean = !isClosed && active && generation == token

  fun detach() {
    active = false
  }

  fun close() {
    detach()
    isClosed = true
  }
}
