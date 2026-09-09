import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import zlib from "node:zlib";
import {fileURLToPath} from "node:url";

// Android package-content gate ("Android release gates" in docs/MIGRATION_ROADMAP.md): prove that a
// released AAR carries only its own component implementation - no React Native or other-platform
// code, no permissions or manifest components, and no cross-component classes.
//
// Zip reading is implemented here against the central directory instead of shelling out to
// `unzip -l`/`unzip -p`: classes.jar has to be read *out of* the AAR and then listed, which with
// `unzip` needs a temporary file per component and two spawns per archive, while Node can inflate
// the nested archive in memory. It also keeps the gate working on a CI image without `unzip`.

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

// Only the AAR layout an implementation-only library may ship. `proguard.txt` is optional because a
// component without `consumerProguardFiles` (the active-probes component) legitimately omits it;
// everything else in this list is required. Anything not listed - `jni/`, `assets/`, `res/` with
// content, `libs/`, bundled sources - fails, because it means the component started shipping
// payload the gate has never reviewed.
const REQUIRED_AAR_ENTRIES = [
  "R.txt",
  "AndroidManifest.xml",
  "classes.jar",
  "META-INF/com/android/build/gradle/aar-metadata.properties",
];
const OPTIONAL_AAR_ENTRIES = ["proguard.txt"];

const ALLOWED_MANIFEST_ELEMENTS = new Set(["manifest", "uses-sdk"]);
const FORBIDDEN_MANIFEST_ELEMENTS = [
  "uses-permission",
  "uses-permission-sdk-23",
  "uses-feature",
  "queries",
  "application",
  "provider",
  "receiver",
  "service",
  "activity",
];
const EXPECTED_MIN_SDK_VERSION = 24;

// Foreign framework and other-platform bytecode: the standalone Android SDKs must not carry React
// Native, AndroidX/support, coroutines, Google or JetBrains annotation classes.
const FORBIDDEN_CLASS_PREFIXES = [
  "com/facebook/",
  "androidx/",
  "android/support/",
  "kotlinx/coroutines/",
  "com/google/",
  "org/jetbrains/annotations/",
];
const FORBIDDEN_RESOURCE_EXTENSIONS = [".js", ".ts", ".swift", ".m", ".h", ".mm", ".kt", ".java"];

// Non-class payload a Kotlin library jar may contain. Directory entries and the Kotlin module
// metadata are expected; anything else is unreviewed payload.
function isAllowedJarResource(entryName) {
  return entryName.endsWith("/") || /^META-INF\/[^/]+\.kotlin_module$/u.test(entryName);
}

// The active loopback probe is the only socket-using implementation in the ecosystem and lives in
// its own component. Assert by class name that it never lands in the core AAR. Scanning bytecode
// for socket API references (constant-pool inspection of java/net/Socket and friends) is
// deliberately out of scope: it needs a class-file parser, and the boundary this gate protects is
// the component split, which the package and class-name checks already express.
const CORE_FORBIDDEN_CLASS_PATTERN = /FridaScan/u;

function readUInt32(buffer, offset) {
  return buffer.readUInt32LE(offset);
}

function findEndOfCentralDirectory(buffer) {
  const minimum = Math.max(0, buffer.length - 22 - 0xffff);
  for (let offset = buffer.length - 22; offset >= minimum; offset -= 1) {
    if (readUInt32(buffer, offset) === 0x06054b50) return offset;
  }
  return -1;
}

