import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const manifestPath = path.join(root, "device-risk-signals.json");

function fail(message) {
  console.error(`Device Risk Signals ecosystem verification failed:\n- ${message}`);
  process.exit(1);
}

if (!fs.existsSync(manifestPath)) {
  fail("device-risk-signals.json is missing");
}

const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const rootPackage = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));
const failures = [];

if (manifest.schemaVersion !== 1) failures.push("schemaVersion must be 1");
if (manifest.name !== "device-risk-signals") failures.push("name must be device-risk-signals");
if (manifest.productName !== "Device Risk Signals") {
  failures.push("productName must be Device Risk Signals");
}
if (!Array.isArray(manifest.components) || manifest.components.length === 0) {
  failures.push("components must be a non-empty array");
}

const components = Array.isArray(manifest.components) ? manifest.components : [];
const componentIds = new Set();
const coordinates = new Set();
const expectedLibraryNames = {
  android: "android-device-risk-signals",
  ios: "ios-device-risk-signals",
  web: "web-device-risk-signals",
  "react-native": "react-native-device-risk-signals",
  flutter: "flutter-device-risk-signals",
  capacitor: "capacitor-device-risk-signals",
};

for (const component of components) {
  if (!component.id) {
    failures.push("every component must have an id");
    continue;
  }
  if (componentIds.has(component.id)) failures.push(`duplicate component id: ${component.id}`);
  componentIds.add(component.id);

  const expectedLibraryName = expectedLibraryNames[component.id];
  if (expectedLibraryName && component.libraryName !== expectedLibraryName) {
    failures.push(`${component.id}: libraryName must be ${expectedLibraryName}`);
  }

  if (!["sdk", "binding"].includes(component.kind)) {
    failures.push(`${component.id}: kind must be sdk or binding`);
  }
  if (!["active", "in-development", "planned"].includes(component.status)) {
    failures.push(`${component.id}: status must be active, in-development, or planned`);
  }
  if (!component.path || path.isAbsolute(component.path) || component.path.includes("..")) {
    failures.push(`${component.id}: path must be repository-relative`);
    continue;
  }

  const componentPath = path.join(root, component.path);
  const readmePath = path.join(componentPath, "README.md");
  if (!fs.existsSync(readmePath)) failures.push(`${component.id}: ${component.path}/README.md is missing`);

  if (!Array.isArray(component.platforms) || component.platforms.length === 0) {
    failures.push(`${component.id}: platforms must be a non-empty array`);
  }

  if (!component.distribution?.registry || !component.distribution?.name) {
    failures.push(`${component.id}: distribution registry and name are required`);
  } else {
    const coordinate = `${component.distribution.registry}:${component.distribution.name}`;
    if (coordinates.has(coordinate)) failures.push(`duplicate distribution coordinate: ${coordinate}`);
    coordinates.add(coordinate);
  }

  if (component.status === "planned" && fs.existsSync(path.join(componentPath, "package.json"))) {
    failures.push(`${component.id}: planned placeholder must not contain package.json`);
  }
  const dependencies = component.dependsOn ?? [];
  const targetDependencies = component.targetDependsOn ?? [];
  if (component.kind === "sdk" && [...dependencies, ...targetDependencies].length > 0) {
    failures.push(`${component.id}: a native or web SDK must not depend on a binding`);
  }
  for (const dependency of [...dependencies, ...targetDependencies]) {
    const dependencyComponent = components.find((candidate) => candidate.id === dependency);
    if (!dependencyComponent) {
      failures.push(`${component.id}: unknown dependency ${dependency}`);
    } else if (component.kind === "binding" && dependencyComponent.kind !== "sdk") {
      failures.push(`${component.id}: bindings may depend only on SDK components`);
    }
  }
}

const componentById = Object.fromEntries(components.map((component) => [component.id, component]));
if (componentById.android?.distribution?.name !== "io.github.afanasievn:android-device-risk-signals") {
  failures.push("android Maven coordinate must end with android-device-risk-signals");
}
if (componentById.ios?.distribution?.name !== "ios-device-risk-signals") {
  failures.push("iOS package name must be ios-device-risk-signals");
}
if (componentById.web?.distribution?.name !== "web-device-risk-signals") {
  failures.push("web npm package must be web-device-risk-signals");
}
if (componentById.flutter?.distribution?.name !== "flutter_device_risk_signals") {
  failures.push("Flutter pub coordinate must use the valid flutter_device_risk_signals identifier");
}
if (componentById.capacitor?.distribution?.name !== "capacitor-device-risk-signals") {
  failures.push("Capacitor npm package must be capacitor-device-risk-signals");
}

const activeReactNativeBindings = components.filter(
  (component) => component.id === "react-native" && component.kind === "binding" && component.status === "active",
);
if (activeReactNativeBindings.length !== 1) {
  failures.push("exactly one active react-native binding is required");
} else {
  const binding = activeReactNativeBindings[0];
  if (binding.path !== ".") failures.push("the transitional react-native binding must remain at the repository root");
  if (binding.distribution?.registry !== "npm" || binding.distribution?.name !== rootPackage.name) {
    failures.push("the active react-native distribution must match the root npm package");
  }
  if (rootPackage.private === true) failures.push("the active root react-native package must remain publishable");
}

for (const requiredId of ["android", "ios", "web", "react-native", "flutter", "capacitor"]) {
  if (!componentIds.has(requiredId)) failures.push(`missing ecosystem component: ${requiredId}`);
}

for (const contractFile of ["README.md", "probe-catalog.json", "raw-signal-event.schema.json"]) {
  if (!fs.existsSync(path.join(root, "contract", contractFile))) {
    failures.push(`contract/${contractFile} is missing`);
  }
}

const androidCoreBuild = fs.readFileSync(path.join(root, "sdks/android/build.gradle.kts"), "utf8");
if (/^\s*(?:api|implementation|compileOnly|runtimeOnly)\s*\(/mu.test(androidCoreBuild)) {
  failures.push("android core must not declare runtime dependencies");
}
const androidCoreSource = path.join(root, "sdks/android/src/main");
for (const relativePath of fs.readdirSync(androidCoreSource, {recursive: true})) {
  if (!relativePath.endsWith(".kt")) continue;
  const source = fs.readFileSync(path.join(androidCoreSource, relativePath), "utf8");
  if (source.includes("com.facebook.react")) {
    failures.push(`android core must not import React Native: ${relativePath}`);
  }
}

if (failures.length > 0) {
  console.error("Device Risk Signals ecosystem verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`Device Risk Signals ecosystem is valid for ${components.length} components.`);
