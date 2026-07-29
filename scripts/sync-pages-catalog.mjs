import fs from "node:fs";
import path from "node:path";
import {readProbeCatalog} from "./read-probe-catalog.mjs";
import {readSignalContract} from "./read-signal-contract.mjs";

const root = process.cwd();
const signalsPath = path.join(root, "website/signals/index.html");
const jsonPath = path.join(root, "website/probe-catalog.json");
const schemaPath = path.join(root, "website/raw-signal-event.schema.json");
const examplesPath = path.join(root, "website/examples");
const packageJson = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));
const startMarker = "<!-- GENERATED_PROBE_ROWS_START -->";
const endMarker = "<!-- GENERATED_PROBE_ROWS_END -->";

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function tag(label, className = "") {
  return `<span class="tag${className ? ` ${className}` : ""}">${escapeHtml(label)}</span>`;
}

function renderField(probeId, field, metadata) {
  const jsonPath = `probes.${probeId}.data.${field}`;
  const fieldSearch = `${field} ${metadata.type} ${jsonPath}`.toLocaleLowerCase("en");
  return `<div class="field-contract" id="field-${escapeHtml(probeId)}-${escapeHtml(field)}" data-field-name="${escapeHtml(field)}" data-field-search="${escapeHtml(fieldSearch)}">
                  <div class="field-contract-heading"><code>${escapeHtml(field)}</code><span class="field-type">${escapeHtml(metadata.type)}</span><button class="copy-path-button" type="button" data-copy-path="${escapeHtml(jsonPath)}" aria-label="Copy JSON path for ${escapeHtml(field)}">Copy path</button></div>
                  <code class="field-path">${escapeHtml(jsonPath)}</code>
                </div>`;
}

function renderRow(descriptor, signalContract) {
  const platforms = descriptor.platforms.map((platform) => platform === "ios" ? "iOS" : "Android").join(" + ");
  const permission = descriptor.permissions.length === 0
    ? "No permission required"
    : descriptor.permissions.join("; ");
  const typeContract = signalContract[descriptor.id];
  if (!typeContract) throw new Error(`Missing signal contract for ${descriptor.id}`);
  const fields = descriptor.fields.map((field) => {
    const metadata = typeContract.fields[field];
    if (!metadata) throw new Error(`Missing TypeScript field ${typeContract.typeName}.${field}`);
    return renderField(descriptor.id, field, metadata);
  }).join("");
  const categories = descriptor.dataCategories.map((category) => tag(category)).join("");
  const notes = descriptor.notes ? `<p class="probe-notes">${escapeHtml(descriptor.notes)}</p>` : "";
  const searchable = [
    descriptor.id,
    descriptor.title,
    descriptor.purpose,
    ...descriptor.fields,
    ...descriptor.fields.map((field) => typeContract.fields[field].type),
    ...descriptor.dataCategories,
    ...descriptor.permissions,
  ].join(" ").toLocaleLowerCase("en");

  return `          <tr data-probe-id="${escapeHtml(descriptor.id)}" data-platform="${descriptor.platforms.join(" ")}" data-default="${descriptor.enabledByDefault ? "on" : "off"}" data-sensitivity="${descriptor.sensitivity}" data-permission="${descriptor.permissions.length > 0 ? "yes" : "no"}" data-search="${escapeHtml(searchable)}">
            <td class="probe-summary"><code>${escapeHtml(descriptor.id)}</code><strong>${escapeHtml(descriptor.title)}</strong><p>${escapeHtml(descriptor.purpose)}</p></td>
            <td><div class="table-tags">${tag(platforms)}${tag(descriptor.enabledByDefault ? "default on" : "default off", descriptor.enabledByDefault ? "" : "default-off")}</div></td>
            <td><div class="table-tags">${tag(`${descriptor.sensitivity} sensitivity`, descriptor.sensitivity === "high" ? "high" : "")}${categories}</div></td>
            <td><p class="permission-copy">${escapeHtml(permission)}</p>${notes}</td>
            <td><details class="field-disclosure"><summary>${descriptor.fields.length} typed top-level fields</summary><div class="field-list">${fields}</div></details></td>
          </tr>`;
}

