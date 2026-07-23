package com.reactnativedeviceintel

/** Pure classification helpers kept separate so unavailable reads never become negative evidence. */
internal object IntegrityEvidenceClassifier {
  private val dangerousPropertyValues = listOf(
    "ro.debuggable" to "1",
    "ro.secure" to "0",
    "service.adb.root" to "1",
    "ro.sys.initd" to "1",
  )

  fun dangerousSystemProperties(observed: Map<String, String>): List<String> =
    dangerousPropertyValues.mapNotNull { (key, dangerousValue) ->
      observed[key]?.takeIf { it == dangerousValue }?.let { "$key=$it" }
    }

  fun loadedHookClassNames(
    candidates: List<String>,
    isLoadableWithoutInitialization: (String) -> Boolean,
  ): List<String> = candidates.filter(isLoadableWithoutInitialization)

  private val hookStackClassPrefixes = listOf(
    "de.robv.android.xposed.",
    "org.lsposed.lspd.",
    "com.lsposed.lspd.",
    "edxp.",
  )

  /**
   * Scans a captured stack trace for hook-framework frames — the orthogonal "stack probe" method
   * (vs loaded classes and /proc/self/maps). Xposed/LSPosed/EdXposed/Substrate inject their bridge
   * frames into the dispatch path; `com.android.internal.os.ZygoteInit` appearing twice is a
   * re-injected-Zygote tell. Returns the raw `class.method` matches (empty ⇒ nothing suspicious).
   */
  fun hookStackFrameMatches(frames: List<StackTraceElement>): List<String> {
    val matches = LinkedHashSet<String>()
    var zygoteInitCount = 0
    for (frame in frames) {
      val className = frame.className
      if (className == "com.android.internal.os.ZygoteInit") zygoteInitCount++
      val matched = hookStackClassPrefixes.any { className.startsWith(it) } ||
        className.contains(".xposed.XposedBridge") ||
        className.startsWith("com.saurik.substrate")
      if (matched) matches.add("${className}.${frame.methodName}")
    }
    if (zygoteInitCount >= 2) matches.add("com.android.internal.os.ZygoteInit(x$zygoteInitCount)")
    return matches.toList()
  }
}
