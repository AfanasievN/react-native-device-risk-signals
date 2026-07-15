import {readFileSync} from "node:fs";
import {join} from "node:path";
import {IOS_APP_AUDIT_SCHEMES, IOS_JAILBREAK_QUERY_SCHEMES} from "./knownAppsSchemes";
import {assertDefined} from "./testing/assertDefined";

// Drift guard (package-internal, portable): JailbreakDetector.m / MediaBluetoothAppsProvider.m are
// the ACTUAL canOpenURL consumers and hardcode their own scheme arrays. We assert those native
// arrays match the exported TS canonical lists in both directions, so the two never silently drift.
//
// NOT checked here: that the schemes are declared in the *consuming app's* Info.plist
// LSApplicationQueriesSchemes — canOpenURL returns false for any undeclared scheme. That assertion
// is intentionally the host app's responsibility, not this framework package's: the package ships no
// host Info.plist and the path is app-specific. A consuming app should add its own test asserting
// IOS_JAILBREAK_QUERY_SCHEMES and IOS_APP_AUDIT_SCHEMES (both exported for this purpose) are a
// subset of its LSApplicationQueriesSchemes — mirroring the Android KnownAppLists.kt ⇔ manifest check.
const JAILBREAK_DETECTOR_PATH = join(__dirname, "../ios/JailbreakDetector.m");
const MEDIA_PROVIDER_PATH = join(__dirname, "../ios/MediaBluetoothAppsProvider.m");

// Extract the @"..." entries from a `static NSString *const <anchor>[] = { ... }` array in a .m file.
function schemesInNativeArray(source: string, anchor: string): Set<string> {
  const start = source.indexOf(`${anchor}[]`);
  expect(start).toBeGreaterThan(-1);
  const braceOpen = source.indexOf("{", start);
  const braceClose = source.indexOf("}", braceOpen);
  expect(braceClose).toBeGreaterThan(braceOpen);
  const block = source.slice(braceOpen, braceClose);
  const schemes = new Set<string>();
  for (const match of block.matchAll(/@"([^"]+)"/g)) {
    schemes.add(assertDefined(match[1]).trim());
  }
  return schemes;
}

function assertSameSet(native: Set<string>, ts: readonly string[]): void {
  const tsSet = new Set(ts);
  expect(native.size).toBeGreaterThan(0);
  expect([...native].filter((s) => !tsSet.has(s)).sort()).toEqual([]);
  expect([...tsSet].filter((s) => !native.has(s)).sort()).toEqual([]);
}

describe("native canOpenURL arrays ⇔ TS canonical lists", () => {
  it("JailbreakDetector.m kJailbreakSchemes == IOS_JAILBREAK_QUERY_SCHEMES", () => {
    const native = schemesInNativeArray(readFileSync(JAILBREAK_DETECTOR_PATH, "utf8"), "kJailbreakSchemes");
    assertSameSet(native, IOS_JAILBREAK_QUERY_SCHEMES);
  });

  it("MediaBluetoothAppsProvider.m kAppAuditSchemes == IOS_APP_AUDIT_SCHEMES", () => {
    const native = schemesInNativeArray(readFileSync(MEDIA_PROVIDER_PATH, "utf8"), "kAppAuditSchemes");
    assertSameSet(native, IOS_APP_AUDIT_SCHEMES);
  });
});
