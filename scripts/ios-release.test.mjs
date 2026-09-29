import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, readFileSync, existsSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

test('iOS export is standalone, traced to a commit, and refuses overwrite', () => {
  const temp = mkdtempSync(join(tmpdir(), 'ios-release-test-'));
  const output = join(temp, 'package');
  const run = (version, destination = output) => spawnSync(process.execPath,
    ['scripts/export-ios-release.mjs', version, destination], {encoding: 'utf8'});
  try {
    assert.equal(run('../bad').status, 1);
    assert.equal(existsSync(output), false);
    const result = run('0.1.0');
    assert.equal(result.status, 0, result.stderr);
    assert.ok(existsSync(join(output, 'Package.swift')));
    assert.ok(existsSync(join(output, 'Sources/IOSDeviceRiskSignals/LocaleInfoProvider.m')));
    assert.ok(existsSync(join(output, 'LICENSE')));
    for (const name of ['.git', '.build', 'android', 'node_modules', 'example']) {
      assert.equal(existsSync(join(output, name)), false, name);
    }
    const provenance = JSON.parse(readFileSync(join(output, 'SOURCE.json'), 'utf8'));
    assert.match(provenance.commit, /^[a-f0-9]{40}$/);
    assert.equal(provenance.version, '0.1.0');
    assert.match(provenance.sha256['Package.swift'], /^[a-f0-9]{64}$/);
    assert.equal(run('0.1.0').status, 1);
    assert.equal(readFileSync(join(output, 'Package.swift'), 'utf8').startsWith('// swift-tools-version:'), true);
  } finally {
    rmSync(temp, {recursive: true, force: true});
  }
});
