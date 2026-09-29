# ADR-0007: Publish the iOS Swift Package from the monorepo

- Status: accepted
- Date: 2026-09-29

## Context

SwiftPM resolves semantic versions from Git tags and expects a root Package.swift. The monorepo
already uses vX.Y.Z tags for React Native. Sharing that version namespace would couple independent
components, while ios-vX.Y.Z is not a normal SwiftPM semantic version requirement.

## Decision

Keep all authored iOS source, tests and release automation here. Export the committed Package.swift,
Sources, Tests and root LICENSE into AfanasievN/ios-device-risk-signals. Generate a consumer README,
release notes and SOURCE.json with the source commit and file checksums. This repository is a
distribution mirror, not a second implementation. Issues and pull requests belong to the monorepo.

The mirror uses plain X.Y.Z tags. The monorepo retains existing npm tags and does not introduce
component-prefixed tags or workspaces. Publish iOS Swift Package is a manual workflow on main,
dry-run by default, with simulator tests and iOS/Catalyst builds before any write. It refuses an
existing version. A narrowly scoped IOS_DISTRIBUTION_TOKEN enables writes to the mirror only.

## Consequences

One repository controls platform releases; npm, Maven and SwiftPM retain independent versions.
The first release is 0.1.0 with nine collectors, not full React Native parity. Development status
continues until physical-device calibration and remaining activation gates pass. React Native
CocoaPods packaging is unchanged. No claims of new probe coverage or permissions are introduced.

If Git push succeeds but GitHub Release creation fails, recover the Release on the existing tag;
never delete or move a published tag. The Git tag itself is the SwiftPM distribution boundary.
