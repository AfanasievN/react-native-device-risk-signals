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
  "ai-prompts/index.html",
  "guides/index.html",
  "guides/react-native-root-jailbreak-detection/index.html",
  "guides/react-native-emulator-frida-detection/index.html",
  "guides/device-signals-vs-platform-attestation/index.html",
  "compare/react-native-device-risk-libraries/index.html",
  "use-cases/index.html",
  "use-cases/authentication/index.html",
  "use-cases/payments/index.html",
  "use-cases/account-recovery/index.html",
  "use-cases/promotions/index.html",
  "use-cases/protected-actions/index.html",
  "compatibility/index.html",
  "versions/index.html",
  "privacy/index.html",
  "risk-teams/index.html",
  "faq/index.html",
  "support/index.html",
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
  assert(/<meta property="og:image" content="https:/.test(html), `${relativePath}: missing Open Graph image`);
  assert(/<meta property="og:image:alt" content="[^"]+"/.test(html), `${relativePath}: missing Open Graph image alt`);
  assert(/<meta property="og:image:width" content="1200"/.test(html), `${relativePath}: missing Open Graph image width`);
  assert(/<meta property="og:image:height" content="630"/.test(html), `${relativePath}: missing Open Graph image height`);
  assert(/<meta name="twitter:card" content="summary_large_image"/.test(html), `${relativePath}: missing Twitter card`);
  assert(/<meta name="twitter:title" content="[^"]+"/.test(html), `${relativePath}: missing Twitter title`);
  assert(/<meta name="twitter:description" content="[^"]+"/.test(html), `${relativePath}: missing Twitter description`);
  assert(/<script type="application\/ld\+json">[\s\S]+?<\/script>/.test(html), `${relativePath}: missing JSON-LD`);
  assert(
    /href="\/react-native-device-risk-signals\/support\/"/.test(html),
    `${relativePath}: footer must link to the support page`,
  );
  for (const match of html.matchAll(/<script type="application\/ld\+json">([\s\S]+?)<\/script>/g)) {
    try {
      JSON.parse(match[1]);
    } catch {
      assert(false, `${relativePath}: contains invalid JSON-LD`);
    }
  }
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
  "assets/social-preview.png",
  "assets/donate-ton-qr.png",
  "probe-catalog.json",
  "raw-signal-event.schema.json",
  "llms.txt",
  "llms-full.txt",
  "examples/android-event.json",
  "examples/ios-event.json",
  "examples/outcome-states.json",
  "robots.txt",
  "sitemap.xml",
  ".nojekyll",
]) {
  assert(fs.existsSync(path.join(siteRoot, asset)), `Missing website/${asset}`);
}

const sitemapPath = path.join(siteRoot, "sitemap.xml");
if (fs.existsSync(sitemapPath)) {
  const sitemap = fs.readFileSync(sitemapPath, "utf8");
  const indexablePages = expectedPages.filter((relativePath) => relativePath !== "404.html");
  const sitemapUrls = [...sitemap.matchAll(/<url><loc>([^<]+)<\/loc><lastmod>(\d{4}-\d{2}-\d{2})<\/lastmod><\/url>/g)];
  assert(sitemapUrls.length === indexablePages.length, "Sitemap must contain every indexable page with lastmod");
  for (const relativePath of indexablePages) {
    const route = relativePath === "index.html" ? "" : relativePath.replace(/index\.html$/, "");
    const canonical = `https://afanasievn.github.io/react-native-device-risk-signals/${route}`;
    assert(sitemapUrls.some(([_, url]) => url === canonical), `Sitemap is missing ${canonical}`);
  }
}

const attributesPath = path.join(root, ".gitattributes");
assert(fs.existsSync(attributesPath), "Missing .gitattributes language classification");
if (fs.existsSync(attributesPath)) {
  const attributes = fs.readFileSync(attributesPath, "utf8");
  assert(/^website\/\*\* linguist-documentation$/m.test(attributes), "website/** must be classified as documentation");
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
  assert(/function activateBackendTab/.test(siteScript), "Backend framework tabs must have an activation controller");
  assert(/ArrowLeft/.test(siteScript) && /ArrowRight/.test(siteScript), "Backend framework tabs must support arrow keys");
}

