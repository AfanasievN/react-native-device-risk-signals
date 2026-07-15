import {readFileSync} from "node:fs";
import {join} from "node:path";
import {assertDefined} from "./testing/assertDefined";

// Android mirror of the iOS scheme drift guard: on API 30+, PackageManager cannot see a package
// unless it is declared in <queries>. KnownAppLists.kt (what the detector queries) and the
// AndroidManifest <queries> block MUST list the exact same packages, or root/hook detection
// silently misses installed managers. This asserts set-equality in BOTH directions.
const PACKAGE_DIR = join(__dirname, "../android/src/main/java/com/reactnativedeviceintel");
const KNOWN_APP_LISTS_PATH = join(PACKAGE_DIR, "KnownAppLists.kt");
const MANIFEST_PATH = join(__dirname, "../android/src/main/AndroidManifest.xml");

const PACKAGE_NAME = /^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$/;

function packagesInKotlin(source: string): Set<string> {
  const found = new Set<string>();
  for (const match of source.matchAll(/"([^"]+)"/g)) {
    const value = assertDefined(match[1]);
    if (PACKAGE_NAME.test(value)) {
      found.add(value);
    }
  }
  return found;
}

function packagesInManifest(manifest: string): Set<string> {
  const found = new Set<string>();
  for (const match of manifest.matchAll(/<package\s+android:name="([^"]+)"/g)) {
    found.add(assertDefined(match[1]));
  }
  return found;
}

describe("KnownAppLists.kt ⇔ AndroidManifest <queries>", () => {
  it("declares the exact same package set in both, in both directions", () => {
    const kotlin = packagesInKotlin(readFileSync(KNOWN_APP_LISTS_PATH, "utf8"));
    const manifest = packagesInManifest(readFileSync(MANIFEST_PATH, "utf8"));

    expect(kotlin.size).toBeGreaterThan(0);

    const inKotlinNotManifest = [...kotlin].filter((p) => !manifest.has(p)).sort();
    const inManifestNotKotlin = [...manifest].filter((p) => !kotlin.has(p)).sort();

    expect(inKotlinNotManifest).toEqual([]);
    expect(inManifestNotKotlin).toEqual([]);
  });
});
