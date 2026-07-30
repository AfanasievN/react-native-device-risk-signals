package com.reactnativedeviceintel

internal object PathExecutableProbe {
  fun absoluteCandidates(pathValue: String?, executableName: String): List<String> {
    if (pathValue == null) return emptyList()
    return pathValue
      .split(':')
      .map { it.trim() }
      .filter { it.startsWith("/") }
      .map { directory -> "${directory.trimEnd('/')}/$executableName" }
      .distinct()
  }

  fun existsOnPath(
    pathValue: String?,
    executableName: String,
    exists: (String) -> Boolean,
  ): Boolean? {
    if (pathValue == null) return null
    return try {
      absoluteCandidates(pathValue, executableName).any(exists)
    } catch (e: Throwable) {
      null
    }
  }
}
