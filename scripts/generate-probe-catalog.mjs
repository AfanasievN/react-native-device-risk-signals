import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const sourcePath = path.join(root, "contract/source/probe-catalog.source.json");
const outputPath = path.join(root, "src/probeCatalog.ts");
const relativeSourcePath = path.relative(root, sourcePath);
const relativeOutputPath = path.relative(root, outputPath);
const maxLineLength = 120;

function fail(message, details = []) {
  console.error(`Probe catalog generation failed:\n- ${message}`);
  for (const detail of details) console.error(`  ${detail}`);
  process.exit(1);
}

function pascalCase(name) {
  return name.replace(/(?:^|[_-])([a-z])/gu, (_match, letter) => letter.toUpperCase());
}

function quote(value) {
  return JSON.stringify(value);
}

function typeExpression(spec, enums) {
  if (spec.type === "string") return "string";
  if (spec.type === "boolean") return "boolean";
  if (spec.type === "enum") {
    if (!enums[spec.enum]) throw new Error(`unknown enum ${spec.enum}`);
    return pascalCase(spec.enum);
  }
  if (spec.type === "array") return `readonly ${typeExpression(spec.items, enums)}[]`;
  throw new Error(`unsupported field type ${JSON.stringify(spec.type)}`);
}

function renderStringArray(indent, key, values) {
  const inline = `${indent}${key}: [${values.map(quote).join(", ")}],`;
  if (inline.length <= maxLineLength) return [inline];
  return [`${indent}${key}: [`, ...values.map((value) => `${indent}  ${quote(value)},`), `${indent}],`];
}

function validate(source) {
  const failures = [];
  if (source.sourceVersion !== 1) failures.push("sourceVersion must be 1");
  if (!source.enums || typeof source.enums !== "object") failures.push("enums must be an object");
  if (!Array.isArray(source.descriptor?.fields) || source.descriptor.fields.length === 0) {
    failures.push("descriptor.fields must be a non-empty array");
  }
  if (!Array.isArray(source.documentation?.catalog)) {
    failures.push("documentation.catalog must be an array of doc-comment lines");
  }
  if (!Array.isArray(source.probes) || source.probes.length === 0) {
    failures.push("probes must be a non-empty array");
  }
  if (failures.length > 0) return failures;

  const descriptorFields = source.descriptor.fields;
  const knownFieldNames = new Set(descriptorFields.map((field) => field.name));
  const seenIds = new Set();

  for (const field of descriptorFields) {
    try {
      typeExpression(field, source.enums);
    } catch (error) {
      failures.push(`descriptor field ${field.name}: ${error.message}`);
    }
  }

  for (const [index, probe] of source.probes.entries()) {
    const label = probe.id ? `probes[${index}] (${probe.id})` : `probes[${index}]`;
    for (const key of Object.keys(probe)) {
      if (!knownFieldNames.has(key)) failures.push(`${label}: unknown field ${key}`);
    }
    for (const field of descriptorFields) {
      const value = probe[field.name];
      if (value === undefined) {
        if (!field.optional) failures.push(`${label}: missing required field ${field.name}`);
        continue;
      }
      if (field.type === "array") {
        if (!Array.isArray(value) || value.some((entry) => typeof entry !== "string")) {
          failures.push(`${label}: ${field.name} must be an array of strings`);
          continue;
        }
        if (field.items.type === "enum") {
          for (const entry of value) {
            if (!source.enums[field.items.enum].includes(entry)) {
              failures.push(`${label}: ${field.name} has unknown ${field.items.enum} value ${entry}`);
            }
          }
        }
        continue;
      }
      if (field.type === "boolean" && typeof value !== "boolean") {
        failures.push(`${label}: ${field.name} must be a boolean`);
        continue;
      }
      if (field.type === "string" && typeof value !== "string") {
        failures.push(`${label}: ${field.name} must be a string`);
        continue;
      }
      if (field.type === "enum" && !source.enums[field.enum].includes(value)) {
        failures.push(`${label}: ${field.name} has unknown ${field.enum} value ${value}`);
      }
    }
    if (typeof probe.id === "string") {
      if (seenIds.has(probe.id)) failures.push(`duplicate probe id: ${probe.id}`);
      seenIds.add(probe.id);
    }
  }

  return failures;
}

function render(source) {
  const lines = [];

  for (const [name, values] of Object.entries(source.enums)) {
    lines.push(`export type ${pascalCase(name)} = ${values.map(quote).join(" | ")};`);
  }

  lines.push("");
  lines.push("export type ProbeDescriptor = {");
  for (const field of source.descriptor.fields) {
    lines.push(`  ${field.name}${field.optional ? "?" : ""}: ${typeExpression(field, source.enums)};`);
  }
  lines.push("};");
  lines.push("");

  lines.push("/**");
  for (const line of source.documentation.catalog) lines.push(line === "" ? " *" : ` * ${line}`);
  lines.push(" */");

  lines.push("export const PROBE_CATALOG = [");
  for (const probe of source.probes) {
    lines.push("  {");
    for (const field of source.descriptor.fields) {
      const value = probe[field.name];
      if (value === undefined) continue;
      if (field.type === "array") {
        lines.push(...renderStringArray("    ", field.name, value));
      } else {
        lines.push(`    ${field.name}: ${JSON.stringify(value)},`);
      }
    }
    lines.push("  },");
  }
  lines.push("] as const satisfies readonly ProbeDescriptor[];");
  lines.push("");

  lines.push('export type ProbeId = (typeof PROBE_CATALOG)[number]["id"];');
  lines.push("");
  lines.push("export function getProbeDescriptor(id: string): ProbeDescriptor | undefined {");
  lines.push("  return PROBE_CATALOG.find((descriptor) => descriptor.id === id);");
  lines.push("}");

  return `${lines.join("\n")}\n`;
}

function describeDifference(expected, actual) {
  const expectedLines = expected.split("\n");
  const actualLines = actual.split("\n");
  const details = [];
  const lineCount = Math.max(expectedLines.length, actualLines.length);
  for (let index = 0; index < lineCount && details.length < 12; index += 1) {
    if (expectedLines[index] === actualLines[index]) continue;
    details.push(`line ${index + 1}:`);
    details.push(`- ${actualLines[index] ?? "<missing>"}`);
    details.push(`+ ${expectedLines[index] ?? "<missing>"}`);
  }
  if (details.length === 0) details.push("files differ only in trailing bytes");
  return details;
}

if (!fs.existsSync(sourcePath)) {
  fail(`${relativeSourcePath} is missing`);
}

let source;
try {
  source = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
} catch (error) {
  fail(`${relativeSourcePath} is not valid JSON: ${error.message}`);
}

const validationFailures = validate(source);
if (validationFailures.length > 0) {
  fail(`${relativeSourcePath} is invalid`, validationFailures);
}

const expected = render(source);
const shouldWrite = process.argv.includes("--write");

if (shouldWrite) {
  fs.writeFileSync(outputPath, expected);
  console.log(`Generated ${relativeOutputPath} from ${source.probes.length} authored probes.`);
  process.exit(0);
}

const current = fs.existsSync(outputPath) ? fs.readFileSync(outputPath, "utf8") : "";
if (current !== expected) {
  fail(
    `${relativeOutputPath} does not match ${relativeSourcePath}`,
    [
      ...describeDifference(expected, current),
      "Run node scripts/generate-probe-catalog.mjs --write and commit the generated file.",
    ],
  );
}

console.log(`Probe catalog is generated from ${relativeSourcePath} for ${source.probes.length} probes.`);