const backendPath = path.join(siteRoot, "backend/index.html");
if (fs.existsSync(backendPath)) {
  const backend = fs.readFileSync(backendPath, "utf8");
  assert(/POST \/api\/v1\/device-signal-events/.test(backend), "Backend guide must define the ingestion endpoint");
  assert(/role="tablist"[^>]*aria-label="Backend framework"/.test(backend), "Backend guide must expose accessible framework tabs");
  for (const backendId of ["django", "fastapi", "express", "go"]) {
    assert(backend.includes(`data-backend-tab="${backendId}"`), `Backend guide must include the ${backendId} tab`);
    assert(backend.includes(`data-backend-panel="${backendId}"`), `Backend guide must include the ${backendId} panel`);
  }
  assert(/action_type = serializers\.CharField/.test(backend), "Backend guide must accept the use-case action_type");
  assert(/"action_type": serializer\.validated_data/.test(backend), "Backend guide must persist the use-case action_type");
  assert(/class DeviceSignalEventIn\(BaseModel\)/.test(backend), "Backend guide must include FastAPI validation");
  assert(/app\.post\("\/api\/v1\/device-signal-events"/.test(backend), "Backend guide must include an Express endpoint");
  assert(/http\.HandleFunc\("\/api\/v1\/device-signal-events"/.test(backend), "Backend guide must include a Go endpoint");
  assert(/models\.JSONField/.test(backend), "Backend guide must include a Django JSONField model");
  assert(/class DeviceSignalEventSerializer/.test(backend), "Backend guide must include a DRF serializer");
  assert(/idempotency/i.test(backend), "Backend guide must document idempotency");
  assert(/unknown probe/i.test(backend), "Backend guide must document unknown probe handling");
}

const useCaseRequirements = new Map([
  ["use-cases/authentication/index.html", ["login_attempt", "consentFor", "clientId"]],
  ["use-cases/payments/index.html", ["payment_attempt", "transaction_safety", "action_id"]],
  ["use-cases/account-recovery/index.html", ["recovery_attempt", "recovery path", "action_id"]],
  ["use-cases/promotions/index.html", ["promotion_redemption", "geolocation", "action_id"]],
  ["use-cases/protected-actions/index.html", ["protected_action", "transaction_safety", "action_id"]],
]);

for (const [relativePath, requiredTerms] of useCaseRequirements) {
  const useCasePath = path.join(siteRoot, relativePath);
  if (!fs.existsSync(useCasePath)) continue;
  const useCase = fs.readFileSync(useCasePath, "utf8");
  assert(/data-use-case=/.test(useCase), `${relativePath}: missing machine-readable use-case id`);
  assert(/does not calculate a risk score/i.test(useCase), `${relativePath}: missing raw-signal boundary`);
  for (const term of requiredTerms) {
    assert(useCase.includes(term), `${relativePath}: missing required guidance for ${term}`);
  }
}

const useCasesHubPath = path.join(siteRoot, "use-cases/index.html");
if (fs.existsSync(useCasesHubPath)) {
  const useCasesHub = fs.readFileSync(useCasesHubPath, "utf8");
  for (const slug of ["authentication", "payments", "account-recovery", "promotions", "protected-actions"]) {
    assert(useCasesHub.includes(`./${slug}/`), `Use-case hub must link to ${slug}`);
  }
}

const aiPromptsPath = path.join(siteRoot, "ai-prompts/index.html");
if (fs.existsSync(aiPromptsPath)) {
  const aiPrompts = fs.readFileSync(aiPromptsPath, "utf8");
  for (const promptId of [
    "explain-sdk-prompt",
    "mobile-integration-prompt",
    "backend-architecture-prompt",
    "django-backend-prompt",
    "fastapi-backend-prompt",
    "express-backend-prompt",
    "go-backend-prompt",
    "privacy-review-prompt",
  ]) {
    assert(aiPrompts.includes(`id="${promptId}"`), `AI prompt library is missing ${promptId}`);
  }
  for (const requiredTerm of [
    "{{BACKEND_STACK}}",
    "{{DATABASE}}",
    "{{AUTHENTICATION_MODEL}}",
    "raw-signal-event.schema.json",
    "probe-catalog.json",
    "Do not invent probe IDs or fields",
    "does not calculate a risk score",
  ]) {
    assert(aiPrompts.includes(requiredTerm), `AI prompt library is missing required context: ${requiredTerm}`);
  }
}

const llmsPath = path.join(siteRoot, "llms.txt");
if (fs.existsSync(llmsPath)) {
  const llms = fs.readFileSync(llmsPath, "utf8");
  assert(llms.startsWith("# React Native Device Risk Signals"), "llms.txt must start with the product heading");
  for (const pathName of ["signals/", "backend/", "use-cases/", "ai-prompts/", "raw-signal-event.schema.json", "probe-catalog.json"]) {
    assert(llms.includes(pathName), `llms.txt must link to ${pathName}`);
  }
  assert(/does not calculate a risk score/i.test(llms), "llms.txt must preserve the SDK decision boundary");
}

const llmsFullPath = path.join(siteRoot, "llms-full.txt");
if (fs.existsSync(llmsFullPath)) {
  const llmsFull = fs.readFileSync(llmsFullPath, "utf8");
  for (const requiredTerm of [
    "RawSignalEvent",
    "zero runtime dependencies",
    "Probe outcome semantics",
    "Backend ingestion contract",
    "does not calculate a risk score",
    "probe-catalog.json",
  ]) {
    assert(llmsFull.includes(requiredTerm), `llms-full.txt is missing required context: ${requiredTerm}`);
  }
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
  assert(
    /<title>React Native Device Risk Signals SDK \| Root, Jailbreak, Emulator &amp; Frida Detection<\/title>/.test(home),
    "Homepage title must target the primary SDK search intent",
  );
  assert(/<h1>Raw device risk signals for React Native<\/h1>/.test(home), "Homepage H1 must describe the SDK");
  assert(home.includes("./use-cases/"), "Homepage must link to the use-case hub");
  assert(home.includes("./guides/"), "Homepage must link to the technical guides");
  assert(home.includes("./ai-prompts/"), "Homepage must link to the AI prompt library");
  assert(home.includes("/discussions/categories/q-a"), "Homepage must link to integration Q&A");
  assert(
    home.includes("issues/new?template=03-device-compatibility.yml"),
    "Homepage must link to the physical-device compatibility form",
  );
}

const supportPath = path.join(siteRoot, "support/index.html");
if (fs.existsSync(supportPath)) {
  const support = fs.readFileSync(supportPath, "utf8");
  for (const requiredTerm of [
    "https://github.com/sponsors/AfanasievN",
    "https://app.tonkeeper.com/transfer/UQAMfkOwBBk_TZyn7LP2o9UgMrNW3GCLs3IJKOVxYBdzr0IK",
    "UQAMfkOwBBk_TZyn7LP2o9UgMrNW3GCLs3IJKOVxYBdzr0IK",
    "../assets/donate-ton-qr.png",
  ]) {
    assert(support.includes(requiredTerm), `Support page is missing ${requiredTerm}`);
  }
}

const fundingPath = path.join(root, ".github/FUNDING.yml");
assert(fs.existsSync(fundingPath), "Missing GitHub funding configuration");
if (fs.existsSync(fundingPath)) {
  const funding = fs.readFileSync(fundingPath, "utf8");
  assert(/^github:\s*\[AfanasievN\]$/m.test(funding), "GitHub Sponsors must be enabled for AfanasievN");
}

const readmePath = path.join(root, "README.md");
if (fs.existsSync(readmePath)) {
  const readme = fs.readFileSync(readmePath, "utf8");
  assert(
    readme.includes("https://github.com/sponsors/AfanasievN"),
    "README support section must link directly to GitHub Sponsors",
  );
}

const discoveryGuidePath = path.join(root, "docs/SEARCH_DISCOVERY.md");
assert(fs.existsSync(discoveryGuidePath), "Missing search discovery operations guide");
if (fs.existsSync(discoveryGuidePath)) {
  const discoveryGuide = fs.readFileSync(discoveryGuidePath, "utf8");
  for (const requiredTerm of ["Google Search Console", "Bing Webmaster Tools", "npm run docs:seo"]) {
    assert(discoveryGuide.includes(requiredTerm), `Search discovery guide is missing ${requiredTerm}`);
  }
}

const workflowPath = path.join(root, ".github/workflows/pages.yml");
assert(fs.existsSync(workflowPath), "Missing .github/workflows/pages.yml");
if (fs.existsSync(workflowPath)) {
  const workflow = fs.readFileSync(workflowPath, "utf8");
  assert(workflow.includes("actions/upload-pages-artifact@"), "Pages workflow must upload a Pages artifact");
  assert(workflow.includes("actions/deploy-pages@"), "Pages workflow must deploy through GitHub Pages");
  assert(workflow.includes("path: website"), "Pages workflow must publish only website/");
  assert(
    workflow.includes("npm ci --ignore-scripts"),
    "Pages workflow must install verification dependencies without lifecycle scripts"
  );
}

if (failures.length > 0) {
  console.error(`GitHub Pages verification failed (${failures.length}):`);
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log(`GitHub Pages verification passed for ${expectedPages.length} pages.`);
