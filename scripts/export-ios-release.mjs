import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {existsSync, mkdirSync, writeFileSync} from 'node:fs';
import {dirname, resolve, join} from 'node:path';

// Export committed, allowlisted files only; never copy a checkout or its credentials/build output.
const [version, destination] = process.argv.slice(2);
try {
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(version ?? '')) {
    throw new Error('Expected an independent iOS version, e.g. 0.1.0');
  }
  if (!destination) throw new Error('An absent destination directory is required');
  const output = resolve(destination);
  if (existsSync(output)) throw new Error('Refusing to overwrite an existing destination');
  const git = (...args) => execFileSync('git', args);
  const commit = git('rev-parse', 'HEAD').toString().trim();
  const prefix = 'sdks/ios/';
  const paths = git('ls-tree', '-r', '--name-only', commit, '--',
    `${prefix}Package.swift`, `${prefix}Sources`, `${prefix}Tests`, 'LICENSE')
    .toString().trim().split('\n');
  if (!paths.includes(`${prefix}Package.swift`)) throw new Error('Committed iOS package is missing');
  const files = new Map(paths.map(path => [path.replace(/^sdks\/ios\//, ''), git('show', `${commit}:${path}`)]));
  const source = `https://github.com/AfanasievN/react-native-device-risk-signals/tree/${commit}`;
  files.set('README.md', Buffer.from(`# ios-device-risk-signals

Standalone iOS device and runtime observations for Swift and Objective-C apps.
Product: **IOSDeviceRiskSignals**. Requires **iOS 15.1+** or **Mac Catalyst 15.1+**; not macOS.

## Install

In Xcode, choose File → Add Package Dependencies and enter:

\`https://github.com/AfanasievN/ios-device-risk-signals.git\`

Select version **${version}** and the **IOSDeviceRiskSignals** product.

\`\`\`swift
.package(url: "https://github.com/AfanasievN/ios-device-risk-signals.git", exact: "${version}")
// In your target dependencies:
.product(name: "IOSDeviceRiskSignals", package: "ios-device-risk-signals")
\`\`\`

## Use

\`\`\`swift
import IOSDeviceRiskSignals

let locale = LocaleInfoProvider().localeSignals()
let application = ApplicationInfoProvider().applicationSignals()
\`\`\`

Nine collectors cover application metadata, locale, runtime timing, numeric consistency,
telephony, network, audio latency, device identity and an optional GPU benchmark.
This is a **partial, pre-stable SDK**, not full React Native probe parity.
No collector runs automatically. GPU collection must run off the main thread; it skips on Simulator.
Never block the main thread waiting for a worker collecting device identity, which may hop to main.
Telephony fields are commonly absent on modern iOS. Missing data is not evidence of fraud.

## Privacy and limitations

Raw observations only: no score, trusted/untrusted verdict, blocking, network upload,
permission prompt, or persistent device identifier. The host owns consent, minimization,
retention, transport and decision-making. Local IP addresses, proxy details, locale and
hardware/runtime characteristics can be sensitive or high-entropy: collect only what you need.
Do not use these values to create a prohibited device fingerprint. Simulator checks do not
replace physical-device calibration; GPU, carrier and network behavior require device QA.

[API, privacy and threading details](${source}/sdks/ios/README.md) ·
[Native example](${source}/sdks/ios/example) ·
[Documentation](https://afanasievn.github.io/react-native-device-risk-signals/signals/)

## Development and releases

This repository is a generated distribution mirror. Submit changes and issues to the
[monorepo](https://github.com/AfanasievN/react-native-device-risk-signals).
SOURCE.json records the source commit and SHA-256 checksums. No independent implementation lives here.

Run tests on an available iOS Simulator with
\`xcodebuild test -scheme ios-device-risk-signals -destination 'platform=iOS Simulator,name=iPhone 17 Pro'\`.
\`swift test\` targets macOS and is not supported.

MIT licensed. See LICENSE.
`));
  files.set('RELEASE_NOTES.md', Buffer.from(`# iOS SDK ${version}

Standalone Swift Package for iOS 15.1+ and Mac Catalyst 15.1+, with no React Native dependency.

- Nine raw-observation collectors: application, locale, runtime timing, numeric consistency,
  telephony, network, audio latency, device identity and optional GPU benchmarking.
- Swift-importable Objective-C API, shared statistics helpers and native tests.
- No automatic collection, network transport, permission prompts, persistent IDs or risk scoring.
- Independent versioning and a reproducible, allowlisted export from the monorepo.

Pre-stable, partial API: remaining React Native probes are not included. Physical-device QA
is still required; Simulator cannot validate real carrier data or GPU performance.
See README for privacy and thread requirements, installation and usage.

Source: ${source}
`));
  const sha256 = Object.fromEntries([...files].map(([name, content]) =>
    [name, createHash('sha256').update(content).digest('hex')]));
  mkdirSync(output, {recursive: true});
  for (const [name, content] of files) {
    mkdirSync(dirname(join(output, name)), {recursive: true});
    writeFileSync(join(output, name), content);
  }
  writeFileSync(join(output, 'SOURCE.json'), JSON.stringify({version, commit, source, sha256}, null, 2) + '\n');
  console.log(`Exported iOS ${version} from ${commit} to ${output}`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
