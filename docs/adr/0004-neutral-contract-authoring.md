# ADR-0004: Probe catalog authoring leaves the React Native package

- Status: accepted for the catalog half; type authoring, contract versioning and cross-SDK generation remain open
- Date: 2026-09-14
- Follows: [ADR-0001](0001-platform-sdk-monorepo.md)

## Decision

Probe metadata is authored in `contract/source/probe-catalog.source.json`, a framework-neutral JSON
file with no TypeScript and no React Native types. `src/probeCatalog.ts` becomes a generated file:
`scripts/generate-probe-catalog.mjs --write` emits it, `--check` fails the build when it drifts, and
`npm run verify` runs that check. The generated file is byte-identical to the previously hand-written
one, so the package's public surface does not move: the data still ships behind the
`react-native-device-risk-signals/catalog` subpath export, `ProbeId` stays a literal union derived
from `as const`, and `getProbeDescriptor` keeps its signature.

The authored source carries only contract data: probe ids, titles, purposes, platforms, defaults,
sensitivity, permissions, data categories, field lists and notes, in an explicit order, plus the
enum vocabularies and the descriptor shape. Everything TypeScript-specific stays in the generator
template, which is binding surface rather than contract.

The published catalog now names that file in its `source` field, because a catalog that pointed at a
generated file would misstate its own provenance.

## Scope and what this does not decide

This slice moves authoring only. No probe id, field, type, default, permission or note changes, and
no generated artifact changes except the one provenance string. The following stay open and are
tracked in the migration checklist:

- Field TYPES are still derived by parsing `src/NativeDeviceIntel.ts` and `src/probes/runtimeProbe.ts`
  with the TypeScript compiler API, and the published `fieldTypes` values are raw TypeScript source
  text such as `string[]` and `AndroidBuildInfo`. Until that moves, the contract still embeds
  binding syntax and a new probe still needs a hand-written entry in the id-to-type-name map in
  `scripts/read-signal-contract.mjs`.
- `sdk_version` in the published catalog is still the npm package version, so the contract cannot yet
  be versioned or released independently.
- Generating Kotlin and Swift models from the same source, and validating every implementation
  against shared fixtures, is not done. `contract/fixtures/` and `scripts/verify-contract-fixtures.mjs`
  are the first step: they pin envelope shape, the four outcome states, nested objects, arrays, and
  the false-versus-absent and zero-versus-absent distinctions, and they reject malformed payloads.

## Consequences

Adding or changing a probe now starts in `contract/source/probe-catalog.source.json`, then runs the
generator and `npm run docs:sync`; editing `src/probeCatalog.ts` by hand is a build failure rather
than a silent divergence. `AGENTS.md`, `CONTRIBUTING.md`, `README.md`, the data dictionary, the
contract README and the architecture document are updated to name the new authoring path.

`contract/source/` ships inside the npm tarball, because `package.json` already includes `contract`.
That is intentional for now: the neutral contract is small and having it next to the generated
artifacts helps a consumer audit provenance. It should be revisited when the contract becomes an
independently released component.

The risk this slice accepts is that two authoring sources still exist: neutral JSON for metadata and
TypeScript for field types. Until the second one moves, a reader must know that half the contract is
authored in the binding. That is why this ADR is recorded as accepted for the catalog half only.
