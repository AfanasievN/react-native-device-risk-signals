// Signal type drift guard: neutral authoring source vs. the TypeScript the binding exposes.
//
// Run: node scripts/verify-signal-types.mjs
//
// Field TYPES are moving out of the React Native package: contract/source/signal-types.source.json
// is the framework-neutral authoring source from which the published `fieldTypes`, `fieldSchemas`
// and `optionalFields` of contract/probe-catalog.json are generated. src/NativeDeviceIntel.ts stays
// hand-written, because React Native codegen consumes it. Those two therefore describe the same
// shape from two places, and nothing forces them to agree: adding, removing, renaming or
// re-optionalizing a field in the TypeScript would leave the published contract silently describing
// the old shape. This script is that force.
//
// WHAT THIS PROVES
//   Every probe in contract/probe-catalog.json has an entry in the neutral source, and vice versa.
//   For every probe: the same typeName, exactly the same field names, and for each field the same
//   published type string, the same optionality, and the same JSON Schema fragment as the
//   TypeScript declarations parsed by scripts/read-signal-contract.mjs (src/NativeDeviceIntel.ts
//   and src/probes/runtimeProbe.ts). So the shape the published contract CLAIMS is the shape the
//   binding's TypeScript actually DECLARES.
//
// WHAT THIS DOES NOT PROVE
//   Nothing about any native implementation. It never looks at Kotlin, Objective-C++, Swift or the
//   standalone SDKs under sdks/, so it cannot tell you whether a declared field is ever populated,
//   whether the native side emits a value of the declared type, or whether a platform stub exists.
//   Native/TypeScript parity is scripts/verify-native-contract.mjs's job; emitted-payload
//   conformance is scripts/verify-contract-fixtures.mjs's job. It also says nothing about whether a
//   type is a GOOD description of the data, only that both descriptions of it agree — and nothing
//   about probe metadata (platforms, sensitivity, permissions), which is
//   contract/source/probe-catalog.source.json's territory.
//
// COMPARISON RULES (see the report at the bottom of this comment block for why)
//   - Field ORDER is not compared. The TypeScript declaration order and the catalog field order
//     already disagree for two probes (os_integrity_frida_scan, runtime_timing), so order carries
//     no shared meaning here; names are compared as a set.
//   - Object KEY order inside a schema fragment is not compared: it carries no JSON Schema meaning.
//     ARRAY element order (`required`, `anyOf`, `enum`) IS compared, because a JSON array is
//     ordered and reordering it changes the generated artifact.
//   - A field with no `optional` key in the source means `optional: false`. Absence is read as a
//     definite claim, not as "unspecified".

import fs from "node:fs";
import path from "node:path";

import {readSignalContract} from "./read-signal-contract.mjs";

const root = process.cwd();
const sourcePath = path.join(root, "contract", "source", "signal-types.source.json");
const catalogPath = path.join(root, "contract", "probe-catalog.json");
const sourceLabel = "contract/source/signal-types.source.json";
const typeScriptLabel = "src/NativeDeviceIntel.ts";

function fail(message) {
  console.error(`Signal type verification failed:\n- ${message}`);
  process.exit(1);
}

function jsonTypeOf(value) {
  if (value === null) return "null";
  if (Array.isArray(value)) return "array";
  return typeof value;
}

function isPlainObject(value) {
  return jsonTypeOf(value) === "object";
}

/** Short, single-line rendering of a value, so a schema mismatch never dumps a whole object. */
function summarize(value) {
  if (value === undefined) return "(absent)";
  if (isPlainObject(value)) {
    const keys = Object.keys(value);
    return keys.length === 0 ? "{}" : `object with keys {${keys.sort().join(", ")}}`;
  }
  if (Array.isArray(value)) return `array of ${value.length}`;
  const text = JSON.stringify(value);
  return text.length > 60 ? `${text.slice(0, 57)}...` : text;
}

/**
 * Returns the first structural difference between two JSON Schema fragments as
 * {pointer, expected, actual}, or undefined when they are equivalent. Object key order is ignored;
 * array element order is significant.
 */
