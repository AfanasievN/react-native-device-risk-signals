package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityEvidenceClassifierTest {
  @Test
  fun `dangerous properties retain only observed suspicious key value pairs`() {
    val observed = mapOf(
      "ro.debuggable" to "1",
      "ro.secure" to "1",
      "service.adb.root" to "1",
      "ro.sys.initd" to "0",
    )

    assertEquals(
      listOf("ro.debuggable=1", "service.adb.root=1"),
      IntegrityEvidenceClassifier.dangerousSystemProperties(observed),
    )
  }

  @Test
  fun `missing and stock properties produce no dangerous evidence`() {
    assertTrue(IntegrityEvidenceClassifier.dangerousSystemProperties(emptyMap()).isEmpty())
    assertTrue(
      IntegrityEvidenceClassifier.dangerousSystemProperties(
        mapOf("ro.debuggable" to "0", "ro.secure" to "1"),
      ).isEmpty(),
    )
  }

  @Test
  fun `hook class scan returns names without initializing classes`() {
    val candidates = listOf(
      "de.robv.android.xposed.XposedBridge",
      "com.saurik.substrate.MS\$SubstrateClass",
      "org.lsposed.lspd.core.Main",
    )
    val inspected = mutableListOf<String>()

    val found = IntegrityEvidenceClassifier.loadedHookClassNames(candidates) { name ->
      inspected.add(name)
      name == "de.robv.android.xposed.XposedBridge" || name == "org.lsposed.lspd.core.Main"
    }

    assertEquals(candidates, inspected)
    assertEquals(
      listOf("de.robv.android.xposed.XposedBridge", "org.lsposed.lspd.core.Main"),
      found,
    )
  }
}
