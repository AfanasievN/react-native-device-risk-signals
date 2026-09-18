#!/usr/bin/env node
// Drift guard: the Android component version is declared in three places, and a release that
// updates only some of them fails in the one path CI exercises least.
//
//   sdks/android/gradle.properties                  deviceRiskSignalsVersion
//   sdks/android-active-probes/gradle.properties    deviceRiskSignalsVersion
//   android/build.gradle                            componentVersion default, used by the
//                                                   -PdeviceRiskSignalsUseArtifacts path
//
// A stale default in the binding resolves nothing once the components carry a real version, and
// only the artifact-mode CI job would notice. This check is cheap and runs with the rest.
import { readFileSync } from 'node:fs';

const sources = [
  {
    path: 'sdks/android/gradle.properties',
    label: 'passive core component',
    read: (text) => /^deviceRiskSignalsVersion=(.+)$/m.exec(text)?.[1]?.trim(),
  },
  {
    path: 'sdks/android-active-probes/gradle.properties',
    label: 'active probes component',
    read: (text) => /^deviceRiskSignalsVersion=(.+)$/m.exec(text)?.[1]?.trim(),
  },
  {
    path: 'android/build.gradle',
    label: 'React Native binding artifact-mode default',
    read: (text) =>
      /findProperty\("deviceRiskSignalsComponentVersion"\)\s*\?:\s*"([^"]+)"/.exec(text)?.[1]?.trim(),
  },
];

const found = [];
const failures = [];

for (const source of sources) {
  let text;
  try {
    text = readFileSync(source.path, 'utf8');
  } catch (error) {
    failures.push(`${source.path} could not be read (${error.code ?? error.message}).`);
    continue;
  }
  const version = source.read(text);
  if (!version) {
    failures.push(
      `${source.path} no longer declares a version this check can find. If the declaration moved, ` +
        'update scripts/verify-android-version.mjs rather than dropping the guard.',
    );
    continue;
  }
  found.push({ ...source, version });
}

const versions = new Set(found.map((entry) => entry.version));
if (versions.size > 1) {
  failures.push(
    'Android component versions disagree:\n' +
      found.map((entry) => `  ${entry.version.padEnd(16)} ${entry.path} (${entry.label})`).join('\n'),
  );
}

// The binding may also name the coordinates; a rename there would silently resolve nothing.
const bindingText = readFileSync('android/build.gradle', 'utf8');
for (const coordinate of [
  'io.github.afanasievn:android-device-risk-signals',
  'io.github.afanasievn:android-active-probes-device-risk-signals',
]) {
  if (!bindingText.includes(coordinate)) {
    failures.push(
      `android/build.gradle no longer declares ${coordinate}. Coordinates are normative in ` +
        'device-risk-signals.json; update both together.',
    );
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n\n'));
  process.exit(1);
}

console.log(
  `Verified the Android component version ${[...versions][0]} agrees across ${found.length} declarations.`,
);
