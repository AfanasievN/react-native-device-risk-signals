import {consentFor} from "./config/consent";
import {DeviceIntel, type RawSignalEvent} from "./DeviceIntel";
import type {Probe} from "./probes/types";
import {assertDefined} from "./testing/assertDefined";

const fakeProbe: Probe = {
  id: "fake_probe",
  timeoutMs: 100,
  enabled: () => true,
  collect: () => Promise.resolve({value: 42}),
};

// The mock factory is hoisted above the `let` below, but the getter defers the read until collect()
// runs (well after mockAllProbes is initialized), so the reference is safe. `let` (not const) is
// intentional — beforeEach reassigns it to reset per-test state.
jest.mock("./probes", () => ({
  get allProbes() {
    return mockAllProbes;
  },
}));

let mockAllProbes: Probe[] = [fakeProbe];

let mockSessionCounter = 0;
const mockGetRandomSessionId = jest.fn(
  () => `00000000-0000-4000-8000-${(++mockSessionCounter).toString().padStart(12, "0")}`,
);
jest.mock("./NativeDeviceIntel", () => ({
  __esModule: true,
  default: {
    get getRandomSessionId() {
      return mockGetRandomSessionId;
    },
  },
}));

const mockSend = jest.fn().mockResolvedValue({ok: true, status: 200});
jest.mock("./transport/transport", () => ({
  Transport: jest.fn().mockImplementation(() => ({send: mockSend})),
}));

describe("DeviceIntel.collect", () => {
  beforeEach(() => {
    mockAllProbes = [fakeProbe];
  });

  it("assembles a RawSignalEvent with the expected top-level shape", async () => {
    const deviceIntel = new DeviceIntel();
    const event = await deviceIntel.collect();

    expect(event.event_type).toBe("device_intel_collection");
    expect(event.schema_version).toBe(1);
    expect(typeof event.session_id).toBe("string");
    expect(typeof event.collected_at).toBe("string");
    expect(event.probes.fake_probe).toEqual({status: "success", data: {value: 42}});
  });

  it("resetSession() changes session_id between two collect() calls", async () => {
    const deviceIntel = new DeviceIntel();
    const first = await deviceIntel.collect();
    deviceIntel.resetSession();
    const second = await deviceIntel.collect();

    expect(second.session_id).not.toBe(first.session_id);
  });

  it("does not change session_id across collect() calls without an explicit resetSession()", async () => {
    const deviceIntel = new DeviceIntel();
    const first = await deviceIntel.collect();
    const second = await deviceIntel.collect();

    expect(second.session_id).toBe(first.session_id);
  });

  it("setConfig() changes which probes run on the next collect()", async () => {
    const deviceIntel = new DeviceIntel();

    const before = await deviceIntel.collect();
    expect(before.probes.fake_probe).toEqual({status: "success", data: {value: 42}});

    deviceIntel.setConfig({probes: {fake_probe: {enabled: false}}});

    const after = await deviceIntel.collect();
    expect(after.probes.fake_probe).toEqual({status: "skipped", reason: "disabled"});
  });
});

describe("DeviceIntel session id", () => {
  beforeEach(() => {
    mockAllProbes = [fakeProbe];
    mockSessionCounter = 0;
    mockGetRandomSessionId.mockClear();
  });

  it("stamps the app-supplied session id from the constructor onto the event", async () => {
    const deviceIntel = new DeviceIntel({sessionId: "app-session-1"});
    expect((await deviceIntel.collect()).session_id).toBe("app-session-1");
  });

  it("setSessionId() replaces the id for subsequent collects", async () => {
    const deviceIntel = new DeviceIntel();
    deviceIntel.setSessionId("app-session-2");
    expect((await deviceIntel.collect()).session_id).toBe("app-session-2");
  });

  it("collect({sessionId}) overrides for one call without mutating the instance", async () => {
    const deviceIntel = new DeviceIntel({sessionId: "instance-session"});
    expect((await deviceIntel.collect({sessionId: "one-off"})).session_id).toBe("one-off");
    expect((await deviceIntel.collect()).session_id).toBe("instance-session");
  });

  it("falls back to an SDK-generated ephemeral id (di_ prefix) when the app supplies none", async () => {
    const deviceIntel = new DeviceIntel();
    expect((await deviceIntel.collect()).session_id).toMatch(/^di_/);
  });

  it("uses native secure randomness instead of Math.random for generated session ids", async () => {
    const insecureRandom = jest.spyOn(Math, "random").mockImplementation(() => {
      throw new Error("Math.random must not generate security-sensitive session ids");
    });

    try {
      const deviceIntel = new DeviceIntel();
      const first = await deviceIntel.collect();
      deviceIntel.resetSession();
      const second = await deviceIntel.collect();

      expect(first.session_id).toBe("di_00000000-0000-4000-8000-000000000001");
      expect(second.session_id).toBe("di_00000000-0000-4000-8000-000000000002");
      expect(mockGetRandomSessionId).toHaveBeenCalledTimes(2);
    } finally {
      insecureRandom.mockRestore();
    }
  });
});

