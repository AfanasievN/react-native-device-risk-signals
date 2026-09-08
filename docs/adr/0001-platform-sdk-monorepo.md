# ADR-0001: Platform SDK monorepo with thin framework bindings

- **Status:** Accepted
- **Date:** 2026-08-26
- **Decision owners:** Device Risk Signals maintainers

## Context

The project began as `react-native-device-risk-signals`, with Android, iOS, TypeScript orchestration,
documentation, and publishing owned by one npm package. Reusing the same platform observations in
native Android, native iOS, Flutter, Capacitor, and Web applications would otherwise require copying
detection logic into several repositories or making native consumers depend on React Native.

## Decision

The repository evolves incrementally into the `device-risk-signals` monorepo:

- Android logic is owned by `android-device-risk-signals`.
- iOS logic is owned by `ios-device-risk-signals`.
- Browser logic is owned by `web-device-risk-signals`.
- React Native, Flutter, and Capacitor packages are thin bindings over those SDKs.
- Probe semantics and the raw-event shape are governed by one shared, versioned contract.
- Components are versioned and released independently.

The existing npm package remains at the repository root until native SDK APIs and package-manager
release pipelines are stable. Migration must preserve its npm name and public API unless a breaking
release is explicitly planned.

## Consequences

Positive:

- Native applications install only their platform SDK.
- Detection logic has one implementation and test suite per platform.
- Framework bindings become smaller and easier to maintain.
- New integrations share field semantics and backend contracts.

Costs:

- CI and release automation become multi-registry and package-aware.
- Compatibility ranges between bindings and native SDKs must be maintained.
- The transitional tree temporarily contains both root-package and monorepo layouts.
- Contract changes require coordination across several independently versioned packages.

## Rejected alternatives

- **Separate repositories immediately:** rejected because synchronized migration and atomic contract
  changes would be harder before the native APIs stabilize.
- **Keep all detection inside React Native:** rejected because native and Flutter/Capacitor consumers
  would depend on the wrong framework or duplicate implementation.
- **One universal package version:** rejected because unrelated platforms would require unnecessary
  releases and version coupling.
- **Implement a shared scoring engine:** rejected because this project collects raw observations;
  trusted scoring and policy belong on the application backend.

## Follow-up decisions

Registry credentials, Maven/Swift publication configuration, workspace tooling, and the GitHub
repository/Pages rename require separate implementation decisions. They must follow the boundaries
defined here and in `docs/ECOSYSTEM_ARCHITECTURE.md`. GitHub Pages must become the ecosystem-level
documentation portal as components activate; its route/canonical-URL migration is part of the
monorepo rollout and cannot be postponed until after a repository rename.
