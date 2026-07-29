import fs from "node:fs";
import path from "node:path";
import {readProbeCatalog} from "./read-probe-catalog.mjs";

const root = process.cwd();
const siteRoot = path.join(root, "website");
const expectedPages = [
  "index.html",
  "signals/index.html",
  "integration/index.html",
  "backend/index.html",
  "recipes/index.html",
  "compatibility/index.html",
  "versions/index.html",
  "privacy/index.html",
  "risk-teams/index.html",
  "faq/index.html",
  "404.html",
];

const failures = [];

function assert(condition, message) {
  if (!condition) failures.push(message);
}

function resolveLocalHref(pagePath, href) {
  if (href.startsWith("/react-native-device-risk-signals/")) {
    return path.join(siteRoot, href.slice("/react-native-device-risk-signals/".length));
  }

  const withoutFragment = href.split("#", 1)[0];
  return path.resolve(path.dirname(pagePath), withoutFragment);
}

for (const relativePath of expectedPages) {
  const pagePath = path.join(siteRoot, relativePath);
  assert(fs.existsSync(pagePath), `Missing website/${relativePath}`);
  if (!fs.existsSync(pagePath)) continue;

  const html = fs.readFileSync(pagePath, "utf8");
  assert(/<html lang="en">/.test(html), `${relativePath}: missing lang=en`);
  assert(/<title>[^<]{10,}<[\/]title>/.test(html), `${relativePath}: missing descriptive title`);
  assert(/<meta name="description" content="[^"]{50,}"/.test(html), `${relativePath}: missing meta description`);
  assert(/<link rel="canonical" href="https:[\/]\/afanasievn\.github\.io\/react-native-device-risk-signals\//.test(html), `${relativePath}: missing canonical URL`);
  assert(/<meta property="og:title"/.test(html), `${relativePath}: missing Open Graph title`);
  assert(!/[—–]/.test(html), `${relativePath}: contains a forbidden typographic dash`);

  for (const match of html.matchAll(/href="([^"]+)"/g)) {
    const href = match[1];
    if (/^(https?:|mailto:|#)/.test(href)) continue;
    const target = resolveLocalHref(pagePath, href);
    const candidate = target.endsWith(path.sep) ? path.join(target, "index.html") : target;
    assert(fs.existsSync(candidate), `${relativePath}: broken local link ${href}`);
  }
}

for (const asset of [
  "assets/styles.css",
  "assets/site.js",
  "probe-catalog.json",
  "raw-signal-event.schema.json",
  "examples/android-event.json",
  "examples/ios-event.json",
  "examples/outcome-states.json",
  "robots.txt",
  "sitemap.xml",
  ".nojekyll",
]) {
  assert(fs.existsSync(path.join(siteRoot, asset)), `Missing website/${asset}`);
}

const probeCatalog = readProbeCatalog(root);
const signalsPath = path.join(siteRoot, "signals/index.html");
if (fs.existsSync(signalsPath)) {
  const signals = fs.readFileSync(signalsPath, "utf8");
  assert(/type="search"/.test(signals), "Signal catalog must expose a text search");
  assert(/data-filter="default-on"/.test(signals), "Signal catalog must filter default-on probes");
  assert(/data-filter="default-off"/.test(signals), "Signal catalog must filter default-off probes");
  assert(/data-filter="permission"/.test(signals), "Signal catalog must filter permission-aware probes");
  assert(/data-filter="high"/.test(signals), "Signal catalog must filter high-sensitivity probes");
  const documentedRows = new Map(
    [...signals.matchAll(/<tr\s+data-probe-id="([^"]+)"[^>]*>([\s\S]*?)<\/tr>/g)].map((match) => [match[1], match[2]]),
  );

  assert(documentedRows.size === probeCatalog.length, `Signal table must document all ${probeCatalog.length} probes`);
  for (const descriptor of probeCatalog) {
    const row = documentedRows.get(descriptor.id);
    assert(Boolean(row), `Signal table is missing probe ${descriptor.id}`);
    if (!row) continue;
    for (const field of descriptor.fields) {
      assert(row.includes(`data-field-name="${field}"`), `Signal table is missing ${descriptor.id}.${field}`);
      assert(
        row.includes(`data-copy-path="probes.${descriptor.id}.data.${field}"`),
        `Signal table is missing copy path for ${descriptor.id}.${field}`,
      );
    }
  }
}

const catalogJsonPath = path.join(siteRoot, "probe-catalog.json");
if (fs.existsSync(catalogJsonPath)) {
  const publishedCatalog = JSON.parse(fs.readFileSync(catalogJsonPath, "utf8"));
  assert(publishedCatalog.catalog_version === 2, "Published probe catalog must declare catalog_version 2");
  assert(publishedCatalog.source === "src/probeCatalog.ts", "Published probe catalog must identify its source of truth");
  assert(publishedCatalog.sdk_version === JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8")).version, "Published probe catalog must identify the SDK version");
  assert(publishedCatalog.probes.length === probeCatalog.length, "Published probe catalog must include every probe");
  for (const descriptor of probeCatalog) {
    const published = publishedCatalog.probes.find((probe) => probe.id === descriptor.id);
    assert(Boolean(published), `Published probe catalog is missing ${descriptor.id}`);
    if (!published) continue;
    assert(JSON.stringify(published.fields) === JSON.stringify(descriptor.fields), `${descriptor.id}: field names drifted`);
    for (const field of descriptor.fields) {
      assert(typeof published.fieldTypes?.[field] === "string", `${descriptor.id}.${field}: missing field type`);
      assert(Boolean(published.fieldSchemas?.[field]), `${descriptor.id}.${field}: missing JSON Schema`);
    }
  }
}

const eventSchemaPath = path.join(siteRoot, "raw-signal-event.schema.json");
if (fs.existsSync(eventSchemaPath)) {
  const schema = JSON.parse(fs.readFileSync(eventSchemaPath, "utf8"));
  assert(schema.$schema === "https://json-schema.org/draft/2020-12/schema", "Event schema must use JSON Schema 2020-12");
  assert(schema.properties?.probes?.type === "object", "Event schema must describe the probes object");
  for (const descriptor of probeCatalog) {
    assert(Boolean(schema.properties.probes.properties?.[descriptor.id]), `Event schema is missing ${descriptor.id}`);
  }
}

const siteScriptPath = path.join(siteRoot, "assets/site.js");
if (fs.existsSync(siteScriptPath)) {
  const siteScript = fs.readFileSync(siteScriptPath, "utf8");
  assert(/function revealCatalogFieldFromHash/.test(siteScript), "BUG-R1: catalog deep links must reveal fields inside closed details");
  assert(/addEventListener\("hashchange", revealCatalogFieldFromHash\)/.test(siteScript), "BUG-R1: catalog deep links must respond to hash changes");
  assert(
    /if \(disclosure\) disclosure\.open = true;\s+target\.scrollIntoView/.test(siteScript),
    "BUG-R1: catalog deep links must scroll synchronously after revealing a field",
  );
}

const backendPath = path.join(siteRoot, "backend/index.html");
if (fs.existsSync(backendPath)) {
  const backend = fs.readFileSync(backendPath, "utf8");
  assert(/POST \/api\/v1\/device-signal-events/.test(backend), "Backend guide must define the ingestion endpoint");
  assert(/models\.JSONField/.test(backend), "Backend guide must include a Django JSONField model");
  assert(/class DeviceSignalEventSerializer/.test(backend), "Backend guide must include a DRF serializer");
  assert(/idempotency/i.test(backend), "Backend guide must document idempotency");
  assert(/unknown probe/i.test(backend), "Backend guide must document unknown probe handling");
}

const integrationPath = path.join(siteRoot, "integration/index.html");
if (fs.existsSync(integrationPath)) {
  const integration = fs.readFileSync(integrationPath, "utf8");
  assert(/id="permissions"/.test(integration), "Integration guide must include a permission matrix");
  for (const permission of [
    "ACCESS_NETWORK_STATE",
    "READ_PHONE_STATE",
    "BLUETOOTH_CONNECT",
    "DETECT_SCREEN_CAPTURE",
    "DETECT_SCREEN_RECORDING",
  ]) {
    assert(integration.includes(permission), `Permission matrix must document ${permission}`);
  }
  assert(/never requests location permission/i.test(integration), "Permission matrix must document location prompt behavior");
}

const riskGuidePath = path.join(siteRoot, "risk-teams/index.html");
if (fs.existsSync(riskGuidePath)) {
  const riskGuide = fs.readFileSync(riskGuidePath, "utf8");
  assert(/does not calculate a risk score/i.test(riskGuide), "Risk guide must state that the SDK does not calculate a score");
  assert(/never (?:block|decline)[^<]{0,80}(?:single|one) signal/i.test(riskGuide), "Risk guide must prohibit single-signal decisions");
}

const homePath = path.join(siteRoot, "index.html");
if (fs.existsSync(homePath)) {
  const home = fs.readFileSync(homePath, "utf8");
  assert(home.includes("/discussions/categories/q-a"), "Homepage must link to integration Q&A");
  assert(
    home.includes("issues/new?template=03-device-compatibility.yml"),
    "Homepage must link to the physical-device compatibility form",
  );
}

const workflowPath = path.join(root, ".github/workflows/pages.yml");
assert(fs.existsSync(workflowPath), "Missing .github/workflows/pages.yml");
if (fs.existsSync(workflowPath)) {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  assert(workflow.includes("actions/upload-pages-artifact@"), "Pages workflow must upload a Pages artifact");
  assert(workflow.includes("actions/deploy-pages@"), "Pages workflow must deploy through GitHub Pages");
  assert(workflow.includes("path: website"), "Pages workflow must publish only website/");
}

if (failures.length > 0) {
  console.error(`GitHub Pages verification failed (${failures.length}):`);
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`GitHub Pages verification passed for ${expectedPages.length} pages.`);