function replaceGeneratedRows(html, rows) {
  let start = html.indexOf(startMarker);
  let end = html.indexOf(endMarker);
  if (start === -1 || end === -1 || end < start) {
    const catalogStart = html.indexOf('        <div class="catalog">');
    if (catalogStart === -1) {
      throw new Error(`Missing generated row markers in ${path.relative(root, signalsPath)}`);
    }

    const divPattern = /<\/?div\b[^>]*>/g;
    let depth = 0;
    let catalogEnd = -1;
    for (const match of html.slice(catalogStart).matchAll(divPattern)) {
      depth += match[0].startsWith("</") ? -1 : 1;
      if (depth === 0) {
        catalogEnd = catalogStart + match.index + match[0].length;
        break;
      }
    }
    if (catalogEnd === -1) throw new Error("Could not find the end of the legacy probe card catalog");

    const table = `        <div class="catalog-table-wrap" tabindex="0" aria-label="Complete public probe catalog">
          <table class="probe-contract-table">
            <thead><tr><th>Probe and purpose</th><th>Availability</th><th>Data classification</th><th>Permission behavior and notes</th><th>Possible success data</th></tr></thead>
            <tbody>
          ${startMarker}
          ${endMarker}
            </tbody>
          </table>
        </div>`;
    html = `${html.slice(0, catalogStart)}${table}${html.slice(catalogEnd)}`;
    start = html.indexOf(startMarker);
    end = html.indexOf(endMarker);
  }

  return `${html.slice(0, start + startMarker.length)}\n${rows}\n          ${html.slice(end)}`;
}

function outcomeSchema(successDataSchema) {
  return {
    oneOf: [
      {
        type: "object",
        properties: {
          status: {const: "success"},
          data: successDataSchema,
        },
        required: ["status", "data"],
        additionalProperties: false,
      },
      {
        type: "object",
        properties: {
          status: {const: "skipped"},
          reason: {type: "string"},
        },
        required: ["status", "reason"],
        additionalProperties: false,
      },
      {
        type: "object",
        properties: {status: {const: "timeout"}},
        required: ["status"],
        additionalProperties: false,
      },
      {
        type: "object",
        properties: {
          status: {const: "error"},
          error: {type: "string"},
        },
        required: ["status", "error"],
        additionalProperties: false,
      },
    ],
  };
}

function successDataSchema(descriptor, metadata) {
  const properties = Object.fromEntries(
    descriptor.fields.map((field) => [field, metadata.fields[field].schema]),
  );
  const required = descriptor.fields.filter((field) => !metadata.fields[field].optional);
  return {
    type: "object",
    properties,
    ...(required.length > 0 ? {required} : {}),
    additionalProperties: true,
  };
}

function buildEventSchema(catalog, signalContract) {
  const knownProbeSchemas = Object.fromEntries(
    catalog.map((descriptor) => [
      descriptor.id,
      outcomeSchema(successDataSchema(descriptor, signalContract[descriptor.id])),
    ]),
  );
  return {
    $schema: "https://json-schema.org/draft/2020-12/schema",
    $id: "https://afanasievn.github.io/react-native-device-risk-signals/raw-signal-event.schema.json",
    title: "React Native Device Risk Signals raw event",
    description: "Exact SDK event envelope. Probe IDs and success data are additive.",
    type: "object",
    properties: {
      session_id: {type: "string", minLength: 1},
      client_id: {type: "string", minLength: 1},
      event_type: {const: "device_intel_collection"},
      schema_version: {const: 1},
      collected_at: {type: "string", format: "date-time"},
      probes: {
        type: "object",
        properties: knownProbeSchemas,
        additionalProperties: outcomeSchema({
          type: "object",
          additionalProperties: true,
        }),
      },
    },
    required: ["session_id", "event_type", "schema_version", "collected_at", "probes"],
    additionalProperties: false,
  };
}

const catalog = readProbeCatalog(root);
const signalContract = readSignalContract(root);
const rows = catalog.map((descriptor) => renderRow(descriptor, signalContract)).join("\n");
const totalFieldCount = catalog.reduce((total, descriptor) => total + descriptor.fields.length, 0);
const currentHtml = fs.readFileSync(signalsPath, "utf8");
const expectedHtml = replaceGeneratedRows(currentHtml, rows)
  .replace(/Search all \d+ fields/g, `Search all ${totalFieldCount} fields`)
  .replace(/Showing all \d+ probes and \d+ fields\./g, `Showing all ${catalog.length} probes and ${totalFieldCount} fields.`);
