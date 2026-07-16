import {createRequire} from "node:module";
import os from "node:os";
import {performance} from "node:perf_hooks";

const require = createRequire(import.meta.url);
const {collectAll} = require("../lib/probes/registry.js");
const iterations = Number.parseInt(process.argv[2] ?? "5000", 10);

if (!Number.isFinite(iterations) || iterations <= 0) {
  throw new Error("Iterations must be a positive integer.");
}

const probes = Array.from({length: 15}, (_, index) => ({
  id: `probe_${index}`,
  timeoutMs: 1000,
  enabled: () => true,
  collect: () => Promise.resolve({value: index}),
}));

for (let index = 0; index < 250; index += 1) await collectAll(probes);

const samples = [];
for (let index = 0; index < iterations; index += 1) {
  const startedAt = performance.now();
  await collectAll(probes);
  samples.push(performance.now() - startedAt);
}

samples.sort((left, right) => left - right);
const percentile = (value) => samples[Math.min(samples.length - 1, Math.floor(samples.length * value))];
const mean = samples.reduce((sum, value) => sum + value, 0) / samples.length;

console.log(
  JSON.stringify(
    {
      scope: "JavaScript probe orchestration only; native provider work is mocked",
      probesPerCollection: probes.length,
      iterations,
      meanMs: Number(mean.toFixed(4)),
      p50Ms: Number(percentile(0.5).toFixed(4)),
      p95Ms: Number(percentile(0.95).toFixed(4)),
      node: process.version,
      platform: `${process.platform}-${process.arch}`,
      cpu: os.cpus()[0]?.model,
    },
    null,
    2,
  ),
);
