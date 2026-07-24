package com.reactnativedeviceintel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

  @Test
  fun `stack frame scan matches hook framework frames`() {
    val frames = listOf(
      StackTraceElement("com.example.app.MainActivity", "onCreate", "MainActivity.kt", 12),
      StackTraceElement("de.robv.android.xposed.XposedBridge", "handleHookedMethod", "XposedBridge.java", 1),
      StackTraceElement("org.lsposed.lspd.core.Main", "forkAndSpecialize", null, 2),
      StackTraceElement("com.saurik.substrate.MS\$2", "invoked", null, 3),
    )

    assertEquals(
      listOf(
        "de.robv.android.xposed.XposedBridge.handleHookedMethod",
        "org.lsposed.lspd.core.Main.forkAndSpecialize",
        "com.saurik.substrate.MS\$2.invoked",
      ),
      IntegrityEvidenceClassifier.hookStackFrameMatches(frames),
    )
  }

  @Test
  fun `stack frame scan flags a doubly injected zygote and stays clean otherwise`() {
    val doubled = listOf(
      StackTraceElement("com.android.internal.os.ZygoteInit", "main", null, 1),
      StackTraceElement("com.android.internal.os.ZygoteInit", "main", null, 1),
    )
    assertEquals(
      listOf("com.android.internal.os.ZygoteInit(x2)"),
      IntegrityEvidenceClassifier.hookStackFrameMatches(doubled),
    )

    val clean = listOf(
      StackTraceElement("com.android.internal.os.ZygoteInit", "main", null, 1),
      StackTraceElement("com.facebook.react.bridge.JavaMethodWrapper", "invoke", null, 2),
    )
    assertTrue(IntegrityEvidenceClassifier.hookStackFrameMatches(clean).isEmpty())
  }

  @Test
  fun `frida thread-name scan keeps only frida-specific names`() {
    assertEquals(
      listOf("gum-js-loop", "pool-frida"),
      IntegrityEvidenceClassifier.fridaThreadNamesFound(
        listOf("main", "gmain", "gum-js-loop", "Binder:1", "pool-frida", "RenderThread"),
      ),
    )
    assertTrue(IntegrityEvidenceClassifier.fridaThreadNamesFound(listOf("main", "gmain", "gdbus")).isEmpty())
  }

  @Test
  fun `magisk abstract socket heuristic flags a 32-plus char random name only`() {
    val header = "Num RefCount Protocol Flags Type St Inode Path\n"
    val magisk = header +
      "0000: 00000002 00000000 00010000 0001 01 12345 @A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6\n"
    val clean = header +
      "0000: 00000002 00000000 00010000 0001 01 12345 @android:something\n" +
      "0000: 00000002 00000000 00010000 0001 01 12346 /dev/socket/logd\n"
    assertTrue(IntegrityEvidenceClassifier.magiskAbstractSocketPresent(magisk))
    assertFalse(IntegrityEvidenceClassifier.magiskAbstractSocketPresent(clean))
  }

  @Test
  fun `frida listener detected on default ports in LISTEN state`() {
    val header = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid\n"
    val listening = header + "   0: 0100007F:69A2 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0\n"
    val established = header + "   0: 0100007F:69A2 0100007F:ABCD 01 00000000:00000000 00:00000000 00000000  10000\n"
    assertTrue(IntegrityEvidenceClassifier.fridaListenerPresent(listening))
    assertFalse(IntegrityEvidenceClassifier.fridaListenerPresent(established))
  }

  @Test
  fun `magic mount cross-check matches a system file living on the data device`() {
    // /data on device 253:0 (decimal); maps dev is hex (fd:00 == 253:0).
    val mountinfo = "35 24 253:0 / /data rw,nosuid,nodev,noatime - ext4 /dev/block/dm-5 rw\n" +
      "22 24 254:1 / /system ro,noatime - ext4 /dev/block/dm-0 ro\n"
    val magicMounted = "700000000-700010000 r-xp 00000000 fd:00 5001 /system/app/Injected/base.apk\n"
    val clean = "700000000-700010000 r-xp 00000000 fe:01 5001 /system/framework/framework.jar\n"
    assertEquals(1, IntegrityEvidenceClassifier.magicMountModuleCount(mountinfo, magicMounted))
    assertEquals(0, IntegrityEvidenceClassifier.magicMountModuleCount(mountinfo, clean))
    assertEquals(0, IntegrityEvidenceClassifier.magicMountModuleCount("", magicMounted))
  }
}
