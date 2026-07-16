import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import {fileURLToPath} from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

function uniqueMatches(source, pattern) {
  return new Set(Array.from(source.matchAll(pattern), (match) => match[1]));
}

const specSource = read("src/NativeDeviceIntel.ts");
const specBody = specSource.match(/export interface Spec extends TurboModule \{([\s\S]*?)\n\}/)?.[1];
if (!specBody) throw new Error("Could not locate the TurboModule Spec interface.");

const expected = uniqueMatches(specBody, /^\s*([A-Za-z][A-Za-z0-9]*):\s*\(\)\s*=>/gm);
const android = uniqueMatches(
  read("android/src/main/java/com/reactnativedeviceintel/DeviceIntelModule.kt"),
  /^\s*override fun ([A-Za-z][A-Za-z0-9]*)\s*\(/gm,
);
const ios = uniqueMatches(read("ios/DeviceIntel.mm"), /^-\s*\([^)]*\)\s*([A-Za-z][A-Za-z0-9]*)/gm);

function missingFrom(actual) {
  return [...expected].filter((method) => !actual.has(method));
}

const missingAndroid = missingFrom(android);
const missingIos = missingFrom(ios);
if (missingAndroid.length > 0 || missingIos.length > 0) {
  if (missingAndroid.length > 0) console.error(`Android is missing: ${missingAndroid.join(", ")}`);
  if (missingIos.length > 0) console.error(`iOS is missing: ${missingIos.join(", ")}`);
  process.exit(1);
}

console.log(`Verified ${expected.size} TurboModule methods across TypeScript, Kotlin, and Objective-C++.`);
