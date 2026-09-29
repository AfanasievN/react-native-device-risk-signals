# ADR-0006: Resolve React Native passive Android core from Maven Central

- Status: Accepted
- Date: 2026-09-29

## Decision

The React Native binding depends on `io.github.afanasievn:android-device-risk-signals:0.1.0`,
whose POM and AAR are publicly available from Maven Central. Remove passive core Kotlin sources
from the npm archive and its Android source set. Hosts own `mavenCentral()` repository configuration.

Active probes remain bundled source because their Maven coordinate is unpublished. The existing
artifact verification flag applies only to active probes. iOS packaging is unchanged.

## Consequences

Native core compilation is shared with native consumers and Kotlin internal declarations become
an enforced boundary. First builds require Maven access; cached builds can reuse Gradle artifacts.
Core upgrades require a binding dependency change and a new npm release. JS signal APIs are unchanged.
CI must compile the normal binding against the public artifact and verify npm archive contents.
