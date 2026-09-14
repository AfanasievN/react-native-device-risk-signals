# Device Risk Signals contract

This directory is the platform-neutral, machine-readable boundary shared by every SDK and binding in
the Device Risk Signals ecosystem.

- `probe-catalog.json` describes probes, fields, platforms, sensitivity, permissions, and defaults.
- `raw-signal-event.schema.json` describes the additive raw-event envelope accepted by a backend.

`fixtures/` holds cross-SDK conformance payloads: complete events plus sidecars saying what each one
pins, including the four outcome states and the false-versus-absent and zero-versus-absent
distinctions. Negative fixtures must stay rejected. Run them with `npm run verify:fixtures`.

Probe metadata is authored in `source/probe-catalog.source.json` and field types in
`source/signal-types.source.json`; `src/probeCatalog.ts` is generated from the first, and the
published `fieldTypes`/`fieldSchemas`/`optionalFields` from the second (see
[ADR-0004](../docs/adr/0004-neutral-contract-authoring.md)). Generating the published contract no
longer parses TypeScript. `src/NativeDeviceIntel.ts` is still hand-written for React Native codegen
and is held in sync by `npm run verify:signal-types`; during the incremental monorepo migration it remains
the authoring sources. Run `npm run docs:sync` after changing either source; the command regenerates
both this neutral contract and the GitHub Pages copies. Do not edit generated JSON by hand.

The contract contains observations and collection outcomes only. It deliberately contains no risk
score, trusted/untrusted verdict, persistent identifier, transport, or vendor endpoint.

Independent contract authoring, versioning and cross-SDK conformance are still pending; see the
[shared-contract checklist](../docs/MIGRATION_ROADMAP.md#3-make-the-shared-contract-independent).