function firstSchemaDifference(expected, actual, pointer = "") {
  const at = pointer === "" ? "" : pointer;
  if (jsonTypeOf(expected) !== jsonTypeOf(actual)) {
    return {pointer: at, expected: summarize(expected), actual: summarize(actual)};
  }
  if (Array.isArray(expected)) {
    if (expected.length !== actual.length) {
      return {
        pointer: at,
        expected: `array of ${expected.length}`,
        actual: `array of ${actual.length}`,
      };
    }
    for (let index = 0; index < expected.length; index += 1) {
      const difference = firstSchemaDifference(expected[index], actual[index], `${at}/${index}`);
      if (difference) return difference;
    }
    return undefined;
  }
  if (isPlainObject(expected)) {
    const keys = [...new Set([...Object.keys(expected), ...Object.keys(actual)])].sort();
    for (const key of keys) {
      const childPointer = `${at}/${key.replace(/~/gu, "~0").replace(/\//gu, "~1")}`;
      if (!Object.hasOwn(expected, key) || !Object.hasOwn(actual, key)) {
        return {
          pointer: childPointer,
          expected: Object.hasOwn(expected, key) ? summarize(expected[key]) : "(absent)",
          actual: Object.hasOwn(actual, key) ? summarize(actual[key]) : "(absent)",
        };
      }
      const difference = firstSchemaDifference(expected[key], actual[key], childPointer);
      if (difference) return difference;
    }
    return undefined;
  }
  if (expected !== actual) {
    return {pointer: at, expected: summarize(expected), actual: summarize(actual)};
  }
  return undefined;
}

function pointerLabel(pointer) {
  return pointer === "" ? "(schema root)" : pointer;
}

// ── Load both sides ──────────────────────────────────────────────────────────────────────────

if (!fs.existsSync(sourcePath)) {
  fail(
    `${sourceLabel} is missing. The published fieldTypes/fieldSchemas/optionalFields are generated ` +
      `from it, so without it nothing pins the published contract against ${typeScriptLabel}.`,
  );
}
if (!fs.existsSync(catalogPath)) {
  fail("contract/probe-catalog.json is missing — there is no probe list to check the source against");
}

let source;
try {
  source = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
} catch (error) {
  fail(`${sourceLabel}: not valid JSON (${error.message})`);
}

const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const catalogProbeIds = (catalog.probes ?? []).map((probe) => probe.id);
if (catalogProbeIds.length === 0) {
  fail("contract/probe-catalog.json lists no probes");
}

let typeScriptContract;
try {
  typeScriptContract = readSignalContract(root);
} catch (error) {
  fail(`could not read the TypeScript signal types: ${error.message}`);
}

// ── Shape of the authoring source ────────────────────────────────────────────────────────────

const failures = [];

if (source.sourceVersion !== 1) {
  fail(`${sourceLabel}: "sourceVersion" must be 1, got ${JSON.stringify(source.sourceVersion)}`);
}
if (!isPlainObject(source.probeTypes)) {
  fail(`${sourceLabel}: "probeTypes" must be an object keyed by probe id`);
}

const sourceProbeIds = Object.keys(source.probeTypes);
for (const probeId of sourceProbeIds) {
  const entry = source.probeTypes[probeId];
  if (!isPlainObject(entry)) {
    failures.push(`${sourceLabel}: probe "${probeId}" must be an object with "typeName" and "fields"`);
    continue;
  }
  if (typeof entry.typeName !== "string" || entry.typeName === "") {
    failures.push(`${sourceLabel}: probe "${probeId}" must declare a non-empty "typeName"`);
  }
  if (!isPlainObject(entry.fields)) {
    failures.push(`${sourceLabel}: probe "${probeId}" must declare "fields" as an object keyed by field name`);
    continue;
  }
  for (const [fieldName, field] of Object.entries(entry.fields)) {
    if (!isPlainObject(field)) {
      failures.push(`${sourceLabel}: field "${probeId}.${fieldName}" must be an object`);
      continue;
    }
    if (typeof field.type !== "string" || field.type === "") {
      failures.push(`${sourceLabel}: field "${probeId}.${fieldName}" must declare a non-empty "type" string`);
    }
    if (field.optional !== undefined && typeof field.optional !== "boolean") {
      failures.push(`${sourceLabel}: field "${probeId}.${fieldName}" has a non-boolean "optional"`);
    }
    if (!isPlainObject(field.schema)) {
      failures.push(`${sourceLabel}: field "${probeId}.${fieldName}" must declare a "schema" object`);
    }
  }
}

