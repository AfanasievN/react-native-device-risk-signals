import {Platform} from "react-native";

import {assertDefined} from "../testing/assertDefined";
import {androidOnly, iosOnly} from "./platformProbe";
import type {Probe} from "./types";

jest.mock("react-native", () => ({
  Platform: {OS: "android"},
}));

const MockedPlatform = Platform as jest.Mocked<typeof Platform>;

function fakeProbe(id: string): Probe {
  return {id, timeoutMs: 100, enabled: () => true, collect: () => Promise.resolve(undefined)};
}

describe("iosOnly", () => {
  it("keeps probes enabled on iOS", () => {
    MockedPlatform.OS = "ios";
    const probe = assertDefined(iosOnly([fakeProbe("ios_only_probe")])[0]);
    expect(probe.enabled()).toBe(true);
  });

  it("force-disables probes on Android", () => {
    MockedPlatform.OS = "android";
    const probe = assertDefined(iosOnly([fakeProbe("ios_only_probe")])[0]);
    expect(probe.enabled()).toBe(false);
  });

  it("does not mutate the original probe's identity fields", () => {
    MockedPlatform.OS = "android";
    const probe = assertDefined(iosOnly([fakeProbe("ios_only_probe")])[0]);
    expect(probe.id).toBe("ios_only_probe");
    expect(probe.timeoutMs).toBe(100);
  });
});

describe("androidOnly", () => {
  it("keeps probes enabled on Android", () => {
    MockedPlatform.OS = "android";
    const probe = assertDefined(androidOnly([fakeProbe("android_only_probe")])[0]);
    expect(probe.enabled()).toBe(true);
  });

  it("force-disables probes on iOS", () => {
    MockedPlatform.OS = "ios";
    const probe = assertDefined(androidOnly([fakeProbe("android_only_probe")])[0]);
    expect(probe.enabled()).toBe(false);
  });
});
