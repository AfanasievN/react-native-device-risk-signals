import type {RawSignalEvent} from "./DeviceIntel";

export type ConsistencyExpectations = {
  claimedCountry?: string;
  expectedTimezoneId?: string;
  expectedBundleId?: string;
  expectedAppVersion?: string;
};

export type ConsistencySignals = {
  localeCountryMatchesClaimed?: boolean;
  networkCountryMatchesClaimed?: boolean;
  simCountryMatchesClaimed?: boolean;
  timezoneMatchesExpected?: boolean;
  bundleIdMatchesExpected?: boolean;
  appVersionMatchesExpected?: boolean;
  localeMatchesNetworkCountry?: boolean;
  localeMatchesSimCountry?: boolean;
  networkMatchesSimCountry?: boolean;
};

type SignalData = Record<string, unknown>;

function successfulData(event: RawSignalEvent, probeId: string): SignalData | undefined {
  const outcome = event.probes[probeId];
  if (!outcome || outcome.status !== "success" || typeof outcome.data !== "object" || outcome.data === null) {
    return undefined;
  }
  return outcome.data as SignalData;
}

function stringField(data: SignalData | undefined, field: string): string | undefined {
  const value = data?.[field];
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function country(value: string | undefined): string | undefined {
  return value?.trim().toUpperCase() || undefined;
}

function compare(out: ConsistencySignals, key: keyof ConsistencySignals, left?: string, right?: string): void {
  if (left !== undefined && right !== undefined) out[key] = left === right;
}

/** Raw equality observations only: no weights, score, or fraud verdict. */
export function deriveConsistencySignals(
  event: RawSignalEvent,
  expectations: ConsistencyExpectations = {},
): ConsistencySignals {
  const locale = successfulData(event, "locale");
  const telephony = successfulData(event, "telephony");
  const application = successfulData(event, "application");
  const localeCountry = country(stringField(locale, "country"));
  const networkCountry = country(stringField(telephony, "networkCountryIso"));
  const simCountry = country(stringField(telephony, "simCountryIso"));
  const claimedCountry = country(expectations.claimedCountry);
  const result: ConsistencySignals = {};

  compare(result, "localeCountryMatchesClaimed", localeCountry, claimedCountry);
  compare(result, "networkCountryMatchesClaimed", networkCountry, claimedCountry);
  compare(result, "simCountryMatchesClaimed", simCountry, claimedCountry);
  compare(result, "timezoneMatchesExpected", stringField(locale, "timezoneId"), expectations.expectedTimezoneId?.trim());
  compare(result, "bundleIdMatchesExpected", stringField(application, "bundleId"), expectations.expectedBundleId?.trim());
  compare(result, "appVersionMatchesExpected", stringField(application, "appVersion"), expectations.expectedAppVersion?.trim());
  compare(result, "localeMatchesNetworkCountry", localeCountry, networkCountry);
  compare(result, "localeMatchesSimCountry", localeCountry, simCountry);
  compare(result, "networkMatchesSimCountry", networkCountry, simCountry);
  return result;
}
