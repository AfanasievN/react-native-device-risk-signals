# iOS releases from the monorepo

Source: `sdks/ios/`. Distribution mirror: `AfanasievN/ios-device-risk-signals`.
Never develop providers in the mirror. It has independent plain semantic-version tags.

## One-time GitHub setup

Create a fine-grained personal access token owned by AfanasievN, restricted to the
`ios-device-risk-signals` repository, with **Contents: Read and write** only (Metadata read is
automatic). Use an expiration and rotate it before expiry. Add it in the monorepo's
Settings → Secrets and variables → Actions as **IOS_DISTRIBUTION_TOKEN**.
Do not paste the token into issues, chat, commands, source or release notes.
The ordinary GITHUB_TOKEN cannot push to a different repository.

The first automated beta attempt on 2026-09-29 passed tests/builds but received HTTP 403 on push.
The beta was subsequently published with the maintainer's local authorization. Before the next
automated release, verify the token selects the distribution repository and grants Contents write;
replace the Actions secret if necessary. Do not assume secret presence proves write access.

## Experimental releases

Versions such as `0.2.0-beta.1` are supported (also alpha/rc). The workflow marks them as GitHub
prereleases and does not move the latest Release. Install the exact prerelease version in Xcode.
The maintainer authorized the first beta without physical-iPhone QA; that limitation must remain
prominent. Promotion to an ordinary release still requires device QA. For the React Native package,
prerelease GitHub releases publish to npm `next`, never `latest`; version/flag mismatches fail closed.

## Publish

1. Commit and push reviewed iOS changes to main; wait for CI.
2. Run **Publish iOS Swift Package** in this repository's Actions, choose the independent iOS
   version, and keep `dry_run` checked first.
3. After it succeeds, run the same workflow/version with `dry_run` unchecked.
4. Check the mirror's tag and Release; install that exact version in a clean Xcode consumer.
5. Update availability and tested version in the ecosystem manifest and Pages when needed.

The workflow exports committed files only, tests that exported package on Simulator, builds for
iOS and Catalyst, then atomically pushes the mirror commit and version tag. Existing tags fail
closed. SOURCE.json provides the originating monorepo SHA and exported file checksums.

If the tag exists but the GitHub Release does not, create the Release for that existing tag with
its RELEASE_NOTES.md. Never rewrite a published version; fix source and publish a new patch.

## Local preparation

```sh
node --test scripts/ios-release.test.mjs
node scripts/export-ios-release.mjs 0.1.0 /tmp/ios-device-risk-signals-release
```

The destination must not exist. Run Xcode tests in the exported directory; macOS `swift test`
is not supported. Exported Sources are byte-identical to the recorded source commit.
Local first-publication uses the maintainer's GitHub authorization; it does not install that
credential in CI. Subsequent automated publication requires the scoped secret above.

No npm version bump, Maven publication or React Native iOS migration is required for an iOS release.
