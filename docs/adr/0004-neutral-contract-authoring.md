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

## Update, 2026-09-14: field types

The second half landed the same day. `contract/source/signal-types.source.json` now authors, per
probe, the type name and every field's published type string, optionality and JSON Schema fragment,
and it absorbs the probe-id-to-type-name map that used to be hand-maintained inside
`scripts/read-signal-contract.mjs`. `scripts/sync-pages-catalog.mjs` builds the published
`fieldTypes`, `fieldSchemas` and `optionalFields` from that file and no longer parses TypeScript at
all; every generated artifact stayed byte-identical through the switch.

The direction of truth is deliberately not reversed yet. `src/NativeDeviceIntel.ts` remains
hand-written because React Native codegen consumes it, so the risk is drift: a field added, renamed
or re-optionalized there would leave the published contract describing the old shape.
`scripts/verify-signal-types.mjs` closes that hole. It compares the TypeScript declarations against
the neutral source and fails on a field present on only one side, a differing type string,
optionality, nested schema or type name, a probe missing from either side, and a source entry for a
probe that is not in the catalog. It compares field names as a set, because TypeScript declaration
order and catalog field order already disagree for `os_integrity_frida_scan` and `runtime_timing`,
and it ignores JSON object key order while keeping array order significant.

Three encodings stay lossy and are recorded rather than fixed. `device_identity.androidBuild`
publishes the bare alias name `AndroidBuildInfo` as its type, so a non-TypeScript consumer must read
the schema instead. `runtime_timing` is a flattened intersection, so reordering the two halves in
TypeScript would silently reorder published fields. `hardware.batteryState` and
`hardware.batteryHealth` publish as plain `string` while their closed value sets live only in a
TypeScript comment, so nothing validates them.

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
