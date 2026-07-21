import NativeDeviceIntel, {type RuntimeTimingSignals} from "../NativeDeviceIntel";
import {summarize} from "./signalStatistics";
import type {Probe} from "./types";

const TIMER_SAMPLES = 128;
const EVENT_LOOP_SAMPLES = 8;

type TimingGlobals = {
  performance?: {now?: () => number};
};

function clock(): {source: string; now: () => number} {
  const performance = (globalThis as unknown as TimingGlobals).performance;
  if (typeof performance?.now === "function") {
    return {source: "performance_now", now: () => performance.now?.() ?? Date.now()};
  }
  return {source: "date_now", now: () => Date.now()};
}

function timerResolution(now: () => number): {sampleCount: number; resolution: number} {
  const positive: number[] = [];
  let previous = now();
  for (let index = 0; index < TIMER_SAMPLES; index++) {
    const current = now();
    const delta = current - previous;
    if (delta > 0) positive.push(delta);
    previous = current;
  }
  return {sampleCount: TIMER_SAMPLES, resolution: positive.length > 0 ? Math.min(...positive) : 0};
}

async function eventLoopDelays(now: () => number): Promise<number[]> {
  const samples: number[] = [];
  for (let index = 0; index < EVENT_LOOP_SAMPLES; index++) {
    const start = now();
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    samples.push(now() - start);
  }
  return samples;
}

async function collectRuntimeTiming(): Promise<RuntimeTimingSignals> {
  const jsClock = clock();
  const resolution = timerResolution(jsClock.now);
  const eventLoop = summarize(await eventLoopDelays(jsClock.now));
  const bridgeStart = jsClock.now();
  const native = await NativeDeviceIntel.getRuntimeTimingSignals();
  const bridgeRoundTripMs = jsClock.now() - bridgeStart;
  return {
    ...native,
    jsClockSource: jsClock.source,
    jsTimerSampleCount: resolution.sampleCount,
    jsTimerResolutionMs: resolution.resolution,
    eventLoopSampleCount: eventLoop?.sampleCount ?? 0,
    eventLoopP50Ms: eventLoop?.median ?? 0,
    eventLoopP95Ms: eventLoop?.p95 ?? 0,
    eventLoopMadMs: eventLoop?.mad ?? 0,
    bridgeRoundTripMs,
  };
}

export const runtimeTimingProbes: Probe[] = [
  {
    id: "runtime_timing",
    timeoutMs: 1000,
    enabled: () => false,
    collect: collectRuntimeTiming,
  } satisfies Probe<RuntimeTimingSignals>,
];
