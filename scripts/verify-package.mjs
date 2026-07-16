import {existsSync, readFileSync} from "node:fs";
import {resolve} from "node:path";

const packageJson = JSON.parse(readFileSync(resolve("package.json"), "utf8"));
const requiredFiles = [
  packageJson.main,
  packageJson.types,
  "src/NativeDeviceIntel.ts",
  "android/build.gradle",
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

if (errors.length > 0) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log(
    `Verified ${packageJson.name}@${packageJson.version}: compiled entrypoints and native sources are present.`,
  );
}
