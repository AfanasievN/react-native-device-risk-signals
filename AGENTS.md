# Repository instructions for coding agents

These rules apply to the entire repository.

## Product boundaries

- The SDK collects raw device and runtime observations. It must not calculate a risk score, return a
  trusted/untrusted verdict, or make a blocking decision.
- The SDK performs no network requests. Applications own authentication, serialization, retries,
  storage, and transport of collected events.
- Never add persistent device identifiers, vendor endpoints, credentials, customer-specific names,
  `QUERY_ALL_PACKAGES`, or permission prompts.
- Treat high-entropy fields, location, telephony, accessibility, local addresses, and app visibility
  as sensitive data. Document purpose and platform/privacy implications before implementing them.

## Adding or changing a probe

1. Define the raw result type and TurboModule method in `src/NativeDeviceIntel.ts` when native data is
   required.
2. Implement the method on both Kotlin and Objective-C++, or add an explicit platform stub and gate
   the probe with `androidOnly`/`iosOnly`.
3. Add the JS probe under `src/probes/` and register it in `src/probes/index.ts`.
4. Add or update its descriptor in `src/probeCatalog.ts`, including selectable fields, platforms,
   sensitivity, permissions, data categories, default state, purpose, and notes.
5. Update `docs/DATA_DICTIONARY.md` and privacy documentation.
6. Add focused tests for registry composition, configuration validation, failure, timeout, and
   platform behavior.
7. Run `npm run verify`. The native contract verifier must report parity across TypeScript, Kotlin,
   and Objective-C++.

New sensitive or expensive probes must ship disabled until representative physical-device QA and a
documented benchmark justify enabling them.

## Compatibility and generated code

- The supported floor is React Native 0.76 with the New Architecture enabled.
- Do not edit generated code under `lib/` or React Native codegen output. Edit `src/` and rebuild.
- Keep the public TypeScript contract, Kotlin implementation, Objective-C++ implementation, Probe
  Catalog, README, and example app consistent.
- An API removal or incompatible event/configuration change requires a documented breaking release.

## Verification

Use RED -> GREEN -> REFACTOR for behavior changes. Run the narrow test first, then the full checks:

```sh
npm run verify
npm pack --dry-run
npm test --prefix example -- --runInBand --watchman=false
npm run lint --prefix example
npx --prefix example tsc --noEmit -p example/tsconfig.json
```

Do not weaken TypeScript, lint, CodeQL, native build, or privacy checks to make CI pass.