if (failures.length > 0) {
  console.error("Signal type verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

// ── Probe ids: source vs. catalog ────────────────────────────────────────────────────────────

for (const probeId of sourceProbeIds) {
  if (!catalogProbeIds.includes(probeId)) {
    failures.push(
      `${sourceLabel}: describes probe "${probeId}", which is not in contract/probe-catalog.json — ` +
        `remove it, or add the probe to contract/source/probe-catalog.source.json`,
    );
  }
}
for (const probeId of catalogProbeIds) {
  if (!sourceProbeIds.includes(probeId)) {
    failures.push(
      `contract/probe-catalog.json lists probe "${probeId}", which has no entry in ${sourceLabel} — ` +
        `the published contract would have no field types for it`,
    );
  }
}

// ── Probe ids: source vs. TypeScript ─────────────────────────────────────────────────────────

const typeScriptProbeIds = Object.keys(typeScriptContract);
for (const probeId of typeScriptProbeIds) {
  if (!sourceProbeIds.includes(probeId)) {
    failures.push(
      `probe "${probeId}": declared in ${typeScriptLabel} (type ${typeScriptContract[probeId].typeName}) ` +
        `but absent from ${sourceLabel}`,
    );
  }
}
for (const probeId of sourceProbeIds) {
  if (!typeScriptProbeIds.includes(probeId)) {
    failures.push(
      `probe "${probeId}": present in ${sourceLabel} but not declared in ${typeScriptLabel} — ` +
        `no TypeScript signal type maps to this probe id`,
    );
  }
}

// ── Field-by-field comparison ────────────────────────────────────────────────────────────────

let probesChecked = 0;
let fieldsChecked = 0;

for (const probeId of typeScriptProbeIds) {
  const typeScriptProbe = typeScriptContract[probeId];
  const sourceProbe = source.probeTypes[probeId];
  if (!sourceProbe) continue;
  probesChecked += 1;

  if (sourceProbe.typeName !== typeScriptProbe.typeName) {
    failures.push(
      `probe "${probeId}": typeName is "${sourceProbe.typeName}" in ${sourceLabel} but ` +
        `"${typeScriptProbe.typeName}" in ${typeScriptLabel}`,
    );
  }

  const typeScriptFieldNames = Object.keys(typeScriptProbe.fields);
  const sourceFieldNames = Object.keys(sourceProbe.fields);

  for (const fieldName of typeScriptFieldNames) {
    if (!Object.hasOwn(sourceProbe.fields, fieldName)) {
      failures.push(
        `field "${probeId}.${fieldName}": declared in ${typeScriptLabel} ` +
          `(${typeScriptProbe.typeName}.${fieldName}: ${typeScriptProbe.fields[fieldName].type}) but absent ` +
          `from ${sourceLabel} — the published contract would not describe it`,
      );
    }
  }
  for (const fieldName of sourceFieldNames) {
    if (!Object.hasOwn(typeScriptProbe.fields, fieldName)) {
      failures.push(
        `field "${probeId}.${fieldName}": described in ${sourceLabel} but not declared in ` +
          `${typeScriptLabel} (${typeScriptProbe.typeName}) — the published contract would describe a ` +
          `field the binding does not expose`,
      );
    }
  }

  for (const fieldName of typeScriptFieldNames) {
    const sourceField = sourceProbe.fields[fieldName];
    if (!sourceField) continue;
    const typeScriptField = typeScriptProbe.fields[fieldName];
    fieldsChecked += 1;

    if (sourceField.type !== typeScriptField.type) {
      failures.push(
        `field "${probeId}.${fieldName}": type is "${sourceField.type}" in ${sourceLabel} but ` +
          `"${typeScriptField.type}" in ${typeScriptLabel}`,
      );
    }

    const sourceOptional = sourceField.optional === true;
    if (sourceOptional !== typeScriptField.optional) {
      failures.push(
        `field "${probeId}.${fieldName}": ${sourceLabel} says optional=${sourceOptional} but ` +
          `${typeScriptLabel} declares it ${typeScriptField.optional ? "optional (`?`)" : "required (no `?`)"}`,
      );
    }

    const difference = firstSchemaDifference(typeScriptField.schema, sourceField.schema);
    if (difference) {
      failures.push(
        `field "${probeId}.${fieldName}": schema differs at ${pointerLabel(difference.pointer)} — ` +
          `${typeScriptLabel} has ${difference.expected}, ${sourceLabel} has ${difference.actual}`,
      );
    }
  }
}

if (failures.length > 0) {
  console.error("Signal type verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(
  `Signal types are in sync: ${probesChecked} probes and ${fieldsChecked} fields match between ` +
    `${sourceLabel} and the TypeScript signal types (${typeScriptLabel}, src/probes/runtimeProbe.ts).`,
);