// Minimal zip reader: central directory listing plus stored/deflated entry extraction. Enough for
// an AAR and its classes.jar, both of which are produced by the Android Gradle plugin.
function readZip(buffer, archiveLabel) {
  const endOffset = findEndOfCentralDirectory(buffer);
  if (endOffset < 0) {
    throw new Error(`${archiveLabel} is not a readable zip archive (no end-of-central-directory)`);
  }

  const entryCount = buffer.readUInt16LE(endOffset + 10);
  const centralDirectoryOffset = readUInt32(buffer, endOffset + 16);
  if (entryCount === 0xffff || centralDirectoryOffset === 0xffffffff) {
    throw new Error(`${archiveLabel} uses zip64 extensions, which this gate does not read`);
  }

  const entries = new Map();
  let offset = centralDirectoryOffset;
  for (let index = 0; index < entryCount; index += 1) {
    if (readUInt32(buffer, offset) !== 0x02014b50) {
      throw new Error(`${archiveLabel} has a malformed central directory at entry ${index}`);
    }
    const compressionMethod = buffer.readUInt16LE(offset + 10);
    const compressedSize = readUInt32(buffer, offset + 20);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const localHeaderOffset = readUInt32(buffer, offset + 42);
    const name = buffer.toString("utf8", offset + 46, offset + 46 + nameLength);
    entries.set(name, {name, compressionMethod, compressedSize, localHeaderOffset});
    offset += 46 + nameLength + extraLength + commentLength;
  }

  function read(entryName) {
    const entry = entries.get(entryName);
    if (!entry) throw new Error(`${archiveLabel} does not contain ${entryName}`);
    const header = entry.localHeaderOffset;
    if (readUInt32(buffer, header) !== 0x04034b50) {
      throw new Error(`${archiveLabel} has a malformed local header for ${entryName}`);
    }
    const nameLength = buffer.readUInt16LE(header + 26);
    const extraLength = buffer.readUInt16LE(header + 28);
    const dataStart = header + 30 + nameLength + extraLength;
    const data = buffer.subarray(dataStart, dataStart + entry.compressedSize);
    if (entry.compressionMethod === 0) return Buffer.from(data);
    if (entry.compressionMethod === 8) return zlib.inflateRawSync(data);
    throw new Error(
      `${archiveLabel} stores ${entryName} with unsupported compression method ${entry.compressionMethod}`,
    );
  }

  return {names: [...entries.keys()], read};
}

function readNamespace(componentPath) {
  const buildFile = path.join(root, componentPath, "build.gradle.kts");
  if (!fs.existsSync(buildFile)) return undefined;
  return fs.readFileSync(buildFile, "utf8").match(/^\s*namespace\s*=\s*"([^"]+)"/mu)?.[1];
}

function packagePrefix(namespace) {
  return `${namespace.replaceAll(".", "/")}/`;
}

const manifestPath = path.join(root, "device-risk-signals.json");
if (!fs.existsSync(manifestPath)) {
  console.error("Device Risk Signals Android AAR verification failed:");
  console.error("- device-risk-signals.json is missing; the component list cannot be resolved");
  process.exit(1);
}

// The component list, paths and artifact ids come from the ecosystem manifest rather than being
// hardcoded, so a new Android component is gated the moment it is declared.
const ecosystem = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const androidComponents = (ecosystem.components ?? []).filter(
  (component) =>
    component.kind === "sdk" &&
    (component.platforms ?? []).includes("android") &&
    typeof component.path === "string" &&
    component.path.startsWith("sdks/android"),
);

const failures = [];

if (androidComponents.length === 0) {
  failures.push(
    "device-risk-signals.json declares no Android SDK component under sdks/android; this gate would pass vacuously",
  );
}

const namespaces = new Map(
  androidComponents.map((component) => [component.id, readNamespace(component.path)]),
);

