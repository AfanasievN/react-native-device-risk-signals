# Releasing

This repository releases two independent kinds of artifact, on separate tracks:

| Track | Artifact | Registry | Trigger |
| --- | --- | --- | --- |
| npm | `react-native-device-risk-signals` | npmjs.com | a published GitHub Release (`publish.yml`) |
| Android | `io.github.afanasievn:android-device-risk-signals`, `io.github.afanasievn:android-active-probes-device-risk-signals` | Maven Central | a manual `workflow_dispatch` run (`publish-android.yml`) |

Component versions are independent (`docs/ECOSYSTEM_ARCHITECTURE.md`, "Versioning and releases").
Neither Android component has been published yet, and neither track creates a component-prefixed git
tag: `vX.Y.Z` still refers to the npm package alone.

## The npm package

Releases are published to the public npm registry as `react-native-device-risk-signals`. A GitHub
Release is the source of truth for automated publication.

### Before the first release

1. Create or sign in to an npm account with two-factor authentication enabled.
2. Confirm that the package name is still available:

   ```sh
   npm view react-native-device-risk-signals
   ```

   A `404 Not Found` response means no public package currently uses that name.

3. Verify the repository locally:

   ```sh
   npm ci
   npm run verify
   npm pack --dry-run
   ```

4. If npm allows Trusted Publisher configuration before the package exists, configure it using the
   values in the next step and publish the GitHub Release normally.

   Otherwise, publish `0.1.0` manually once to claim the package name:

   ```sh
   npm login
   npm publish
   ```

   Publishing is permanent for that name and version. Inspect the dry-run output before confirming.

5. Open the package settings on npm and configure a GitHub Actions Trusted Publisher:

   - Organization or user: `AfanasievN`
   - Repository: `react-native-device-risk-signals`
   - Workflow filename: `publish.yml`
   - Environment: leave empty
   - Allowed action: `npm publish`

6. After one successful automated release, restrict token-based publishing and revoke any automation
   token that is no longer needed.

7. Create the GitHub Release `v0.1.0`. If the version was published manually, the workflow detects
   that it already exists in npm and exits successfully without attempting to replace it.

### Publishing later versions

1. Update the version without creating a local tag:

   ```sh
   npm version patch --no-git-tag-version
   ```

   Use `minor` for backward-compatible features and `major` for breaking changes.

2. Move the relevant entries from `Unreleased` into a dated section in `CHANGELOG.md`.
3. Run the complete verification:

   ```sh
   npm run verify
   npm pack --dry-run
   ```

4. Merge the version change into `main`.
5. Create and publish a GitHub Release with the exact tag `v<package version>`, for example
   `v0.2.0`.

The `Publish Package` workflow checks that the release tag matches `package.json`, repeats all package
verification, skips versions that already exist in npm, and otherwise publishes through npm's
short-lived OIDC credentials. Draft and prerelease GitHub Releases do not publish the package.

### What consumers receive

The npm archive contains compiled JavaScript and TypeScript declarations, the TurboModule spec, the
Android and iOS native sources, the standalone Android component sources the binding compiles, and
the `contract/` authoring sources, fixtures and generated artifacts. `npm run verify:package` checks
that every path the Android build needs is actually in the archive. React Native autolinking discovers the native package after
installation. Codegen and native compilation happen when the consumer builds their application.

## Publishing the Android components

Both standalone Android components (`sdks/android/`, `sdks/android-active-probes/`) build a release
AAR, a sources jar, a Dokka-rendered javadoc jar and a complete POM, and both can be signed and
uploaded to Maven Central by `.github/workflows/publish-android.yml`. **Nothing on this track works
yet**: the Sonatype account, the verified namespace and the GPG key do not exist. Every step marked
**BLOCKED** below needs a human with the project's GitHub identity; no part of it can be automated
from inside this repository.

