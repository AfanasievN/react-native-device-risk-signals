// Cross-SDK conformance fixture verifier.
//
// Run: node scripts/verify-contract-fixtures.mjs
//
// Every SDK in the ecosystem (Android, iOS, web, and each binding) is expected to be able to emit
// the POSITIVE fixtures under contract/fixtures/ byte-compatibly and to never emit the NEGATIVE
// ones. Today each implementation is only checked by its own unit tests; these fixtures are the
// shared, implementation-independent pin for the raw-event envelope and the four probe outcome
// states.
//
// Each fixture is a pair of plain JSON files:
//   <name>.json           a complete RawSignalEvent
//   <name>.expected.json  a sidecar stating what it pins and how it must be judged
//
// Sidecar shape:
//   {
//     "fixture": "<name>.json",
//     "pins": "one sentence: what this fixture proves",
//     "expect": "valid" | "invalid",
//     // invalid only:
//     "rejected_by": "schema" | "catalog",
//     "expect_message_contains": "substring that MUST appear in a rejection message"
//   }
//
// A negative fixture that accidentally validates is a hard failure, and so is a negative fixture
// that is rejected by the wrong layer or for the wrong reason — otherwise the suite would rot into
// a set of files that pass for accidental reasons.
//
// ─────────────────────────────────────────────────────────────────────────────────────────────
// JSON Schema support
//
// contract/raw-signal-event.schema.json declares draft 2020-12 but uses only a small subset of it,
// and the repository ships no JSON Schema validator (no ajv in node_modules, and adding an npm
// dependency for a verification script is not worth it). So this file implements exactly that
// subset. It is deliberately a SUBSET-CORRECT validator: anything rejected here would also be
// rejected by a full 2020-12 validator.
//
// SUPPORTED (asserted):
//   type (single name or array of names; "integer" included for completeness)
//   const (deep equality)
//   enum  (deep equality against any member)
//   required
//   properties
//   additionalProperties (false, true, or a subschema)
//   items (single subschema applied to every array element)
//   minLength
//   oneOf (exactly one branch must match)
//
// DELIBERATELY IGNORED (annotations, no assertion performed):
//   $schema, $id, title, description, $comment, default, examples, deprecated, readOnly, writeOnly
//   format — in draft 2020-12 `format` is an annotation by default, not an assertion. The schema
//     uses "format": "date-time" on collected_at. Asserting it here would make this validator
//     STRICTER than a stock 2020-12 validator, so a fixture could be rejected here and accepted by
//     another SDK's validator. That divergence is worse than the missed check, so date-time shape
//     is not enforced. Fixtures still use real ISO-8601 UTC timestamps.
//
// NOT IMPLEMENTED: every other 2020-12 keyword ($ref, $defs, allOf, anyOf, not, if/then/else,
//   patternProperties, propertyNames, prefixItems, contains, dependentSchemas/-Required, unevaluated*,
//   pattern, maxLength, minimum/maximum/exclusive*, multipleOf, min/maxItems, uniqueItems,
//   min/maxProperties). Silently ignoring an unsupported assertion would make this verifier vacuous,
//   so assertSchemaKeywordsAreSupported() walks the schema up front and FAILS if the schema ever
//   starts using a keyword from that list. Adding a keyword to the schema therefore forces a
//   deliberate update here rather than a silent loss of coverage.
// ─────────────────────────────────────────────────────────────────────────────────────────────

import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const fixturesDirectory = path.join(root, "contract", "fixtures");
const schemaPath = path.join(root, "contract", "raw-signal-event.schema.json");
const catalogPath = path.join(root, "contract", "probe-catalog.json");

function fail(message) {
  console.error(`Contract fixture verification failed:\n- ${message}`);
  process.exit(1);
}

const ASSERTED_KEYWORDS = new Set([
  "type",
  "const",
  "enum",
  "required",
  "properties",
  "additionalProperties",
  "items",
  "minLength",
  "oneOf",
]);

const IGNORED_ANNOTATION_KEYWORDS = new Set([
  "$schema",
  "$id",
  "title",
  "description",
  "$comment",
  "default",
  "examples",
  "deprecated",
  "readOnly",
  "writeOnly",
  "format",
]);

// Keys whose value is a single subschema, and keys whose value is a map of name -> subschema.
const SUBSCHEMA_KEYS = new Set(["additionalProperties", "items"]);
const SUBSCHEMA_MAP_KEYS = new Set(["properties"]);
const SUBSCHEMA_LIST_KEYS = new Set(["oneOf"]);

