import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const siteRoot = path.join(root, "website");
const expectedPages = [
  "index.html",
  "signals/index.html",
  "integration/index.html",
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

for (const asset of ["assets/styles.css", "assets/site.js", "robots.txt", "sitemap.xml", ".nojekyll"]) {
  assert(fs.existsSync(path.join(siteRoot, asset)), `Missing website/${asset}`);
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
