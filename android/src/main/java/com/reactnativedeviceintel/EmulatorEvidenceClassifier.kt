package com.reactnativedeviceintel

internal data class EmulatorBuildSnapshot(
  val fingerprint: String,
  val model: String,
  val manufacturer: String,
  val brand: String,
  val device: String,
  val product: String,
  val hardware: String,
  val supportedAbis: List<String>,
)

internal data class EmulatorEvidence(
  val buildMarkers: List<String>,
  val filePaths: List<String>,
  val systemPropertyMarkers: List<String>,
  val cpuMarkers: List<String>,
  val emulatorVendorMarkers: List<String>,
  val deviceFarmMarkers: List<String>,
  val hasStrongBuildEvidence: Boolean,
  val isStrongEmulatorEvidence: Boolean,
)

/**
 * Classifies already collected Android observations without assigning a risk score.
 *
 * Broad signals such as test-keys, an unknown manufacturer, or an x86 ABI are retained for a
 * caller to correlate, but never flip [EmulatorEvidence.isStrongEmulatorEvidence] by themselves.
 */
internal object EmulatorEvidenceClassifier {
  fun classify(
    build: EmulatorBuildSnapshot,
    emulatorFilePaths: List<String>,
    systemProperties: Map<String, String>,
    cpuInfo: String?,
  ): EmulatorEvidence {
    val buildMarkers = LinkedHashSet<String>()
    var hasStrongBuildEvidence = false

    fun addBuildMarker(marker: String, strong: Boolean = true) {
      buildMarkers.add(marker)
      if (strong) hasStrongBuildEvidence = true
    }

    val fingerprint = build.fingerprint.lowercase()
    when {
      fingerprint.contains("generic") -> addBuildMarker("fingerprint:generic")
      fingerprint.contains("emulator") -> addBuildMarker("fingerprint:emulator")
      fingerprint.contains("vbox") -> addBuildMarker("fingerprint:vbox")
    }
    if (fingerprint.contains("test-keys")) addBuildMarker("build_tags:test-keys", strong = false)

    val model = build.model.lowercase()
    when {
      model.contains("google_sdk") -> addBuildMarker("model:google_sdk")
      model.contains("android sdk built for") -> addBuildMarker("model:android_sdk_built_for")
      model.contains("emulator") -> addBuildMarker("model:emulator")
    }

    val manufacturer = build.manufacturer.lowercase()
    if (manufacturer.contains("genymotion")) addBuildMarker("manufacturer:genymotion")
    if (manufacturer == "unknown") addBuildMarker("manufacturer:unknown", strong = false)

    val product = build.product.lowercase()
    when {
      product.contains("sdk_gphone") -> addBuildMarker("product:sdk_gphone")
      product.contains("vbox") -> addBuildMarker("product:vbox")
      product.contains("emulator") -> addBuildMarker("product:emulator")
    }

    val hardware = build.hardware.lowercase()
    STRONG_HARDWARE_TOKENS.firstOrNull { hardware.contains(it) }
      ?.let { addBuildMarker("hardware:$it") }

    if (build.brand.startsWith("generic", ignoreCase = true) &&
      build.device.startsWith("generic", ignoreCase = true)
    ) {
      addBuildMarker("brand_device:generic")
    }

    build.supportedAbis
      .map { it.lowercase() }
      .filter { it == "x86" || it == "x86_64" }
      .forEach { addBuildMarker("abi:$it", strong = false) }

    val propertyMarkers = LinkedHashSet<String>()
    systemProperties.forEach { (key, value) ->
      val normalizedValue = value.lowercase()
      val isQemuFlag = key in QEMU_FLAG_PROPERTIES && value == "1"
      val isEmulatorHardware = key in HARDWARE_PROPERTIES &&
        STRONG_HARDWARE_TOKENS.any { normalizedValue.contains(it) }
      if (isQemuFlag || isEmulatorHardware) propertyMarkers.add("$key=$value")
    }

    val normalizedCpuInfo = cpuInfo?.lowercase().orEmpty()
    val cpuMarkers = CPU_TOKENS.filter { normalizedCpuInfo.contains(it) }
    val files = emulatorFilePaths.distinct()
    val searchableBuildValues = listOf(
      build.fingerprint,
      build.model,
      build.manufacturer,
      build.brand,
      build.device,
      build.product,
      build.hardware,
    ).map { it.lowercase() }
    val searchableProperties = systemProperties.entries.map { "${it.key}=${it.value}".lowercase() }
    val searchableFiles = files.map { it.lowercase() }
    val vendorMarkers = EMULATOR_VENDOR_SIGNATURES.mapNotNull { (vendor, signatures) ->
      val matched = signatures.any { signature ->
        searchableBuildValues.any { it.contains(signature) } ||
          searchableProperties.any { it.contains(signature) } ||
          searchableFiles.any { it.contains(signature) }
      }
      vendor.takeIf { matched }
    }
    val deviceFarmMarkers = buildList {
      if (systemProperties["firebase.test.lab"].isTruthy()) add("firebase_test_lab")
      if (systemProperties["ro.boot.test_harness"].isTruthy()) add("android_test_harness")
    }
    val strong = hasStrongBuildEvidence || files.isNotEmpty() ||
      propertyMarkers.isNotEmpty() || cpuMarkers.isNotEmpty() || vendorMarkers.isNotEmpty()

    return EmulatorEvidence(
      buildMarkers = buildMarkers.toList(),
      filePaths = files,
      systemPropertyMarkers = propertyMarkers.toList(),
      cpuMarkers = cpuMarkers,
      emulatorVendorMarkers = vendorMarkers,
      deviceFarmMarkers = deviceFarmMarkers,
      hasStrongBuildEvidence = hasStrongBuildEvidence,
      isStrongEmulatorEvidence = strong,
    )
  }

  private val STRONG_HARDWARE_TOKENS = listOf("goldfish", "ranchu", "vbox", "cuttlefish")
  private val CPU_TOKENS = listOf("qemu", "ranchu", "goldfish", "virtualbox", "vbox", "cuttlefish")
  private val QEMU_FLAG_PROPERTIES = setOf("ro.kernel.qemu", "ro.boot.qemu")
  private val HARDWARE_PROPERTIES = setOf("ro.hardware", "ro.boot.hardware")
  private val EMULATOR_VENDOR_SIGNATURES = linkedMapOf(
    "genymotion" to listOf("genymotion", "genyd"),
    "bluestacks" to listOf("bluestacks", "bstfolder", "bst64", "bst32"),
    "nox" to listOf("nox-prop", "nox-vbox", "ro.nox."),
    "memu" to listOf("microvirt", "memud", "ro.microvirt."),
    "ldplayer" to listOf("ldplayer", "ldinit", "ldmountsf", "ro.ld.player"),
    "andy" to listOf("andyroid", "androvm"),
    "droid4x" to listOf("droid4x"),
    "koplayer" to listOf("koplayer"),
  )

  private fun String?.isTruthy(): Boolean = this == "1" || this.equals("true", ignoreCase = true)
}
