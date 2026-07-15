# Contributing

Thank you for helping improve React Native Device Risk Signals.

## Before opening a change

- Keep signal collection transparent, minimal, and permission-conscious.
- Do not add company-specific endpoints, identifiers, credentials, datasets, or application names.
- Document new platform permissions and privacy implications.
- Add tests for new behavior and platform-specific fallbacks.
- Keep risk verdicts and scoring logic out of the client library.

Open an issue before introducing a new sensitive signal category or a breaking public API change.

## Local verification

Install the root dependencies and run the library checks:

```sh
npm install
npm test
npm run typecheck
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

Describe the motivation, affected platforms, privacy impact, and verification performed. Keep pull
requests focused and avoid generated build output.