describe("DeviceIntel client id", () => {
  beforeEach(() => {
    mockAllProbes = [fakeProbe];
  });

  it("omits client_id entirely for an unauthenticated collection", async () => {
    const event = await new DeviceIntel().collect();
    expect("client_id" in event).toBe(false);
  });

  it("stamps the app-supplied client id from the constructor", async () => {
    const deviceIntel = new DeviceIntel({clientId: "user-123"});
    expect((await deviceIntel.collect()).client_id).toBe("user-123");
  });

  it("setClientId() sets it on login; clearing it (undefined) omits it again", async () => {
    const deviceIntel = new DeviceIntel();
    deviceIntel.setClientId("user-123");
    expect((await deviceIntel.collect()).client_id).toBe("user-123");
    deviceIntel.setClientId(undefined);
    expect("client_id" in (await deviceIntel.collect())).toBe(false);
  });

  it("collect({clientId}) overrides for one call without mutating the instance", async () => {
    const deviceIntel = new DeviceIntel({clientId: "instance-user"});
    expect((await deviceIntel.collect({clientId: "one-off"})).client_id).toBe("one-off");
    expect((await deviceIntel.collect()).client_id).toBe("instance-user");
  });
});

describe("DeviceIntel per-call config + field projection", () => {
  beforeEach(() => {
    mockAllProbes = [fakeProbe];
  });

  it("a per-call config disables a probe for that collect() only, leaving the instance config intact", async () => {
    const deviceIntel = new DeviceIntel();

    const scoped = await deviceIntel.collect({config: {probes: {fake_probe: {enabled: false}}}});
    expect(scoped.probes.fake_probe).toEqual({status: "skipped", reason: "disabled"});

    const next = await deviceIntel.collect();
    expect(next.probes.fake_probe).toEqual({status: "success", data: {value: 42}});
  });

  it("field projection (exclude) trims the collected + sent payload", async () => {
    mockAllProbes = [
      {
        id: "rich",
        timeoutMs: 100,
        enabled: () => true,
        collect: () => Promise.resolve({keepMe: 1, dropMe: 2}),
      },
    ];
    const deviceIntel = new DeviceIntel();
    const event = await deviceIntel.collect({config: {probes: {rich: {fields: {exclude: ["dropMe"]}}}}});
    expect(event.probes.rich).toEqual({status: "success", data: {keepMe: 1}});
  });

  it("field projection (include) keeps only the listed fields", async () => {
    mockAllProbes = [
      {
        id: "rich",
        timeoutMs: 100,
        enabled: () => true,
        collect: () => Promise.resolve({a: 1, b: 2, c: 3}),
      },
    ];
    const deviceIntel = new DeviceIntel();
    const event = await deviceIntel.collect({config: {probes: {rich: {fields: {include: ["a", "c"]}}}}});
    expect(event.probes.rich).toEqual({status: "success", data: {a: 1, c: 3}});
  });
});

describe("DeviceIntel consent gating", () => {
  beforeEach(() => {
    mockAllProbes = [fakeProbe];
  });

  it("setConsent force-disables a non-consented probe on every subsequent collect()", async () => {
    const deviceIntel = new DeviceIntel();
    deviceIntel.setConsent(consentFor([])); // consent to nothing
    const event = await deviceIntel.collect();
    expect(event.probes.fake_probe).toEqual({status: "skipped", reason: "disabled"});
  });

  it("per-call consent is ANDed — it can further restrict a single collect()", async () => {
    const deviceIntel = new DeviceIntel();
    const event = await deviceIntel.collect({consent: consentFor([])});
    expect(event.probes.fake_probe).toEqual({status: "skipped", reason: "disabled"});
    // instance consent unchanged — next call (no per-call consent) collects normally
    const next = await deviceIntel.collect();
    expect(next.probes.fake_probe).toEqual({status: "success", data: {value: 42}});
  });

  it("consent cannot re-enable what remote config disabled (config wins downward, consent only subtracts)", async () => {
    const deviceIntel = new DeviceIntel({config: {probes: {fake_probe: {enabled: false}}}});
    const event = await deviceIntel.collect({consent: consentFor(["fake_probe"])});
    expect(event.probes.fake_probe).toEqual({status: "skipped", reason: "disabled"});
  });
});

describe("DeviceIntel.collectAndSend", () => {
  beforeEach(() => {
    mockAllProbes = [fakeProbe];
    mockSend.mockClear();
  });

  it("sends the collected event and reports whether it was accepted", async () => {
    const deviceIntel = new DeviceIntel({transport: {baseUrl: "https://api.example.test"}});
    const {event, sent} = await deviceIntel.collectAndSend();

    expect(sent).toBe(true);
    expect(event.probes.fake_probe).toEqual({status: "success", data: {value: 42}});
  });

  it("sendFields transmits a narrower payload than what collect() returns", async () => {
    mockAllProbes = [
      {id: "rich", timeoutMs: 100, enabled: () => true, collect: () => Promise.resolve({a: 1, b: 2, c: 3})},
    ];
    const deviceIntel = new DeviceIntel({
      transport: {baseUrl: "https://api.example.test"},
      config: {probes: {rich: {sendFields: {include: ["a"]}}}},
    });

    const {event} = await deviceIntel.collectAndSend();
    // The returned (collected) view keeps everything...
    expect(event.probes.rich).toEqual({status: "success", data: {a: 1, b: 2, c: 3}});
    // ...but the transmitted payload is narrowed by sendFields.
    const [, sentPayload] = assertDefined(mockSend.mock.calls[0]);
    expect((sentPayload as RawSignalEvent).probes.rich).toEqual({status: "success", data: {a: 1}});
  });
});
