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
}
