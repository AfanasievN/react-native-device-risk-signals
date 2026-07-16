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
