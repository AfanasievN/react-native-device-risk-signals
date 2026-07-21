package com.reactnativedeviceintel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorEvidenceClassifierTest {
  @Test
  fun `standard physical device has no emulator evidence`() {
    val evidence = EmulatorEvidenceClassifier.classify(
      build = physicalPixel(),
      emulatorFilePaths = emptyList(),
      systemProperties = emptyMap(),
      cpuInfo = "Processor: AArch64 Processor rev 1",
    )

    assertFalse(evidence.isStrongEmulatorEvidence)
    assertTrue(evidence.buildMarkers.isEmpty())
  }

  @Test
  fun `test keys unknown manufacturer and x86 abi are supporting evidence only`() {
    val evidence = EmulatorEvidenceClassifier.classify(
      build = physicalPixel().copy(
        fingerprint = "example/device/build:userdebug/test-keys",
        manufacturer = "unknown",
        supportedAbis = listOf("x86_64"),
      ),
      emulatorFilePaths = emptyList(),
      systemProperties = emptyMap(),
      cpuInfo = null,
    )

    assertFalse(evidence.isStrongEmulatorEvidence)
    assertTrue(evidence.buildMarkers.contains("build_tags:test-keys"))
    assertTrue(evidence.buildMarkers.contains("manufacturer:unknown"))
    assertTrue(evidence.buildMarkers.contains("abi:x86_64"))
  }

  @Test
  fun `android studio emulator reports explainable strong build evidence`() {
    val evidence = EmulatorEvidenceClassifier.classify(
      build = EmulatorBuildSnapshot(
        fingerprint = "google/sdk_gphone64_arm64/emu64a:16/BUILD/dev-keys",
        model = "sdk_gphone64_arm64",
        manufacturer = "Google",
        brand = "google",
        device = "emu64a",
        product = "sdk_gphone64_arm64",
        hardware = "ranchu",
        supportedAbis = listOf("arm64-v8a"),
      ),
      emulatorFilePaths = emptyList(),
      systemProperties = emptyMap(),
      cpuInfo = null,
    )

    assertTrue(evidence.isStrongEmulatorEvidence)
    assertTrue(evidence.buildMarkers.contains("hardware:ranchu"))
    assertTrue(evidence.buildMarkers.contains("product:sdk_gphone"))
  }

  @Test
  fun `qemu property and emulator file are retained as strong raw evidence`() {
    val evidence = EmulatorEvidenceClassifier.classify(
      build = physicalPixel(),
      emulatorFilePaths = listOf("/dev/qemu_pipe"),
      systemProperties = mapOf("ro.kernel.qemu" to "1"),
      cpuInfo = "Hardware: ranchu",
    )

    assertTrue(evidence.isStrongEmulatorEvidence)
    assertTrue(evidence.filePaths.contains("/dev/qemu_pipe"))
    assertTrue(evidence.systemPropertyMarkers.contains("ro.kernel.qemu=1"))
    assertTrue(evidence.cpuMarkers.contains("ranchu"))
  }

  @Test
  fun `known third party emulator vendors are reported explicitly`() {
    val blueStacks = EmulatorEvidenceClassifier.classify(
      build = physicalPixel().copy(manufacturer = "BlueStacks", product = "bst64"),
      emulatorFilePaths = emptyList(),
      systemProperties = emptyMap(),
      cpuInfo = null,
    )
    val nox = EmulatorEvidenceClassifier.classify(
      build = physicalPixel(),
      emulatorFilePaths = listOf("/system/bin/nox-prop"),
      systemProperties = emptyMap(),
      cpuInfo = null,
    )

    assertTrue(blueStacks.isStrongEmulatorEvidence)
    assertTrue(blueStacks.emulatorVendorMarkers.contains("bluestacks"))
    assertTrue(nox.isStrongEmulatorEvidence)
    assertTrue(nox.emulatorVendorMarkers.contains("nox"))
  }

  @Test
  fun `firebase test lab is device farm evidence and not emulator proof`() {
    val evidence = EmulatorEvidenceClassifier.classify(
      build = physicalPixel(),
      emulatorFilePaths = emptyList(),
      systemProperties = mapOf("firebase.test.lab" to "true"),
      cpuInfo = null,
    )

    assertFalse(evidence.isStrongEmulatorEvidence)
    assertTrue(evidence.deviceFarmMarkers.contains("firebase_test_lab"))
  }

  private fun physicalPixel() = EmulatorBuildSnapshot(
    fingerprint = "google/akita/akita:16/BUILD/release-keys",
    model = "Pixel 8a",
    manufacturer = "Google",
    brand = "google",
    device = "akita",
    product = "akita",
    hardware = "akita",
    supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
  )
}