/**
 * Walks the schema and reports any keyword this validator neither asserts nor deliberately ignores,
 * so the verifier can never silently under-check a schema that has grown new constraints.
 */
function assertSchemaKeywordsAreSupported(schema, pointer, unsupported) {
  if (schema === true || schema === false || schema === null || typeof schema !== "object") return;
  if (Array.isArray(schema)) return;

  for (const [keyword, value] of Object.entries(schema)) {
    if (!ASSERTED_KEYWORDS.has(keyword) && !IGNORED_ANNOTATION_KEYWORDS.has(keyword)) {
      unsupported.push(`${pointer || "/"}: unsupported schema keyword "${keyword}"`);
      continue;
    }
    if (SUBSCHEMA_MAP_KEYS.has(keyword)) {
      for (const [name, child] of Object.entries(value)) {
        assertSchemaKeywordsAreSupported(child, `${pointer}/${keyword}/${name}`, unsupported);
      }
    } else if (SUBSCHEMA_LIST_KEYS.has(keyword)) {
      value.forEach((child, index) => {
        assertSchemaKeywordsAreSupported(child, `${pointer}/${keyword}/${index}`, unsupported);
      });
    } else if (SUBSCHEMA_KEYS.has(keyword)) {
      assertSchemaKeywordsAreSupported(value, `${pointer}/${keyword}`, unsupported);
    }
  }
}

function jsonTypeOf(value) {
  if (value === null) return "null";
  if (Array.isArray(value)) return "array";
  return typeof value;
}

function deepEqual(a, b) {
  if (a === b) return true;
  if (jsonTypeOf(a) !== jsonTypeOf(b)) return false;
  if (Array.isArray(a)) return a.length === b.length && a.every((item, index) => deepEqual(item, b[index]));
  if (a !== null && typeof a === "object") {
    const aKeys = Object.keys(a);
    const bKeys = Object.keys(b);
    return aKeys.length === bKeys.length && aKeys.every((key) => deepEqual(a[key], b[key]));
  }
  return false;
}

function matchesType(value, typeName) {
  if (typeName === "integer") return typeof value === "number" && Number.isInteger(value);
  return jsonTypeOf(value) === typeName;
}

/**
 * Validates `value` against the supported 2020-12 subset. Returns an array of human-readable
 * messages; an empty array means the value validates.
 */
function validate(schema, value, pointer = "") {
  const at = pointer || "(root)";
  const errors = [];
  if (schema === true) return errors;
  if (schema === false) return [`${at}: schema is false, nothing validates here`];

  if (schema.type !== undefined) {
    const names = Array.isArray(schema.type) ? schema.type : [schema.type];
    if (!names.some((name) => matchesType(value, name))) {
      return [`${at}: expected type ${names.join(" or ")}, got ${jsonTypeOf(value)}`];
    }
  }

  if (schema.const !== undefined && !deepEqual(value, schema.const)) {
    errors.push(`${at}: expected constant ${JSON.stringify(schema.const)}, got ${JSON.stringify(value)}`);
  }

  if (schema.enum !== undefined && !schema.enum.some((candidate) => deepEqual(value, candidate))) {
    errors.push(`${at}: expected one of ${JSON.stringify(schema.enum)}, got ${JSON.stringify(value)}`);
  }

  if (schema.minLength !== undefined && typeof value === "string" && value.length < schema.minLength) {
    errors.push(`${at}: string shorter than minLength ${schema.minLength}`);
  }

  if (jsonTypeOf(value) === "object") {
    for (const required of schema.required ?? []) {
      if (!Object.hasOwn(value, required)) {
        errors.push(`${at}: missing required property "${required}"`);
      }
    }
    const declared = schema.properties ?? {};
    for (const [key, child] of Object.entries(value)) {
      if (Object.hasOwn(declared, key)) {
        errors.push(...validate(declared[key], child, `${pointer}/${key}`));
        continue;
      }
      if (schema.additionalProperties === false) {
        errors.push(`${at}: unexpected property "${key}" (additionalProperties is false)`);
      } else if (typeof schema.additionalProperties === "object" && schema.additionalProperties !== null) {
        errors.push(...validate(schema.additionalProperties, child, `${pointer}/${key}`));
      }
    }
  }

  if (jsonTypeOf(value) === "array" && schema.items !== undefined) {
    value.forEach((item, index) => {
      errors.push(...validate(schema.items, item, `${pointer}/${index}`));
    });
  }

  if (schema.oneOf !== undefined) {
    const branchErrors = schema.oneOf.map((branch) => validate(branch, value, pointer));
    const matched = branchErrors.filter((branch) => branch.length === 0).length;
    if (matched !== 1) {
      // The branch errors are folded into the message on purpose: a bare "no branch matched" makes
      // a negative fixture impossible to pin to a specific reason.
      const detail = branchErrors
        .map((branch, index) => `    [branch ${index}] ${branch.join("; ") || "matched"}`)
        .join("\n");
      errors.push(
        `${at}: matched ${matched} of ${schema.oneOf.length} oneOf branches (exactly 1 required)\n${detail}`,
      );
    }
  }

  return errors;
}

