import {execFileSync} from "node:child_process";
import {existsSync, readdirSync, readFileSync} from "node:fs";
import {dirname, join, relative, resolve, sep} from "node:path";

const packageJson = JSON.parse(readFileSync(resolve("package.json"), "utf8"));
const requiredFiles = [
  packageJson.main,
  packageJson.types,
  "src/NativeDeviceIntel.ts",
  "android/build.gradle",
  "sdks/android/src/main/kotlin/io/github/afanasievn/devicerisksignals/DeviceRiskSignals.kt",
  "ios/DeviceIntel.mm",
  "RnDeviceIntel.podspec",
  "docs/DATA_DICTIONARY.md",
  "docs/BENCHMARKS.md",
  "README.md",
  "LICENSE",
];

const errors = [];

for (const file of requiredFiles) {
  if (typeof file !== "string" || !existsSync(resolve(file))) {
    errors.push(`missing required package file: ${String(file)}`);
  }
}

if (packageJson.private === true) {
  errors.push("package.json must not set private=true");
}

if (JSON.stringify(packageJson).includes("OWNER")) {
  errors.push("package.json still contains an OWNER placeholder");
}

// ---------------------------------------------------------------------------
// Tarball-content gate.
//
// The checks above prove a file exists in this checkout. That is not what a consumer installs: the
// consumer only ever sees what `package.json` "files" lets `npm pack` put in the tarball. Those two
// sets drifted apart silently before this gate existed - dropping "sdks/android/src/main" from
// "files" broke every consumer's Android build while every check in this repository stayed green,
// and the example app could not catch it either because its node_modules entry is a symlink back to
// this checkout, so the tarball path is never exercised locally.
//
// So: ask npm what it would actually ship, and require the paths the module needs at consumer build
// time to be in that list. The required paths are derived from the build files that consume them -
// android/build.gradle for the Kotlin source trees, RnDeviceIntel.podspec for the iOS sources, the
// "exports" map for the JS entrypoints - so a build file and this gate cannot disagree: if
// android/build.gradle stops compiling a source tree, the gate stops requiring it.
// ---------------------------------------------------------------------------

const packagePath = (absolutePath) => relative(resolve("."), absolutePath).split(sep).join("/");

// Every file under a directory, minus dotfiles and node_modules (npm never packs those, so
// requiring them would fail the gate for a reason that has nothing to do with "files").
function walkFiles(relativeDir, extensions) {
  const absoluteDir = resolve(relativeDir);
  const found = [];
  const pending = [absoluteDir];

  while (pending.length > 0) {
    const current = pending.pop();
    for (const entry of readdirSync(current, {withFileTypes: true})) {
      if (entry.name.startsWith(".") || entry.name === "node_modules") continue;
      const child = join(current, entry.name);
      if (entry.isDirectory()) {
        pending.push(child);
        continue;
      }
      if (!entry.isFile()) continue;
      if (extensions && !extensions.some((extension) => entry.name.endsWith(`.${extension}`))) {
        continue;
      }
      found.push(packagePath(child));
    }
  }

  return found.sort();
}

// android/build.gradle adds each standalone Kotlin source tree to the library source set with a
// `java.srcDirs += [file("../…")]` line. Read those lines instead of restating the paths here. The
// codegen line in the same block points at "${project.buildDir}/generated/…" and carries no
// file(…) call, so it drops out on its own - it is generated at build time and is not packed.
function gradleSourceTrees() {
  const buildGradlePath = "android/build.gradle";
  if (!existsSync(resolve(buildGradlePath))) return [];

  const trees = [];
  for (const line of readFileSync(resolve(buildGradlePath), "utf8").split(/\r?\n/u)) {
    if (!line.includes("java.srcDirs")) continue;
    if (line.trimStart().startsWith("//")) continue;
    for (const match of line.matchAll(/\bfile\(\s*"([^"]+)"\s*\)/gu)) {
      const reference = match[1];
      // A Gradle-interpolated path is a build-time location, not a packed one.
      if (reference.includes("${")) continue;
      trees.push(packagePath(resolve(dirname(buildGradlePath), reference)));
    }
  }

  return trees;
}

// RnDeviceIntel.podspec owns the iOS file set: s.source_files is the compile glob and
// s.resource_bundles lists the bundled privacy manifest. Parse both rather than duplicating them.
function podspecSources() {
  const podspecPath = "RnDeviceIntel.podspec";
  const result = {globs: [], literals: [], parseErrors: []};
  if (!existsSync(resolve(podspecPath))) return result;

  const podspec = readFileSync(resolve(podspecPath), "utf8");

  const sourceFiles = podspec.match(/^\s*s\.source_files\s*=\s*"([^"]+)"/mu)?.[1];
  if (sourceFiles === undefined) {
    result.parseErrors.push(`${podspecPath} declares no s.source_files glob, so the iOS sources cannot be derived`);
  } else {
    // Supported forms: "dir/**/*.{a,b,c}" and "dir/**/*.ext".
    const braced = sourceFiles.match(/^(.+?)\/\*\*\/\*\.\{([^}]+)\}$/u);
    const single = sourceFiles.match(/^(.+?)\/\*\*\/\*\.([A-Za-z0-9]+)$/u);
    const parsed = braced ?? single;
    if (!parsed) {
      result.parseErrors.push(
        `${podspecPath} s.source_files glob "${sourceFiles}" is not of the form "dir/**/*.{ext,…}"; ` +
          "extend scripts/verify-package.mjs so the iOS sources stay derived from the podspec",
      );
    } else {
      result.globs.push({
        dir: parsed[1],
        extensions: parsed[2].split(",").map((extension) => extension.trim()),
        source: sourceFiles,
      });
    }
  }

  const resourceBundles = podspec.match(/^\s*s\.resource_bundles\s*=\s*\{([^}]*)\}/mu)?.[1] ?? "";
  for (const match of resourceBundles.matchAll(/\[([^\]]*)\]/gu)) {
    for (const literal of match[1].matchAll(/"([^"]+)"/gu)) {
      result.literals.push(literal[1]);
    }
  }

  return result;
}

