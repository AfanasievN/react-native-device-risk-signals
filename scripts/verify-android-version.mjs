#!/usr/bin/env node
// Drift guard: each Android component's version is declared in two places, and a release that
// updates only one fails in the path CI exercises least.
//
//   sdks/android/gradle.properties                deviceRiskSignalsVersion
//   android/build.gradle                          coreVersion default
//
//   sdks/android-active-probes/gradle.properties  deviceRiskSignalsVersion
//   android/build.gradle                          activeProbesVersion default
//
// The two components are versioned INDEPENDENTLY - docs/ECOSYSTEM_ARCHITECTURE.md, "Versioning and
// releases" - so this never compares one component against the other. It only checks that the
// React Native binding's artifact-mode default for a component matches that component's own
// declaration. A stale default resolves nothing once that component carries a real version, and
// only the -PdeviceRiskSignalsUseArtifacts CI job would notice.
import { readFileSync } from 'node:fs';

const BINDING = 'android/build.gradle';

const components = [
  {
    label: 'passive core',
    properties: 'sdks/android/gradle.properties',
    bindingProperty: 'deviceRiskSignalsCoreVersion',
    coordinate: 'io.github.afanasievn:android-device-risk-signals',
  },
  {
    label: 'active probes',
    properties: 'sdks/android-active-probes/gradle.properties',
    bindingProperty: 'deviceRiskSignalsActiveProbesVersion',
    coordinate: 'io.github.afanasievn:android-active-probes-device-risk-signals',
  },
];

const failures = [];
let bindingText;
try {
  bindingText = readFileSync(BINDING, 'utf8');
} catch (error) {
  console.error(`${BINDING} could not be read (${error.code ?? error.message}).`);
  process.exit(1);
}

const checked = [];

for (const component of components) {
  let declared;
  try {
    declared = /^deviceRiskSignalsVersion=(.+)$/m.exec(readFileSync(component.properties, 'utf8'))?.[1]?.trim();
  } catch (error) {
    failures.push(`${component.properties} could not be read (${error.code ?? error.message}).`);
    continue;
  }
  if (!declared) {
    failures.push(
      `${component.properties} no longer declares deviceRiskSignalsVersion. If the declaration ` +
        'moved, update scripts/verify-android-version.mjs rather than dropping the guard.',
    );
    continue;
  }

  const pattern = new RegExp(`findProperty\\("${component.bindingProperty}"\\)\\s*\\?:\\s*"([^"]+)"`);
  const bindingDefault = pattern.exec(bindingText)?.[1]?.trim();
  if (!bindingDefault) {
    failures.push(
      `${BINDING} no longer declares a default for ${component.bindingProperty} ` +
        `(${component.label}). The artifact-mode dependency would fall back to whatever is left.`,
    );
    continue;
  }

  if (bindingDefault !== declared) {
    failures.push(
      `${component.label}: ${component.properties} declares ${declared}, but ${BINDING} defaults ` +
        `${component.bindingProperty} to ${bindingDefault}. Artifact-mode builds would resolve a ` +
        'version that is not the one being built.',
    );
    continue;
  }

  if (!bindingText.includes(component.coordinate)) {
    failures.push(
      `${BINDING} no longer declares ${component.coordinate}. Coordinates are normative in ` +
        'device-risk-signals.json; update both together.',
    );
    continue;
  }

  checked.push(`${component.label} ${declared}`);
}

if (failures.length > 0) {
  console.error(failures.join('\n\n'));
  process.exit(1);
}

console.log(`Verified the binding tracks each Android component version: ${checked.join(', ')}.`);
