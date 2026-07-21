import fs from "node:fs";
import path from "node:path";
import {readProbeCatalog} from "./read-probe-catalog.mjs";

const root = process.cwd();
const signalsPath = path.join(root, "website/signals/index.html");
const jsonPath = path.join(root, "website/probe-catalog.json");
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

function renderRow(descriptor) {
  const platforms = descriptor.platforms.map((platform) => platform === "ios" ? "iOS" : "Android").join(" + ");
  const permission = descriptor.permissions.length === 0
    ? "No permission required"
    : descriptor.permissions.join("; ");
  const fields = descriptor.fields.map((field) => `<code>${escapeHtml(field)}</code>`).join("");
  const categories = descriptor.dataCategories.map((category) => tag(category)).join("");
  const notes = descriptor.notes ? `<p class="probe-notes">${escapeHtml(descriptor.notes)}</p>` : "";

  return `          <tr data-probe-id="${escapeHtml(descriptor.id)}" data-platform="${descriptor.platforms.join(" ")}">
            <td class="probe-summary"><code>${escapeHtml(descriptor.id)}</code><strong>${escapeHtml(descriptor.title)}</strong><p>${escapeHtml(descriptor.purpose)}</p></td>
            <td><div class="table-tags">${tag(platforms)}${tag(descriptor.enabledByDefault ? "default on" : "default off", descriptor.enabledByDefault ? "" : "default-off")}</div></td>
            <td><div class="table-tags">${tag(`${descriptor.sensitivity} sensitivity`, descriptor.sensitivity === "high" ? "high" : "")}${categories}</div></td>
            <td><p class="permission-copy">${escapeHtml(permission)}</p>${notes}</td>
            <td><details class="field-disclosure"><summary>${descriptor.fields.length} top-level fields</summary><div class="field-list">${fields}</div></details></td>
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

const catalog = readProbeCatalog(root);
const rows = catalog.map(renderRow).join("\n");
const currentHtml = fs.readFileSync(signalsPath, "utf8");
const expectedHtml = replaceGeneratedRows(currentHtml, rows);
const expectedJson = `${JSON.stringify({catalog_version: 1, source: "src/probeCatalog.ts", probes: catalog}, null, 2)}\n`;
const shouldWrite = process.argv.includes("--write");

if (shouldWrite) {
  fs.writeFileSync(signalsPath, expectedHtml);
  fs.writeFileSync(jsonPath, expectedJson);
  console.log(`Synchronized ${catalog.length} probes into website documentation.`);
  process.exit(0);
}

const failures = [];
if (expectedHtml !== currentHtml) failures.push("website/signals/index.html is not synchronized");
if (!fs.existsSync(jsonPath) || fs.readFileSync(jsonPath, "utf8") !== expectedJson) {
  failures.push("website/probe-catalog.json is not synchronized");
}

if (failures.length > 0) {
  console.error("Probe documentation synchronization failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  console.error("Run npm run docs:sync and commit the generated documentation.");
  process.exit(1);
}

console.log(`Probe documentation is synchronized for ${catalog.length} probes.`);
