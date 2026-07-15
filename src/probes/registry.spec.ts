import {assertDefined} from "../testing/assertDefined";
import {collectAll} from "./registry";
import type {Probe} from "./types";

function fakeProbe(overrides: Partial<Probe> = {}): Probe {
  return {
    id: "probe",
    timeoutMs: 50,
    enabled: () => true,
    collect: () => Promise.resolve("data"),
    ...overrides,
  };
}

describe("collectAll", () => {
  it("returns a success outcome for a probe that resolves", async () => {
    const result = assertDefined((await collectAll([fakeProbe({collect: () => Promise.resolve("ok")})]))[0]);
    expect(result.outcome).toEqual({status: "success", data: "ok"});
  });

  it("isolates a throwing probe — does not fail collectAll and does not affect other probes", async () => {
    const throwing = fakeProbe({id: "throwing", collect: () => Promise.reject(new Error("boom"))});
    const healthy = fakeProbe({id: "healthy", collect: () => Promise.resolve("fine")});

    const results = await collectAll([throwing, healthy]);

    const throwingResult = results.find((r) => r.id === "throwing");
    const healthyResult = results.find((r) => r.id === "healthy");
    expect(throwingResult?.outcome).toEqual({status: "error", error: "boom"});
    expect(healthyResult?.outcome).toEqual({status: "success", data: "fine"});
  });

  it("times out a probe that never resolves, without blocking other probes", async () => {
    const hanging = fakeProbe({
      id: "hanging",
      timeoutMs: 20,
      collect: () => new Promise(() => {}), // never resolves
    });
    const fast = fakeProbe({id: "fast", timeoutMs: 200, collect: () => Promise.resolve("fast-data")});

    const results = await collectAll([hanging, fast]);

    const hangingResult = results.find((r) => r.id === "hanging");
    const fastResult = results.find((r) => r.id === "fast");
    expect(hangingResult?.outcome).toEqual({status: "timeout"});
    expect(fastResult?.outcome).toEqual({status: "success", data: "fast-data"});
  });

  it("short-circuits a disabled probe to a skipped outcome and never calls collect()", async () => {
    const collectSpy = jest.fn(() => Promise.resolve("should not be called"));
    const disabled = fakeProbe({id: "disabled", enabled: () => false, collect: collectSpy});

    const result = assertDefined((await collectAll([disabled]))[0]);

    expect(result.outcome).toEqual({status: "skipped", reason: "disabled"});
    expect(result.durationMs).toBe(0);
    expect(collectSpy).not.toHaveBeenCalled();
  });

  it("never throws out of collectAll itself even when every probe fails", async () => {
    const allFail = [
      fakeProbe({id: "a", collect: () => Promise.reject(new Error("a-fail"))}),
      fakeProbe({id: "b", collect: () => Promise.reject(new Error("b-fail"))}),
    ];
    await expect(collectAll(allFail)).resolves.toHaveLength(2);
  });
});
