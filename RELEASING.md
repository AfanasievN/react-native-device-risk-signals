# Releasing

Releases are published to the public npm registry as `react-native-device-risk-signals`. A GitHub
Release is the source of truth for automated publication.

## Before the first release

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

## Publishing later versions

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

## What consumers receive

The npm archive contains compiled JavaScript and TypeScript declarations, the TurboModule spec, and
the Android and iOS native sources. React Native autolinking discovers the native package after
installation. Codegen and native compilation happen when the consumer builds their application.
