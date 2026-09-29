import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, readdirSync, existsSync} from 'node:fs';

const root = 'sdks/ios/Sources/IOSDeviceRiskSignals';
test('standalone iOS cannot acquire the legacy socket/fork implementation', () => {
  for (const file of readdirSync(root).filter(file => file.endsWith('.m'))) {
    const source = readFileSync(`${root}/${file}`, 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '');
    assert.doesNotMatch(source, /\b(socket|connect|fork|waitpid)\s*\(/, file);
    assert.doesNotMatch(source, /requestWhenInUseAuthorization|requestAlwaysAuthorization|startUpdatingLocation/, file);
  }
  assert.doesNotMatch(readFileSync(`${root}/OsIntegrityProvider.m`, 'utf8'), /openReverseEngineeringPorts/);
  assert.match(readFileSync('ios/JailbreakDetector.m', 'utf8'), /OsIntegrityProvider/);
});

test('sixteen facade methods and relocated providers remain in the native package', () => {
  const header = readFileSync(`${root}/include/DeviceRiskSignals.h`, 'utf8');
  const methods = [...header.matchAll(/^- \(NSDictionary \*\)collect(\w+);/gm)].map(m => m[1]);
  assert.equal(methods.length, 16);
  const implementation = readFileSync(`${root}/DeviceRiskSignals.m`, 'utf8');
  for (const method of methods) assert.ok(implementation.includes(`collect${method} {`), method);
  for (const name of ['HardwareInfoProvider', 'GeolocationInfoProvider', 'MediaBluetoothAppsProvider', 'SecurityPostureProvider']) {
    assert.ok(existsSync(`${root}/${name}.m`));
    assert.ok(existsSync(`${root}/include/${name}.h`));
    assert.equal(existsSync(`ios/${name}.m`), false);
    assert.match(readFileSync(`${root}/${name}.m`, 'utf8'), /RNDIRequireMainThread\(\)/);
  }
  assert.match(readFileSync('sdks/ios/Package.swift', 'utf8'), /\.copy\("PrivacyInfo.xcprivacy"\)/);
  assert.ok(existsSync(`${root}/PrivacyInfo.xcprivacy`));
});
