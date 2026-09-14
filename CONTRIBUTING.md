# Contributing

Thank you for helping improve React Native Device Risk Signals.

The repository is gradually becoming the platform-neutral Device Risk Signals monorepo. Read the
[ecosystem architecture](docs/ECOSYSTEM_ARCHITECTURE.md) before moving native code or adding a new
binding. Native SDKs must not depend on framework bindings, and bindings should not duplicate signal
logic.

The architecture document is normative. Package-boundary, dependency-direction, naming, or release
model changes also require an ADR under `docs/adr/` and an update to `device-risk-signals.json`.

By participating, you agree to follow the project [Code of Conduct](CODE_OF_CONDUCT.md).

## Before opening a change

- Keep signal collection transparent, minimal, and permission-conscious.
- Do not add company-specific endpoints, identifiers, credentials, datasets, or application names.
- Document new platform permissions and privacy implications.
- Add tests for new behavior and platform-specific fallbacks.
- Keep risk verdicts and scoring logic out of the client library.
- Contract work: `npm run verify:catalog-source` checks that `src/probeCatalog.ts` still matches the
  authored source, `npm run verify:signal-types` checks that `src/NativeDeviceIntel.ts` has not
  drifted from `contract/source/signal-types.source.json`, and `npm run verify:fixtures` validates
  `contract/fixtures/` and the published example payloads against the schema and catalog. All three
  run inside `npm run verify`.
- After changing a signal type in TypeScript, run `node scripts/generate-signal-types.mjs --write` to
  re-derive the neutral source, then `npm run docs:sync`.
- Author probe metadata in `contract/source/probe-catalog.source.json` and regenerate with
  `node scripts/generate-probe-catalog.mjs --write`; `src/probeCatalog.ts` is generated and
  `npm run verify` fails when it drifts. Keep `docs/DATA_DICTIONARY.md` synchronized with probe behavior.
- Run `npm run docs:sync` when the public contract changes; generated files in `contract/` and
  `website/` must remain identical.
- Update GitHub Pages in the same pull request when changing a component name/status, install
  coordinate, compatibility promise, permission, contract field, or release process.
- Keep transport, authentication, retries, and backend-specific serialization in the host app.

Open an issue before introducing a new sensitive signal category or a breaking public API change.

## Good first contributions

- Pick a scoped task from the
  [`good first issue` list](https://github.com/AfanasievN/react-native-device-risk-signals/issues?q=is%3Aissue%20state%3Aopen%20label%3A%22good%20first%20issue%22).
- Submit a sanitized result from a physical device through the
  [compatibility form](https://github.com/AfanasievN/react-native-device-risk-signals/issues/new?template=03-device-compatibility.yml).
- Discuss an integration question before changing code in
  [GitHub Discussions](https://github.com/AfanasievN/react-native-device-risk-signals/discussions).
- Use the dedicated
  [signal proposal form](https://github.com/AfanasievN/react-native-device-risk-signals/issues/new?template=04-signal-proposal.yml)
  for a new native observation.

Physical-device reports are useful contributions even when no source code changes are required. See
the [device compatibility matrix](docs/DEVICE_COMPATIBILITY.md) for reporting and privacy guidance.

## Local verification

Install the root dependencies and run the library checks:

```sh
npm install
npm run verify
npm pack --dry-run
```

`npm run verify:contract` is part of `verify` and checks every TurboModule method across the
TypeScript spec, Kotlin implementation, and Objective-C++ implementation. Also inside `verify`:
`verify:ecosystem` validates `device-risk-signals.json` against the component rules, and
`verify:package` checks both the working tree and the file list `npm pack` would ship, so a change to
the `files` allow-list cannot silently stop shipping a source tree the Android build needs.

Changes to the standalone Android SDK also run:

```sh
example/android/gradlew -p sdks/android testDebugUnitTest assembleRelease --no-daemon
```

The optional active-probes component builds and tests separately:

```sh
example/android/gradlew -p sdks/android-active-probes :testDebugUnitTest :assembleRelease :example:assembleDebug --no-daemon
```

That component has its own one-button native demo under `sdks/android-active-probes/example/`,
because the passive core's example stays socket-free. Install it with `:example:installDebug`.

Both components run Android lint with warnings treated as errors, and their release AARs are checked
for package contents. Lint exemptions live in `sdks/android/lint.xml` with a written justification
per entry; do not add a lint baseline. Run the gates locally after building both AARs:

```sh
example/android/gradlew -p sdks/android :lintRelease --no-daemon
example/android/gradlew -p sdks/android-active-probes :lintRelease --no-daemon
npm run verify:android-aar
```

`verify:android-aar` reads the built AARs and fails on a permission or manifest component, React
Native or other foreign framework classes, cross-component classes, or any unreviewed payload. It is
not part of `npm run verify` because it needs a Gradle build first.

The instrumented GPU suite needs a booted emulator or a connected device and is not part of CI,
which only compiles it:

```sh
example/android/gradlew -p sdks/android :connectedDebugAndroidTest --no-daemon
```

The example is an independent application. Install and verify it separately:

```sh
cd example
npm install
npm test -- --runInBand --watchman=false
npm run lint
npx tsc --noEmit
```

## Pull requests

Describe the motivation, affected components/platforms, contract impact, privacy impact,
compatibility impact, and verification performed. Keep pull requests focused and avoid generated
build output. For documentation-surface changes, include the affected Pages routes and deployed-site
verification plan.