for (const component of androidComponents) {
  const artifactId = String(component.distribution?.name ?? "").split(":").pop();
  const namespace = namespaces.get(component.id);
  const label = `${component.id} (${component.path})`;

  if (!artifactId) {
    failures.push(`${label}: distribution.name is missing, so the AAR file name is unknown`);
    continue;
  }
  if (!namespace) {
    failures.push(`${label}: build.gradle.kts declares no android.namespace`);
    continue;
  }

  const aarRelativePath = path.join(component.path, "build/outputs/aar", `${artifactId}-release.aar`);
  const aarPath = path.join(root, aarRelativePath);
  if (!fs.existsSync(aarPath)) {
    failures.push(
      `${label}: ${aarRelativePath} is missing. Build it first:\n` +
        `    example/android/gradlew -p ${component.path} :assembleRelease --no-daemon`,
    );
    continue;
  }

  let aar;
  try {
    aar = readZip(fs.readFileSync(aarPath), aarRelativePath);
  } catch (error) {
    failures.push(`${label}: ${error.message}`);
    continue;
  }

  // 1. Top-level entry set.
  const allowedEntries = new Set([...REQUIRED_AAR_ENTRIES, ...OPTIONAL_AAR_ENTRIES]);
  for (const requiredEntry of REQUIRED_AAR_ENTRIES) {
    if (!aar.names.includes(requiredEntry)) {
      failures.push(`${label}: ${aarRelativePath} is missing the required entry ${requiredEntry}`);
    }
  }
  for (const entryName of aar.names) {
    if (allowedEntries.has(entryName)) continue;
    failures.push(
      `${label}: ${aarRelativePath} contains the unexpected entry ${entryName}. ` +
        `An implementation-only AAR may contain only: ${[...allowedEntries].join(", ")}`,
    );
  }

  // 2. AndroidManifest.xml: package plus uses-sdk, nothing else.
  if (aar.names.includes("AndroidManifest.xml")) {
    const rawManifest = aar.read("AndroidManifest.xml").toString("utf8");
    // XML comments are stripped first: a component manifest documents its own emptiness in prose,
    // and commented-out markup must not satisfy or trip these assertions.
    const androidManifest = rawManifest.replaceAll(/<!--[\s\S]*?-->/gu, "");

    for (const elementName of new Set(
      Array.from(androidManifest.matchAll(/<([A-Za-z][\w.:-]*)/gu), (match) => match[1]),
    )) {
      if (ALLOWED_MANIFEST_ELEMENTS.has(elementName)) continue;
      const forbidden = FORBIDDEN_MANIFEST_ELEMENTS.includes(elementName);
      failures.push(
        `${label}: AndroidManifest.xml declares <${elementName}>` +
          `${forbidden ? " which this SDK must never declare" : ""}. ` +
          `Only <manifest> and <uses-sdk> are allowed`,
      );
    }

    if (androidManifest.includes("INTERNET")) {
      failures.push(`${label}: AndroidManifest.xml references INTERNET; this SDK performs no network requests`);
    }

    const declaredPackage = androidManifest.match(/\bpackage\s*=\s*"([^"]+)"/u)?.[1];
    if (declaredPackage !== namespace) {
      failures.push(
        `${label}: AndroidManifest.xml declares package "${declaredPackage ?? "(none)"}" but the component namespace is "${namespace}"`,
      );
    }

    const minSdkVersion = androidManifest.match(/minSdkVersion\s*=\s*"(\d+)"/u)?.[1];
    if (minSdkVersion === undefined) {
      failures.push(`${label}: AndroidManifest.xml declares no <uses-sdk android:minSdkVersion>`);
    } else if (Number(minSdkVersion) !== EXPECTED_MIN_SDK_VERSION) {
      failures.push(
        `${label}: AndroidManifest.xml declares minSdkVersion ${minSdkVersion}, expected ${EXPECTED_MIN_SDK_VERSION}`,
      );
    }
  }

  // 3-5. classes.jar content: foreign frameworks, foreign platform sources, tests, package
  // boundary and the active-probe implementation.
  if (aar.names.includes("classes.jar")) {
    let classesJar;
    try {
      classesJar = readZip(aar.read("classes.jar"), `${aarRelativePath}!/classes.jar`);
    } catch (error) {
      failures.push(`${label}: ${error.message}`);
      classesJar = undefined;
    }

    // A namespace nested inside this component's namespace belongs to another component and must
    // not appear here. The reverse case - a parent component's classes inside a nested component -
    // needs no separate rule: those classes are not under this component's own prefix and are
    // reported by the package-boundary check below.
    const ownPrefix = packagePrefix(namespace);
    const nestedComponentPrefixes = androidComponents
      .filter((other) => other.id !== component.id && namespaces.get(other.id)?.startsWith(`${namespace}.`))
      .map((other) => ({id: other.id, prefix: packagePrefix(namespaces.get(other.id))}));

    for (const entryName of classesJar?.names ?? []) {
      const foreignPrefix = FORBIDDEN_CLASS_PREFIXES.find((prefix) => entryName.startsWith(prefix));
      if (foreignPrefix) {
        failures.push(
          `${label}: classes.jar contains foreign framework content ${entryName} (prefix ${foreignPrefix})`,
        );
        continue;
      }

      const foreignExtension = FORBIDDEN_RESOURCE_EXTENSIONS.find((extension) => entryName.endsWith(extension));
      if (foreignExtension) {
        failures.push(
          `${label}: classes.jar contains the other-platform or source resource ${entryName} (${foreignExtension})`,
        );
        continue;
      }

      if (/(?:Test|Tests)\.class$/u.test(entryName) || /(?:^|\/)(?:junit|org\/junit)\//u.test(entryName)) {
        failures.push(`${label}: classes.jar contains test content ${entryName}; tests must not ship to consumers`);
        continue;
      }

      if (!entryName.endsWith(".class")) {
        if (!isAllowedJarResource(entryName)) {
          failures.push(`${label}: classes.jar contains the unexpected resource ${entryName}`);
        }
        continue;
      }

      if (!entryName.startsWith(ownPrefix)) {
        failures.push(
          `${label}: classes.jar contains ${entryName}, which is outside the component package ${ownPrefix}`,
        );
        continue;
      }

      const nested = nestedComponentPrefixes.find((other) => entryName.startsWith(other.prefix));
      if (nested) {
        failures.push(
          `${label}: classes.jar contains ${entryName}, which belongs to the ${nested.id} component (${nested.prefix})`,
        );
        continue;
      }

      if (component.id === "android" && CORE_FORBIDDEN_CLASS_PATTERN.test(entryName)) {
        failures.push(
          `${label}: classes.jar contains ${entryName}; the active loopback probe must stay in the android-active-probes component`,
        );
      }
    }
  }

  // 6. aar-metadata.properties. Values are asserted to be present and parseable, never equal to a
  // fixed number: a routine Android Gradle plugin bump legitimately moves minCompileSdk, the AAR
  // format version and the minimum plugin version, and pinning them would fail the gate for no
  // package-content reason.
  const metadataEntry = "META-INF/com/android/build/gradle/aar-metadata.properties";
  if (aar.names.includes(metadataEntry)) {
    const properties = new Map(
      aar
        .read(metadataEntry)
        .toString("utf8")
        .split(/\r?\n/u)
        .filter((line) => line.includes("="))
        .map((line) => {
          const separator = line.indexOf("=");
          return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
        }),
    );

    for (const key of ["aarFormatVersion", "minCompileSdk"]) {
      const value = properties.get(key);
      if (value === undefined) {
        failures.push(`${label}: ${metadataEntry} declares no ${key}`);
      } else if (!Number.isFinite(Number.parseFloat(value))) {
        failures.push(`${label}: ${metadataEntry} declares an unparseable ${key} "${value}"`);
      }
    }
    if (!properties.get("minAndroidGradlePluginVersion")) {
      failures.push(`${label}: ${metadataEntry} declares no minAndroidGradlePluginVersion`);
    }
  }
}

if (failures.length > 0) {
  console.error("Device Risk Signals Android AAR verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(
  `Verified ${androidComponents.length} Android AARs: no foreign framework, permission or cross-component content.`,
);