/**
 * Cross-checks an event against contract/probe-catalog.json. The JSON Schema deliberately leaves
 * both of these open — `probes.additionalProperties` accepts any probe id so new probes are additive,
 * and each success `data` sets `additionalProperties: true` so new fields are additive — so the
 * catalog, not the schema, is what pins ids and field names for a given release.
 * Only top-level `data` keys are checked; nested object shapes are already pinned by the schema.
 */
function crossCheckCatalog(event, catalogByProbeId) {
  const errors = [];
  const probes = event?.probes;
  if (jsonTypeOf(probes) !== "object") return errors;

  for (const [probeId, outcome] of Object.entries(probes)) {
    const descriptor = catalogByProbeId.get(probeId);
    if (!descriptor) {
      errors.push(`/probes/${probeId}: probe id "${probeId}" is not in contract/probe-catalog.json`);
      continue;
    }
    if (outcome?.status !== "success" || jsonTypeOf(outcome.data) !== "object") continue;
    const known = new Set(descriptor.fields ?? []);
    for (const field of Object.keys(outcome.data)) {
      if (!known.has(field)) {
        errors.push(
          `/probes/${probeId}/data: field "${field}" is not a catalog field of probe "${probeId}"`,
        );
      }
    }
  }
  return errors;
}

function readJson(filePath, failures) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    failures.push(`${path.relative(root, filePath)}: not valid JSON (${error.message})`);
    return undefined;
  }
}

// ── Load the contract ────────────────────────────────────────────────────────────────────────

for (const required of [schemaPath, catalogPath]) {
  if (!fs.existsSync(required)) fail(`${path.relative(root, required)} is missing`);
}
if (!fs.existsSync(fixturesDirectory)) {
  fail("contract/fixtures/ is missing — there are no cross-SDK conformance fixtures to verify");
}

const schema = JSON.parse(fs.readFileSync(schemaPath, "utf8"));
const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));
const catalogByProbeId = new Map((catalog.probes ?? []).map((probe) => [probe.id, probe]));

const unsupportedKeywords = [];
assertSchemaKeywordsAreSupported(schema, "", unsupportedKeywords);
if (unsupportedKeywords.length > 0) {
  console.error("Contract fixture verification failed:");
  console.error("- contract/raw-signal-event.schema.json uses schema keywords this verifier does not");
  console.error("  assert. Silently ignoring them would make the fixtures vacuous. Implement them in");
  console.error("  scripts/verify-contract-fixtures.mjs (see the header comment) before landing the");
  console.error("  schema change.");
  for (const message of unsupportedKeywords) console.error(`  - ${message}`);
  process.exit(1);
}

// ── Load the fixtures ────────────────────────────────────────────────────────────────────────

const failures = [];
const entries = fs.readdirSync(fixturesDirectory).sort();
const fixtureNames = entries.filter((name) => name.endsWith(".json") && !name.endsWith(".expected.json"));
const sidecarNames = new Set(entries.filter((name) => name.endsWith(".expected.json")));

if (fixtureNames.length === 0) {
  fail("contract/fixtures/ contains no fixtures");
}

for (const sidecarName of sidecarNames) {
  const expectedFixture = `${sidecarName.slice(0, -".expected.json".length)}.json`;
  if (!fixtureNames.includes(expectedFixture)) {
    failures.push(`contract/fixtures/${sidecarName}: describes ${expectedFixture}, which does not exist`);
  }
}

let positiveCount = 0;
let negativeCount = 0;

