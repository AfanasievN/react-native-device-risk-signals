# Contributing

Thank you for helping improve React Native Device Risk Signals.

By participating, you agree to follow the project [Code of Conduct](CODE_OF_CONDUCT.md).

## Before opening a change

- Keep signal collection transparent, minimal, and permission-conscious.
- Do not add company-specific endpoints, identifiers, credentials, datasets, or application names.
- Document new platform permissions and privacy implications.
- Add tests for new behavior and platform-specific fallbacks.
- Keep risk verdicts and scoring logic out of the client library.
- Keep `src/probeCatalog.ts` and `docs/DATA_DICTIONARY.md` synchronized with probe behavior.
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
TypeScript spec, Kotlin implementation, and Objective-C++ implementation.

The example is an independent application. Install and verify it separately:

```sh
cd example
npm install
npm test -- --runInBand --watchman=false
npm run lint
npx tsc --noEmit
```

## Pull requests

Describe the motivation, affected platforms, privacy impact, and verification performed. Keep pull
requests focused and avoid generated build output.
