# Device Risk Signals contract

This directory is the platform-neutral, machine-readable boundary shared by every SDK and binding in
the Device Risk Signals ecosystem.

- `probe-catalog.json` describes probes, fields, platforms, sensitivity, permissions, and defaults.
- `raw-signal-event.schema.json` describes the additive raw-event envelope accepted by a backend.

During the incremental monorepo migration, `src/probeCatalog.ts` and `src/NativeDeviceIntel.ts` remain
the authoring sources. Run `npm run docs:sync` after changing either source; the command regenerates
both this neutral contract and the GitHub Pages copies. Do not edit generated JSON by hand.

The contract contains observations and collection outcomes only. It deliberately contains no risk
score, trusted/untrusted verdict, persistent identifier, transport, or vendor endpoint.

Independent contract authoring, versioning and cross-SDK conformance are still pending; see the
[shared-contract checklist](../docs/MIGRATION_ROADMAP.md#3-make-the-shared-contract-independent).
