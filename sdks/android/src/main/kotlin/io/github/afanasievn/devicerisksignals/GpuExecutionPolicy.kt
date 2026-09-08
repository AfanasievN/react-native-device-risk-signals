package io.github.afanasievn.devicerisksignals

internal object GpuExecutionPolicy {
  fun requireWorker(isMainThread: Boolean) {
    check(!isMainThread) { "GPU collection requires a worker thread" }
  }
}
