import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';

test('release channel separates experimental packages from latest', () => {
  const run = (version, prerelease) => spawnSync(process.execPath,
    ['scripts/release-channel.mjs', version, String(prerelease)], {encoding: 'utf8'});
  assert.equal(run('0.10.0-beta.1', true).stdout.trim(), 'next');
  assert.equal(run('0.9.1', false).stdout.trim(), 'latest');
  for (const [version, prerelease] of [['0.10.0-beta.1', false], ['0.10.0', true],
    ['../bad', true], ['0.2.0-beta.01', true], ['01.2.0', false]]) {
    assert.notEqual(run(version, prerelease).status, 0, version);
  }
});