Sonatype's OSSRH (`oss.sonatype.org`, `s01.oss.sonatype.org`) was retired on 2025-06-30 and replaced
by the [Central Portal](https://central.sonatype.org/pages/ossrh-eol/). There is no official Gradle
plugin for the Portal, so the build publishes with plain `maven-publish` against the Portal's
[OSSRH Staging API compatibility endpoint](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/).

### 1. Central Portal account and namespace — BLOCKED (user)

1. Sign up at <https://central.sonatype.com> **using the `AfanasievN` GitHub account**. Signing up
   with GitHub auto-provisions the verified namespace `io.github.afanasievn`, which is exactly the
   `groupId` both components already declare. Signing up with an email account instead would leave
   the namespace unverified and the coordinates unusable.
2. Confirm the namespace appears as *Verified* under *View Namespaces*. If it does not, verify it
   manually: create a public repository on `github.com/AfanasievN` named after the verification key
   shown in the Portal, submit the namespace for verification, then delete that repository
   ([namespace registration](https://central.sonatype.org/register/namespace/)).
3. Only `io.github.<github username>` is auto-provisioned; an organization namespace is not. If the
   project ever moves to a GitHub organization, the namespace has to be re-verified and the
   `groupId` in both `build.gradle.kts` files, in `device-risk-signals.json` and in the
   documentation changes with it — a breaking coordinate change, not a cleanup.

### 2. Portal user token — BLOCKED (user)

1. Go to <https://central.sonatype.com/usertoken> and press *Generate User Token*.
2. Save both halves immediately; the Portal shows them once and cannot show them again.
3. The two halves are the username and the password for the Maven repository. An old OSSRH token is
   not accepted and returns `401`.

### 3. GPG key — BLOCKED (user)

Maven Central requires a detached PGP signature (`.asc`) for every deployed file. Generate the key
on a trusted machine, not in CI:

```sh
gpg --full-generate-key            # RSA 4096, no expiry or a deliberate one, real name and email
gpg --list-secret-keys --keyid-format LONG
gpg --keyserver keyserver.ubuntu.com --send-keys <FINGERPRINT>
gpg --armor --export-secret-keys <FINGERPRINT>   # paste the whole block into the CI secret
```

Publish the public half to a keyserver so the validator can find it. Keep the private half in a
password manager; the only copy CI ever sees is the GitHub secret. Nothing in this repository stores
key material, and a signing key must never be committed, pasted into an issue, or written to a file
on a runner.

### 4. Repository secrets — BLOCKED (user)

Add these four under *Settings → Secrets and variables → Actions*. The workflow checks all four
before it does anything else and fails with the missing names listed:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token username (step 2) |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password (step 2) |
| `MAVEN_GPG_PRIVATE_KEY` | full ASCII-armored private key block, `-----BEGIN` line included (step 3) |
| `MAVEN_GPG_PASSPHRASE` | passphrase for that key |

The workflow passes them to Gradle as `ORG_GRADLE_PROJECT_signingInMemoryKey`,
`ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`, `ORG_GRADLE_PROJECT_centralPortalUsername` and
`ORG_GRADLE_PROJECT_centralPortalPassword`. A key with several signing subkeys can select one with
`ORG_GRADLE_PROJECT_signingInMemoryKeyId`; a single-key setup does not need it.

### 5. Choose the first version — BLOCKED (user decision)

Both components default to `0.1.0-SNAPSHOT`, declared once each in
`sdks/android/gradle.properties` and `sdks/android-active-probes/gradle.properties`. The build reads
that property and hardcodes no version anywhere. Picking the first real version is a deliberate
choice, not something the build assumes: `docs/ECOSYSTEM_ARCHITECTURE.md` ("Versioning and releases")
is where that decision is recorded, along with the order in which the components and the binding are
released.

A release changes that one line, or the workflow's `version` input overrides it without any file
change. Keep the two in step: ship from a commit whose `gradle.properties` holds the version being
published, so the repository and the registry never disagree.

### 6. Order of operations

1. Namespace verified, token generated, key generated and published, four secrets stored.
2. Set the component's `deviceRiskSignalsVersion` in its `gradle.properties` to a non-snapshot
   version, move the relevant `CHANGELOG.md` entries, and merge to `main`.
3. Run `Publish Android Component` from the Actions tab with `dry_run` enabled. It runs the full
   verification ring, builds, signs and checks that five `.asc` files exist — and uploads nothing.
4. Re-run with `dry_run` disabled. Gradle uploads to the OSSRH Staging API endpoint, then the
   workflow calls `POST /manual/upload/defaultRepository/io.github.afanasievn` so the deployment
   becomes visible in the Portal.
5. Open <https://central.sonatype.com/publishing/deployments>, review the validation result and
   press **Publish**. This last step is manual by design: a released version can never be replaced
   or deleted.
6. Repeat for the second component if both are being released. The workflow takes one component per
   run, so a bad release of one cannot drag the other with it.
7. Once the artifact resolves from Maven Central, update `device-risk-signals.json`,
   `docs/ECOSYSTEM_ARCHITECTURE.md` and the Pages surfaces in the same change, and only then present
   the coordinate as installable.

Snapshots are a separate, lower-stakes path: a `-SNAPSHOT` version publishes to
<https://central.sonatype.com/repository/maven-snapshots/> (enable snapshots for the namespace in the
Portal first) and is cleaned up after about 90 days.

### 7. Pre-flight checklist

- [ ] `io.github.afanasievn` shows as verified in the Central Portal.
- [ ] Portal user token generated and stored; no OSSRH token is in use anywhere.
- [ ] Public key uploaded to a keyserver; private key held only in a password manager and the
      GitHub secret.
- [ ] All four secrets present in the repository.
- [ ] `deviceRiskSignalsVersion` in the component's `gradle.properties` is the intended
      non-snapshot version and is merged to `main`.
- [ ] That exact version does not already exist on Maven Central.
- [ ] `CHANGELOG.md` records the release.
- [ ] CI is green on the commit being released.
- [ ] A `dry_run` run of `Publish Android Component` succeeded and reported five signatures.
- [ ] No git tag is being created; component-prefixed tags stay blocked until the release-process
      migration in `docs/ECOSYSTEM_ARCHITECTURE.md` lands.

### What works today, without any credentials

- Both components build a signable release publication and publish it to a file repository inside
  their own build output (`:publishReleasePublicationToLocalBuildRepository`), with no registry, no
  credentials and nothing written to `~/.m2`.
- The signing plugin is applied but configures nothing without a key, so every existing task behaves
  exactly as before.
- `npm run verify:android-aar` gates the contents of both AARs.
- The release workflow exists, is manual-only, and refuses to run past its first step without the
  four secrets.
