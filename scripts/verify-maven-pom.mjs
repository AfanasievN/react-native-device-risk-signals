#!/usr/bin/env node
// Pre-flight check for a Maven Central deployment.
//
// The Central Portal validates a POM only AFTER the bundle is uploaded, so a missing field is
// discovered in a deployment that then has to be dropped and rebuilt. This checks the same
// requirements locally, against the POM the build actually produced.
//
// Requirements: https://central.sonatype.org/publish/requirements/ (fetched 2026-09-18) - the
// project coordinates, a name, a description, a project URL, licence information, developer
// information and SCM information, plus sources and javadoc artifacts alongside the main one.
//
// Usage: node scripts/verify-maven-pom.mjs <pom-file> [more-pom-files...]
// With no arguments it checks every release POM found in the components' local build repositories.
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';

const LOCAL_REPOS = [
  'sdks/android/build/local-maven/io/github/afanasievn/android-device-risk-signals',
  'sdks/android-active-probes/build/local-maven/io/github/afanasievn/android-active-probes-device-risk-signals',
];

function text(xml, tag) {
  return new RegExp(`<${tag}>([^<]*)</${tag}>`).exec(xml)?.[1]?.trim() ?? '';
}

function block(xml, tag) {
  return new RegExp(`<${tag}>([\\s\\S]*?)</${tag}>`).exec(xml)?.[1] ?? '';
}

// Project-level fields only. Without this, a POM missing its project <url> still passes, because
// the first <url> in the document belongs to the licence, the developer or the SCM block. That is
// exactly the failure this check exists to catch, so the nested blocks are removed before any
// top-level lookup.
function projectLevel(xml) {
  let stripped = xml;
  for (const tag of ['licenses', 'developers', 'scm', 'issueManagement', 'dependencies',
                     'distributionManagement', 'organization', 'contributors', 'mailingLists',
                     'build', 'profiles', 'parent']) {
    stripped = stripped.replace(new RegExp(`<${tag}>[\\s\\S]*?</${tag}>`, 'g'), '');
  }
  return stripped;
}

function discover() {
  const found = [];
  for (const repo of LOCAL_REPOS) {
    if (!existsSync(repo)) continue;
    for (const version of readdirSync(repo)) {
      const dir = join(repo, version);
      let entries;
      try {
        entries = readdirSync(dir);
      } catch {
        continue;
      }
      const pom = entries.find((name) => name.endsWith('.pom'));
      if (pom) found.push(join(dir, pom));
    }
  }
  return found;
}

const poms = process.argv.slice(2).length > 0 ? process.argv.slice(2) : discover();

if (poms.length === 0) {
  console.error(
    'No POM found. Publish to a component\'s local build repository first, for example:\n' +
      '  example/android/gradlew -p sdks/android :publishReleasePublicationToLocalBuildRepository',
  );
  process.exit(1);
}

let failed = false;

for (const path of poms) {
  const problems = [];
  const xml = readFileSync(path, 'utf8');

  const project = projectLevel(xml);
  for (const tag of ['modelVersion', 'groupId', 'artifactId', 'version', 'name', 'description', 'url']) {
    if (!text(project, tag)) problems.push(`<${tag}> is missing or empty at the project level`);
  }

  const licence = block(xml, 'licenses');
  if (!text(licence, 'name')) problems.push('<licenses> must name a licence');
  if (!text(licence, 'url')) problems.push('<licenses> must give a licence URL');

  const developers = block(xml, 'developers');
  if (!developers.trim()) {
    problems.push('<developers> is required');
  } else if (!text(developers, 'name') && !text(developers, 'id') && !text(developers, 'email')) {
    problems.push('<developers> must identify at least one developer by name, id or email');
  }

  const scm = block(xml, 'scm');
  for (const tag of ['connection', 'url']) {
    if (!text(scm, tag)) problems.push(`<scm><${tag}> is required`);
  }

  const version = text(project, 'version');
  const isSnapshot = version.endsWith('-SNAPSHOT');

  // Companion artifacts. A snapshot does not go through release validation, so only a release is
  // held to the sources/javadoc requirement.
  const dir = dirname(path);
  const stem = basename(path, '.pom');
  if (!isSnapshot) {
    for (const suffix of ['-sources.jar', '-javadoc.jar']) {
      if (!existsSync(join(dir, stem + suffix))) {
        problems.push(`${stem}${suffix} is missing; Central requires sources and javadoc artifacts`);
      }
    }
    const main = ['.aar', '.jar'].some((ext) => existsSync(join(dir, stem + ext)));
    if (!main) problems.push(`no main artifact (${stem}.aar or .jar) next to the POM`);
  }

  const label = `${text(project, 'groupId')}:${text(project, 'artifactId')}:${version}`;
  if (problems.length > 0) {
    failed = true;
    console.error(`${label}\n  ${path}\n` + problems.map((p) => `  - ${p}`).join('\n') + '\n');
  } else {
    const note = isSnapshot ? ' (snapshot: sources/javadoc not required)' : '';
    console.log(`${label} satisfies the Central Portal POM requirements${note}.`);
  }
}

if (failed) {
  console.error('Fix the POM in the component\'s build.gradle.kts before deploying.');
  process.exit(1);
}
