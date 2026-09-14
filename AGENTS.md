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
4. Add or update its descriptor in `contract/source/probe-catalog.source.json`, then run
   `node scripts/generate-probe-catalog.mjs --write`. Never hand-edit the generated
   `src/probeCatalog.ts`; include selectable fields, platforms,
   sensitivity, permissions, data categories, default state, purpose, and notes.
5. Update `docs/DATA_DICTIONARY.md` and privacy documentation.
6. Add focused tests for registry composition, configuration validation, failure, timeout, and
   platform behavior.
7. Run `npm run verify`. The native contract verifier must report parity across TypeScript, Kotlin,
   and Objective-C++.

New sensitive or expensive probes must ship disabled until representative physical-device QA and a
documented benchmark justify enabling them.

Consistency helpers may compare already collected raw values, but must omit comparisons when either
side is unavailable and must never assign weights, aggregate a score, or return a fraud/safety verdict.
Transaction-time probes must remain disabled by default until their accessibility and remote-control
observations have been calibrated on representative physical devices.

## Compatibility and generated code

- The supported floor is React Native 0.76 with the New Architecture enabled.
- Do not edit generated code under `lib/` or React Native codegen output. Edit `src/` and rebuild.
- Keep the public TypeScript contract, Kotlin implementation, Objective-C++ implementation, Probe
  Catalog, README, and example app consistent.
- An API removal or incompatible event/configuration change requires a documented breaking release.

## Monorepo architecture

- `docs/ECOSYSTEM_ARCHITECTURE.md` and `device-risk-signals.json` are normative for component
  boundaries, naming, dependencies, lifecycle, and releases.
- Android collection logic belongs in `sdks/android/`; React Native conversion belongs in `android/`.
  Standalone Android source must not import `com.facebook.react`.
- iOS collection logic will move to `sdks/ios/`; Objective-C++ React Native code must remain a thin
  adapter and new providers should avoid React Native types.
- Web collection belongs in `sdks/web/`. Bindings under `bindings/` must not duplicate platform
  detection logic or depend on another binding.
- A package rename, new component, reversed dependency, or shared-contract breaking change requires
  an ADR plus synchronized architecture and ecosystem-manifest updates.
- Do not enable workspaces or component-prefixed release tags until the migration gates documented
  in the architecture are satisfied.
- Component names/statuses, registry coordinates, permissions, compatibility, and contract changes
  must update the relevant GitHub Pages surfaces in the same change. Planned components must never
  be documented with install commands that imply an unpublished artifact exists.
- Treat the Pages URL/repository rename checklist in `docs/ECOSYSTEM_ARCHITECTURE.md` as a release
  gate; preserve canonical schema/catalog URLs and existing documentation routes during migration.

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

## AI attribution

### Commits
When you author a commit, end the message with one trailer identifying
yourself and your model:

```
Assisted-by: <Tool>:<model>
```

Example: `Assisted-by: Codex:gpt-5.2-codex`. Put it on the last line of the
message, after one blank line. Add it to every commit you author, including
fixups. Never state or estimate token usage anywhere — you do not have
access to real numbers.

### Merge requests
When you create a merge request, declare AI involvement with exactly one
label, using a quick action on its own line in the MR description:

- `/label ~"ai::agent"` — you authored the commits in this MR end-to-end
- `/label ~"ai::assisted"` — you helped (plan, code, review); a human authored the commits

Do not set `ai::none` — that label is for humans to declare.

A human-readable footer at the end of the MR description (for example
"🤖 Generated with Claude Code") is allowed but optional — keep your
tool's default. It is decoration: analytics reads only the `ai::*` label
and commit trailers. Do not put `Co-Authored-By` into an MR description —
it is a commit trailer and means nothing outside commits.
