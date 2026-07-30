import {execFileSync} from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import {
  collapseBlankLinesAfterMarker,
  decodeHtmlOnce,
  serializeJsonForHtmlScript,
} from "./pages-seo-helpers.mjs";

const root = process.cwd();
const siteRoot = path.join(root, "website");
const siteBase = "https://afanasievn.github.io/react-native-device-risk-signals/";
const repository = "https://github.com/AfanasievN/react-native-device-risk-signals";
const socialImage = `${siteBase}assets/social-preview.png`;
const supportHref = "/react-native-device-risk-signals/support/";
const supportLink = `<a class="support-link" href="${supportHref}">Support project</a>`;
const markerStart = "<!-- SEO_DISCOVERY_METADATA_START -->";
const markerEnd = "<!-- SEO_DISCOVERY_METADATA_END -->";

function htmlFiles(directory) {
  return fs.readdirSync(directory, {withFileTypes: true}).flatMap((entry) => {
    const absolute = path.join(directory, entry.name);
    return entry.isDirectory() ? htmlFiles(absolute) : entry.name.endsWith(".html") ? [absolute] : [];
  });
}

function capture(html, pattern, label, relativePath) {
  const match = html.match(pattern);
  if (!match) throw new Error(`${relativePath}: missing ${label}`);
  return decodeHtmlOnce(match[1]);
}

function breadcrumbItems(canonical, title) {
  const relative = canonical.slice(siteBase.length).replace(/\/$/, "");
  const segments = relative ? relative.split("/") : [];
  const items = [{name: "Documentation", item: siteBase}];
  let current = siteBase;

  segments.forEach((segment, index) => {
    current += `${segment}/`;
    const name = index === segments.length - 1
      ? title.replace(/\s+\|\s+.*$/, "")
      : segment.replaceAll("-", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
    items.push({name, item: current});
  });

  return items.map((item, index) => ({
    "@type": "ListItem",
    position: index + 1,
    name: item.name,
    item: item.item,
  }));
}

function structuredData({canonical, description, relativePath, title, type}) {
  const graph = [
    {
      "@type": type === "article" ? "TechArticle" : "WebPage",
      "@id": `${canonical}#page`,
      url: canonical,
      name: title,
      description,
      inLanguage: "en",
      isPartOf: {"@id": `${siteBase}#website`},
      ...(type === "article" ? {
        author: {"@id": `${repository}#author`},
        publisher: {"@id": `${repository}#author`},
      } : {}),
    },
    {
      "@type": "BreadcrumbList",
      "@id": `${canonical}#breadcrumb`,
      itemListElement: breadcrumbItems(canonical, title),
    },
  ];

  if (type === "article") {
    graph.push({
      "@type": "Person",
      "@id": `${repository}#author`,
      name: "AfanasievN",
      url: "https://github.com/AfanasievN",
    });
  }

  if (relativePath === "index.html") {
    graph.unshift(
      {
        "@type": "WebSite",
        "@id": `${siteBase}#website`,
        url: siteBase,
        name: "React Native Device Risk Signals",
        description,
        inLanguage: "en",
      },
      {
        "@type": "SoftwareApplication",
        "@id": `${siteBase}#software`,
        name: "react-native-device-risk-signals",
        alternateName: "React Native Device Risk Signals",
        applicationCategory: "DeveloperApplication",
        operatingSystem: "Android, iOS",
        description,
        url: siteBase,
        downloadUrl: "https://www.npmjs.com/package/react-native-device-risk-signals",
        codeRepository: repository,
        programmingLanguage: ["TypeScript", "Kotlin", "Objective-C++"],
        license: `${repository}/blob/main/LICENSE`,
        offers: {
          "@type": "Offer",
          price: "0",
          priceCurrency: "USD",
        },
      },
    );
  }

  return serializeJsonForHtmlScript({"@context": "https://schema.org", "@graph": graph});
}

function metadataBlock({canonical, description, relativePath, title, type}) {
  const escapedTitle = title.replaceAll("&", "&amp;").replaceAll('"', "&quot;");
  const escapedDescription = description.replaceAll("&", "&amp;").replaceAll('"', "&quot;");
  return `${markerStart}
  <meta property="og:image" content="${socialImage}">
  <meta property="og:image:alt" content="React Native Device Risk Signals logo with a structured raw Android and iOS event preview">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="${escapedTitle}">
  <meta name="twitter:description" content="${escapedDescription}">
  <meta name="twitter:image" content="${socialImage}">
  <script type="application/ld+json">
${structuredData({canonical, description, relativePath, title, type})}
  </script>
  ${markerEnd}`;
}

function lastModified(filePath) {
  const relativePath = path.relative(root, filePath);
  try {
    const dirty = execFileSync("git", ["status", "--porcelain", "--", relativePath], {
      cwd: root,
      encoding: "utf8",
    }).trim();
    if (dirty) return new Date().toISOString().slice(0, 10);
    const committed = execFileSync("git", ["log", "-1", "--format=%cs", "--", relativePath], {
      cwd: root,
      encoding: "utf8",
    }).trim();
    if (committed) return committed;
  } catch {
    // A missing Git history must not block a local documentation build.
  }
  return fs.statSync(filePath).mtime.toISOString().slice(0, 10);
}

const pages = htmlFiles(siteRoot);
for (const pagePath of pages) {
  const relativePath = path.relative(siteRoot, pagePath);
  let html = fs.readFileSync(pagePath, "utf8");
  const title = capture(html, /<title>([^<]+)<\/title>/, "title", relativePath);
  const description = capture(html, /<meta name="description" content="([^"]+)"/, "description", relativePath);
  const canonical = capture(html, /<link rel="canonical" href="([^"]+)"/, "canonical", relativePath);
  const type = capture(html, /<meta property="og:type" content="([^"]+)"/, "Open Graph type", relativePath);
  const block = metadataBlock({canonical, description, relativePath, title, type});

  html = html.replace(/\n?  <!-- SEO_DISCOVERY_METADATA_START -->[\s\S]*?<!-- SEO_DISCOVERY_METADATA_END -->\n?/g, "\n");
  html = html
    .replace(/\n?  <meta property="og:image"[^>]*>\n?/g, "\n")
    .replace(/\n?  <meta name="twitter:card"[^>]*>\n?/g, "\n")
    .replace(/(\s*<link rel="icon")/, `\n  ${block}\n$1`);
  html = collapseBlankLinesAfterMarker(html);
  if (!html.includes(`href="${supportHref}"`)) {
    html = html.replace('<div class="footer-links">', `<div class="footer-links">${supportLink}`);
  }
  fs.writeFileSync(pagePath, html);
}

const indexablePages = pages
  .filter((pagePath) => path.basename(pagePath) === "index.html")
  .sort((left, right) => {
    const leftRelative = path.relative(siteRoot, left);
    const rightRelative = path.relative(siteRoot, right);
    if (leftRelative === "index.html") return -1;
    if (rightRelative === "index.html") return 1;
    return leftRelative.localeCompare(rightRelative);
  });

const sitemapEntries = indexablePages.map((pagePath) => {
  const relativePath = path.relative(siteRoot, pagePath);
  const route = relativePath === "index.html" ? "" : relativePath.replace(/index\.html$/, "");
  return `  <url><loc>${siteBase}${route}</loc><lastmod>${lastModified(pagePath)}</lastmod></url>`;
});
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${sitemapEntries.join("\n")}
</urlset>
`;
fs.writeFileSync(path.join(siteRoot, "sitemap.xml"), sitemap);
console.log(`Synchronized discovery metadata for ${pages.length} HTML pages.`);
