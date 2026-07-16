# Benchmarks

Benchmarks are separated into JavaScript orchestration cost and native device collection cost so a
fast framework result is never presented as proof that every native probe is fast.

## JavaScript probe runner

`npm run benchmark:runner` builds the package and runs 5,000 collections containing 15 successful,
immediately resolving synthetic probes. Native provider work is intentionally mocked. The benchmark
measures probe scheduling, timeout creation/cleanup, outcome assembly, and `Promise` coordination.

Baseline recorded on 2026-07-16:

| Environment | Iterations | Probes per collection | Mean | p50 | p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Node 24.16.0, macOS arm64, Apple M2 Pro | 5,000 | 15 | 0.0052 ms | 0.0042 ms | 0.0066 ms |

These numbers are a development regression baseline, not a mobile-device performance claim. Compare
results only on similar hardware and runtime versions.

## On-device collection benchmark

Signal Bench displays the end-to-end duration of every `collect()` call as `N ms total`. Use release
builds on physical devices; debug, simulator, and emulator measurements are useful for diagnostics but
not representative of production.

For a publishable device result:

1. Record device model, OS version, React Native version, architecture, SDK version, build type, and
   enabled probe configuration.
2. Run five warm-up collections.
3. Run at least 30 measured collections without changing permissions or connectivity.
4. Report median and p95 duration, JSON byte size, timeout/error counts, and enabled probes.
5. Test Android and iOS separately and distinguish physical devices from emulator/simulator results.
6. Measure higher-risk disabled probes in a separate cohort; never blend them into the default profile.

No physical-device baseline is checked in yet because the current environment has no connected test
device. This explicit status prevents fabricated or simulator-only numbers from being presented as
production evidence. Contributions with reproducible physical-device results are welcome.
