import {deriveConsistencySignals} from "./consistencySignals";
import type {RawSignalEvent} from "./DeviceIntel";

function event(probes: RawSignalEvent["probes"]): RawSignalEvent {
  return {
    session_id: "session",
    event_type: "device_intel_collection",
    schema_version: 1,
    collected_at: "2026-07-16T00:00:00.000Z",
    probes,
  };
}

describe("deriveConsistencySignals", () => {
  it("normalizes country and app values before comparing them", () => {
    const result = deriveConsistencySignals(
      event({
        locale: {status: "success", data: {country: "us", timezoneId: "America/New_York"}},
        telephony: {status: "success", data: {networkCountryIso: "US", simCountryIso: "us"}},
        application: {status: "success", data: {bundleId: "com.example.app", appVersion: "2.0.0"}},
      }),
      {
        claimedCountry: " US ",
        expectedTimezoneId: "America/New_York",
        expectedBundleId: "com.example.app",
        expectedAppVersion: "2.0.0",
      },
    );

    expect(result).toEqual({
      localeCountryMatchesClaimed: true,
      networkCountryMatchesClaimed: true,
      simCountryMatchesClaimed: true,
      timezoneMatchesExpected: true,
      bundleIdMatchesExpected: true,
      appVersionMatchesExpected: true,
      localeMatchesNetworkCountry: true,
      localeMatchesSimCountry: true,
      networkMatchesSimCountry: true,
    });
  });

  it("omits comparisons when either observation is unavailable", () => {
    const result = deriveConsistencySignals(
      event({
        locale: {status: "timeout"},
        telephony: {status: "success", data: {networkCountryIso: "GB"}},
        application: {status: "error", error: "unavailable"},
      }),
      {claimedCountry: "GB", expectedBundleId: "com.example.app"},
    );

    expect(result).toEqual({networkCountryMatchesClaimed: true});
  });

  it("reports disagreement as raw booleans without producing a risk verdict", () => {
    const result = deriveConsistencySignals(
      event({
        locale: {status: "success", data: {country: "DE"}},
        telephony: {status: "success", data: {simCountryIso: "FR"}},
      }),
      {claimedCountry: "NL"},
    );

    expect(result).toEqual({
      localeCountryMatchesClaimed: false,
      simCountryMatchesClaimed: false,
      localeMatchesSimCountry: false,
    });
    expect(result).not.toHaveProperty("riskScore");
    expect(result).not.toHaveProperty("isFraud");
  });
});
