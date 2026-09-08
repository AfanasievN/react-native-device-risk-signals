package io.github.afanasievn.devicerisksignals

/** Opportunistic telephony observations; unavailable fields are omitted. No device identifiers. */
data class TelephonySignals(
  val phoneType: String? = null,
  val networkOperatorName: String? = null,
  val simOperatorName: String? = null,
  val networkCountryIso: String? = null,
  val simCountryIso: String? = null,
  val simState: String? = null,
  val dataState: String? = null,
  val hasIccCard: Boolean? = null,
  val isNetworkRoaming: Boolean? = null,
  val simCount: Int? = null,
) {
  fun toRawMap(): Map<String, Any> = buildMap {
    phoneType?.let { put("phoneType", it) }
    networkOperatorName?.let { put("networkOperatorName", it) }
    simOperatorName?.let { put("simOperatorName", it) }
    networkCountryIso?.let { put("networkCountryIso", it) }
    simCountryIso?.let { put("simCountryIso", it) }
    simState?.let { put("simState", it) }
    dataState?.let { put("dataState", it) }
    hasIccCard?.let { put("hasIccCard", it) }
    isNetworkRoaming?.let { put("isNetworkRoaming", it) }
    simCount?.let { put("simCount", it) }
  }
}