for (const fixtureName of fixtureNames) {
  const base = fixtureName.slice(0, -".json".length);
  const sidecarName = `${base}.expected.json`;
  const label = `contract/fixtures/${fixtureName}`;

  if (!sidecarNames.has(sidecarName)) {
    failures.push(`${label}: missing sidecar contract/fixtures/${sidecarName} stating what it pins`);
    continue;
  }

  const event = readJson(path.join(fixturesDirectory, fixtureName), failures);
  const sidecar = readJson(path.join(fixturesDirectory, sidecarName), failures);
  if (event === undefined || sidecar === undefined) continue;

  if (sidecar.fixture !== fixtureName) {
    failures.push(`contract/fixtures/${sidecarName}: "fixture" must be "${fixtureName}"`);
  }
  if (typeof sidecar.pins !== "string" || sidecar.pins.trim() === "") {
    failures.push(`contract/fixtures/${sidecarName}: "pins" must say what this fixture proves`);
  }
  if (sidecar.expect !== "valid" && sidecar.expect !== "invalid") {
    failures.push(`contract/fixtures/${sidecarName}: "expect" must be "valid" or "invalid"`);
    continue;
  }

  const schemaErrors = validate(schema, event);
  const catalogErrors = crossCheckCatalog(event, catalogByProbeId);

  if (sidecar.expect === "valid") {
    positiveCount += 1;
    for (const message of schemaErrors) failures.push(`${label}: must validate, but schema rejected it\n    ${message}`);
    for (const message of catalogErrors) {
      failures.push(`${label}: must validate, but the probe catalog rejected it\n    ${message}`);
    }
    continue;
  }

  negativeCount += 1;
  if (sidecar.rejected_by !== "schema" && sidecar.rejected_by !== "catalog") {
    failures.push(`contract/fixtures/${sidecarName}: "rejected_by" must be "schema" or "catalog"`);
    continue;
  }
  if (typeof sidecar.expect_message_contains !== "string" || sidecar.expect_message_contains === "") {
    failures.push(
      `contract/fixtures/${sidecarName}: "expect_message_contains" must pin the intended rejection reason`,
    );
    continue;
  }

  const relevant = sidecar.rejected_by === "schema" ? schemaErrors : catalogErrors;
  const irrelevant = sidecar.rejected_by === "schema" ? catalogErrors : schemaErrors;

  if (schemaErrors.length === 0 && catalogErrors.length === 0) {
    failures.push(`${label}: must be REJECTED, but it validated cleanly — the fixture no longer proves anything`);
    continue;
  }
  if (relevant.length === 0) {
    failures.push(
      `${label}: must be rejected by the ${sidecar.rejected_by}, but only the other layer rejected it\n` +
        irrelevant.map((message) => `    ${message}`).join("\n"),
    );
    continue;
  }
  if (!relevant.some((message) => message.includes(sidecar.expect_message_contains))) {
    failures.push(
      `${label}: rejected by the ${sidecar.rejected_by}, but for the wrong reason.\n` +
        `    expected a message containing: ${sidecar.expect_message_contains}\n` +
        relevant.map((message) => `    got: ${message}`).join("\n"),
    );
  }
}

// website/examples/outcome-states.json is a map of the four outcome shapes rather than an event,
// so it is not validated here; contract/fixtures/outcomes-all-four-states-in-one-event.json pins
// those same shapes inside a real envelope.
// The published examples are contract artifacts too: other SDK authors copy them, so they must
// satisfy the same schema and catalog as the fixtures. They were invalid until 2026-09-14.
const exampleDirectory = path.join(root, "website", "examples");
const exampleNames = fs.existsSync(exampleDirectory)
  ? fs.readdirSync(exampleDirectory).filter((name) => name.endsWith("-event.json")).sort()
  : [];
if (exampleNames.length === 0) {
  failures.push("website/examples/ has no *-event.json payloads to check");
}
for (const exampleName of exampleNames) {
  const label = `website/examples/${exampleName}`;
  const example = readJson(path.join(exampleDirectory, exampleName), failures);
  if (example === undefined) continue;
  for (const message of validate(schema, example)) {
    failures.push(`${label}: published example must validate, but schema rejected it\n    ${message}`);
  }
  for (const message of crossCheckCatalog(example, catalogByProbeId)) {
    failures.push(`${label}: published example must validate, but the probe catalog rejected it\n    ${message}`);
  }
}

if (failures.length > 0) {
  console.error("Contract fixture verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(
  `Contract fixtures are valid: ${positiveCount} positive and ${negativeCount} negative fixtures ` +
    `plus ${exampleNames.length} published examples, checked against ` +
    `contract/raw-signal-event.schema.json and ${catalogByProbeId.size} catalog probes.`,
);
