package io.github.afanasievn.devicerisksignals.active

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the component's DECLARED default to the shared probe catalog.
 *
 * `DeviceRiskActiveProbes` has no probe registry of its own, so its default state is a declaration
 * rather than a switch. Without this test that declaration could drift from
 * `contract/source/probe-catalog.source.json` — the source the React Native package generates from
 * — and a native host and a React Native host would then ship different defaults while both
 * documents claimed otherwise. Changing the catalog entry must fail here.
 *
 * The catalog is read as text on purpose: this module deliberately carries no JSON dependency (see
 * `build.gradle.kts`), and the pin only needs one boolean.
 */
class DeviceRiskActiveProbesDefaultsTest {

  @Test fun theDeclaredDefaultMatchesTheSharedProbeCatalog() {
    val catalog = catalogSource().readText()
    val descriptor = descriptorFor(DeviceRiskActiveProbes.FRIDA_SCAN_PROBE_ID, catalog)

    val declared = ENABLED_BY_DEFAULT.find(descriptor)?.groupValues?.get(1)
    assertNotNull(
      "no `enabledByDefault` on ${DeviceRiskActiveProbes.FRIDA_SCAN_PROBE_ID} in the catalog source",
      declared,
    )
    assertEquals(
      "the component's declared default no longer matches the shared probe catalog",
      declared,
      DeviceRiskActiveProbes.FRIDA_SCAN_ENABLED_BY_DEFAULT.toString(),
    )
  }

  @Test fun theDeclaredDefaultIsEnabled() {
    // Deliberately spelled out rather than derived: this component ships the probe ON, as an
    // explicit exception to the "new sensitive probes ship disabled" rule, because the probe is not
    // new. Flipping it is a product decision and must break this test.
    assertTrue(DeviceRiskActiveProbes.FRIDA_SCAN_ENABLED_BY_DEFAULT)
  }

  /** The descriptor object for [probeId]: from its id to the start of the next probe's id. */
  private fun descriptorFor(probeId: String, catalog: String): String {
    val start = catalog.indexOf("\"$probeId\"")
    assertTrue("probe $probeId is missing from the catalog source", start >= 0)
    val next = catalog.indexOf("\"id\":", start)
    return if (next >= 0) catalog.substring(start, next) else catalog.substring(start)
  }

  private fun catalogSource(): File {
    var directory: File? = File(".").absoluteFile
    while (directory != null) {
      val candidate = File(directory, CATALOG_SOURCE_PATH)
      if (candidate.isFile) return candidate
      directory = directory.parentFile
    }
    throw AssertionError("could not find $CATALOG_SOURCE_PATH above ${File(".").absolutePath}")
  }

  private companion object {
    const val CATALOG_SOURCE_PATH = "contract/source/probe-catalog.source.json"
    val ENABLED_BY_DEFAULT = Regex("\"enabledByDefault\"\\s*:\\s*(true|false)")
  }
}