// The "files" entry that would have shipped a path, so a failure says what to put back.
const filesEntries = (Array.isArray(packageJson.files) ? packageJson.files : []).filter(
  (entry) => typeof entry === "string" && !entry.startsWith("!"),
);

function coveringFilesEntry(path) {
  return filesEntries
    .map((entry) => entry.replace(/\/+$/u, ""))
    .filter((entry) => path === entry || path.startsWith(`${entry}/`))
    .sort((a, b) => b.length - a.length)[0];
}

// Every requirement is {label, paths, suggestion}: paths are concrete package-relative files, and
// suggestion is the "files" entry to add when none covers them.
const requirements = [];
const sourceTrees = gradleSourceTrees();

if (sourceTrees.length === 0) {
  errors.push(
    "android/build.gradle declares no java.srcDirs file(…) source tree; the tarball gate cannot " +
      "tell which Kotlin sources consumers compile and would pass vacuously",
  );
}

for (const tree of sourceTrees) {
  if (!existsSync(resolve(tree))) {
    errors.push(`android/build.gradle compiles ${tree}, which does not exist in this checkout`);
    continue;
  }
  requirements.push({
    label: `Kotlin source tree ${tree} (compiled by android/build.gradle)`,
    paths: walkFiles(tree),
    suggestion: tree,
  });
}

const podspec = podspecSources();
for (const parseError of podspec.parseErrors) errors.push(parseError);

for (const glob of podspec.globs) {
  if (!existsSync(resolve(glob.dir))) {
    errors.push(`RnDeviceIntel.podspec compiles "${glob.source}" but ${glob.dir}/ does not exist in this checkout`);
    continue;
  }
  requirements.push({
    label: `iOS sources matching "${glob.source}" (compiled by RnDeviceIntel.podspec)`,
    paths: walkFiles(glob.dir, glob.extensions),
    suggestion: glob.dir,
  });
}

if (podspec.literals.length > 0) {
  requirements.push({
    label: "iOS resource bundle files declared by RnDeviceIntel.podspec",
    paths: podspec.literals,
    suggestion: "ios",
  });
}

// The build file itself, the TurboModule spec codegen reads, the podspec CocoaPods reads, and every
// entrypoint the "exports" map resolves to. Taken from package.json rather than restated.
const exportTargets = [
  ...new Set(
    Array.from(JSON.stringify(packageJson.exports ?? {}).matchAll(/"\.\/([^"]+)"/gu), (match) => match[1]),
  ),
].filter((target) => existsSync(resolve(target)));

for (const path of ["android/build.gradle", "src/NativeDeviceIntel.ts", "RnDeviceIntel.podspec", ...exportTargets]) {
  requirements.push({label: path, paths: [path], suggestion: path});
}

let packedFiles;
try {
  // --dry-run writes no archive; --ignore-scripts keeps the "prepare" build from running again
  // (npm run verify has already built lib/ by the time this script runs) and keeps the call fast.
  // One invocation, reused by every requirement below.
  const output = execFileSync("npm", ["pack", "--dry-run", "--json", "--ignore-scripts"], {
    cwd: resolve("."),
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    stdio: ["ignore", "pipe", "pipe"],
  });
  const jsonStart = output.indexOf("[");
  const manifests = JSON.parse(jsonStart >= 0 ? output.slice(jsonStart) : output);
  packedFiles = new Set((manifests[0]?.files ?? []).map((entry) => entry.path));
  if (packedFiles.size === 0) {
    errors.push("npm pack --dry-run --json reported no files; the tarball contents cannot be verified");
    packedFiles = undefined;
  }
} catch (error) {
  errors.push(`npm pack --dry-run --json failed, so tarball contents were not verified: ${error.message}`);
}

let verifiedPathCount = 0;
if (packedFiles) {
  for (const requirement of requirements) {
    const missing = requirement.paths.filter((path) => !packedFiles.has(path));
    verifiedPathCount += requirement.paths.length - missing.length;
    if (missing.length === 0) continue;

    const shown = missing.slice(0, 3).join(", ");
    const more = missing.length > 3 ? ` (+${missing.length - 3} more)` : "";
    const covering = coveringFilesEntry(missing[0]);
    const remedy = covering
      ? `package.json "files" lists "${covering}", so a "!" pattern or .npmignore rule is removing it again`
      : `add the package.json "files" entry "${requirement.suggestion}", which would have shipped it`;

    errors.push(
      `npm pack would not ship ${requirement.label}: ${shown}${more}. ` +
        `Consumers build against the tarball, so this breaks their build; ${remedy}.`,
    );
  }
}

if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(
    `Verified ${packageJson.name}@${packageJson.version}: compiled entrypoints and native sources are present.`,
  );
  console.log(
    `Verified the npm pack file list ships ${verifiedPathCount} consumer build-time paths across ` +
      `${requirements.length} requirements derived from android/build.gradle, RnDeviceIntel.podspec and package.json.`,
  );
}
