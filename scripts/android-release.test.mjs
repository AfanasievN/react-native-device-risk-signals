import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, copyFileSync, readFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

function checkAar(args) {
  const root = mkdtempSync(join(tmpdir(), 'aar-release-'));
  try {
    mkdirSync(join(root, 'scripts'));
    copyFileSync('scripts/verify-android-aar.mjs', join(root, 'scripts/verify-android-aar.mjs'));
    copyFileSync('device-risk-signals.json', join(root, 'device-risk-signals.json'));
    for (const id of ['android', 'android-active-probes']) {
      mkdirSync(join(root, 'sdks', id), {recursive: true});
      copyFileSync(`sdks/${id}/build.gradle.kts`, join(root, 'sdks', id, 'build.gradle.kts'));
    }
    return spawnSync(process.execPath, [join(root, 'scripts/verify-android-aar.mjs'), ...args], {encoding: 'utf8'});
  } finally { rmSync(root, {recursive: true, force: true}); }
}

test('single-component gate does not require the other archive; default still requires both', () => {
  for (const id of ['android', 'android-active-probes']) {
    const result = checkAar(['--component', id]);
    assert.equal(result.status, 1); // Selected archive is deliberately absent, never silently skipped.
    assert.match(result.stderr, new RegExp(`${id} \\(sdks/${id}\\)`));
    const other = id === 'android' ? 'android-active-probes' : 'android';
    assert.ok(!result.stderr.includes(`${other} (sdks/${other})`), result.stderr);
  }
  const all = checkAar([]);
  assert.match(all.stderr, /android \(sdks\/android\)/);
  assert.match(all.stderr, /android-active-probes \(sdks\/android-active-probes\)/);
});

test('unknown components and malformed arguments fail closed', () => {
  for (const args of [['--component', 'web'], ['--component'], ['--typo', 'android']]) {
    const result = checkAar(args);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Unknown Android component|Usage:/);
  }
});

test('release version validation treats shell syntax as data', () => {
  const workflow = readFileSync('.github/workflows/publish-android.yml', 'utf8');
  const step = workflow.split('- name: Reject a version the build cannot publish')[1].split('- name: Check out repository')[0];
  const script = step.split('run: |')[1].split('\n').map(line => line.replace(/^          /, '')).join('\n');
  const hostile = "'; printf INJECTED; #";
  const expanded = script.replaceAll('${{ inputs.version }}', hostile);
  const result = spawnSync('bash', ['-c', expanded], {encoding: 'utf8', env: {...process.env, RELEASE_VERSION: hostile}});
  assert.notEqual(result.status, 0);
  assert.ok(!result.stdout.startsWith('INJECTED'), result.stdout);
  assert.ok(!script.includes('${{ inputs.version }}'));
  assert.match(step, /RELEASE_VERSION: \$\{\{ inputs.version \}\}/);
  const valid = spawnSync('bash', ['-c', script], {encoding: 'utf8', env: {...process.env, RELEASE_VERSION: '0.1.0'}});
  assert.equal(valid.status, 0, valid.stderr);
  assert.match(workflow, /npm run verify:android-aar -- --component "\$RELEASE_COMPONENT"/);
});