const publishedProbes = catalog.map((descriptor) => ({
  ...descriptor,
  typeName: signalContract[descriptor.id].typeName,
  fieldTypes: Object.fromEntries(
    descriptor.fields.map((field) => [field, signalContract[descriptor.id].fields[field].type]),
  ),
  fieldSchemas: Object.fromEntries(
    descriptor.fields.map((field) => [field, signalContract[descriptor.id].fields[field].schema]),
  ),
  optionalFields: descriptor.fields.filter((field) => signalContract[descriptor.id].fields[field].optional),
}));
const expectedJson = `${JSON.stringify({
  catalog_version: 2,
  sdk_version: packageJson.version,
  source: "src/probeCatalog.ts",
  type_source: "src/NativeDeviceIntel.ts and src/probes/runtimeProbe.ts",
  probes: publishedProbes,
}, null, 2)}\n`;
const expectedSchema = `${JSON.stringify(buildEventSchema(catalog, signalContract), null, 2)}\n`;
const expectedExamples = {
  "android-event.json": {
    session_id: "checkout_01JEXAMPLE",
    client_id: "account_12345",
    event_type: "device_intel_collection",
    schema_version: 1,
    collected_at: "2026-07-29T12:00:00.000Z",
    probes: {
      device_identity: {
        status: "success",
        data: {
          manufacturer: "Google",
          model: "Pixel 8",
          systemName: "Android",
          systemVersion: "15",
          isTablet: false,
          androidBuild: {
            fingerprint: "google/example/device:user/release-keys",
            supportedAbis: ["arm64-v8a", "armeabi-v7a"],
            sdkInt: 35,
          },
        },
      },
      network: {
        status: "success",
        data: {
          isConnected: true,
          connectionType: "wifi",
          interfaceNames: ["wlan0"],
          localIpAddresses: ["192.0.2.10"],
        },
      },
      transaction_safety: {status: "skipped", reason: "disabled"},
    },
  },
  "ios-event.json": {
    session_id: "login_01JEXAMPLE",
    event_type: "device_intel_collection",
    schema_version: 1,
    collected_at: "2026-07-29T12:00:00.000Z",
    probes: {
      device_identity: {
        status: "success",
        data: {
          model: "iPhone",
          systemName: "iOS",
          systemVersion: "18.5",
          isTablet: false,
          isIosAppOnMac: false,
          isMacCatalystApp: false,
        },
      },
      application: {
        status: "success",
        data: {
          appVersion: "4.2.0",
          appBuild: "420",
          bundleId: "com.example.app",
          receiptPresent: true,
          grantedPermissions: ["notifications"],
        },
      },
      geolocation: {status: "error", error: "location provider unavailable"},
    },
  },
  "outcome-states.json": {
    success: {status: "success", data: {exampleBoolean: false, exampleNumber: 42, exampleStrings: ["a", "b"]}},
    skipped: {status: "skipped", reason: "disabled"},
    timeout: {status: "timeout"},
    error: {status: "error", error: "native read failed"},
  },
};
const shouldWrite = process.argv.includes("--write");

if (shouldWrite) {
  fs.writeFileSync(signalsPath, expectedHtml);
  fs.writeFileSync(jsonPath, expectedJson);
  fs.writeFileSync(schemaPath, expectedSchema);
  fs.mkdirSync(examplesPath, {recursive: true});
  for (const [fileName, example] of Object.entries(expectedExamples)) {
    fs.writeFileSync(path.join(examplesPath, fileName), `${JSON.stringify(example, null, 2)}\n`);
  }
  console.log(`Synchronized ${catalog.length} probes into website documentation.`);
  process.exit(0);
}

const failures = [];
if (expectedHtml !== currentHtml) failures.push("website/signals/index.html is not synchronized");
if (!fs.existsSync(jsonPath) || fs.readFileSync(jsonPath, "utf8") !== expectedJson) {
  failures.push("website/probe-catalog.json is not synchronized");
}
if (!fs.existsSync(schemaPath) || fs.readFileSync(schemaPath, "utf8") !== expectedSchema) {
  failures.push("website/raw-signal-event.schema.json is not synchronized");
}
for (const [fileName, example] of Object.entries(expectedExamples)) {
  const filePath = path.join(examplesPath, fileName);
  const expected = `${JSON.stringify(example, null, 2)}\n`;
  if (!fs.existsSync(filePath) || fs.readFileSync(filePath, "utf8") !== expected) {
    failures.push(`website/examples/${fileName} is not synchronized`);
  }
}

if (failures.length > 0) {
  console.error("Probe documentation synchronization failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  console.error("Run npm run docs:sync and commit the generated documentation.");
  process.exit(1);
}

console.log(`Probe documentation is synchronized for ${catalog.length} probes.`);
