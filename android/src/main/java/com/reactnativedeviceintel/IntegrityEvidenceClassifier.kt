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

  // Frida injects GLib/Gum worker threads; these names are frida-specific enough to avoid the
  // false positives that a bare "gmain"/"gdbus" would cause in apps that legitimately use GLib.
  private val fridaThreadNames = setOf("gum-js-loop", "pool-frida", "frida-gum")

  fun fridaThreadNamesFound(observedThreadNames: List<String>): List<String> =
    observedThreadNames.map(String::trim).filter { it in fridaThreadNames }.distinct()

  /**
   * Magisk replaces its control unix-domain-socket name with a random 32-char string. Scan
   * /proc/net/unix for an ABSTRACT socket (name starts with '@') whose name is >= 32 chars and
   * contains none of '/', '.', ' ' — the rootbeerFresh heuristic. Raw signal (a heuristic tell).
   */
  fun magiskAbstractSocketPresent(procNetUnix: String): Boolean =
    procNetUnix.lineSequence().drop(1).any { line ->
      val name = line.trim().substringAfterLast(' ', "")
      name.startsWith("@") &&
        name.removePrefix("@").let { it.length >= 32 && !it.contains('/') && !it.contains('.') }
    }

  /** /proc/net/tcp(6): a LISTEN socket (st == 0A) on the frida-server default ports 27042/27043. */
  fun fridaListenerPresent(procNetTcp: String): Boolean =
    procNetTcp.lineSequence().drop(1).any { line ->
      val cols = line.trim().split(Regex("\\s+"))
      val local = cols.getOrNull(1).orEmpty()
      val state = cols.getOrNull(3).orEmpty().uppercase()
      val port = local.substringAfterLast(':', "").uppercase()
      state == "0A" && (port == "69A2" || port == "69A3")
    }

  /**
   * Magisk "magic mount" bind-mounts module files from the /data partition over /system paths, so a
   * file mapped from /system|/vendor|/product|/system_ext that shares the /data device number is a
   * module. Compares /data's dev (from mountinfo, DECIMAL) against each system-mapped file's dev
   * (from maps, HEX). Returns the count of distinct matching system paths. Catches magic-mount even
   * when path/name signatures are hidden.
   */
  fun magicMountModuleCount(mountinfo: String, maps: String): Int {
    val dataDev = mountinfo.lineSequence()
      .map { it.trim().split(Regex("\\s+")) }
      .firstOrNull { it.getOrNull(3) == "/" && it.getOrNull(4) == "/data" }
      ?.getOrNull(2)
      ?.let { parseDecimalDev(it) }
      ?: return 0

    val systemPrefixes = listOf("/system/", "/vendor/", "/product/", "/system_ext/")
    val matches = LinkedHashSet<String>()
    for (line in maps.lineSequence()) {
      val cols = line.trim().split(Regex("\\s+"), limit = 6)
      val devHex = cols.getOrNull(3) ?: continue
      val path = cols.getOrNull(5)?.trim() ?: continue
      if (systemPrefixes.none { path.startsWith(it) }) continue
      if (parseHexDev(devHex) == dataDev) matches.add(path)
    }
    return matches.size
  }

  private fun parseDecimalDev(value: String): Pair<Int, Int>? {
    val parts = value.split(':')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return major to minor
  }

  private fun parseHexDev(value: String): Pair<Int, Int>? {
    val parts = value.split(':')
    val major = parts.getOrNull(0)?.toIntOrNull(16) ?: return null
    val minor = parts.getOrNull(1)?.toIntOrNull(16) ?: return null
    return major to minor
  }
}
