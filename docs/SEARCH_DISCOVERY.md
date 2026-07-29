# Search and AI discovery operations

The public documentation is static HTML hosted on GitHub Pages. Search crawlers and AI agents can
read the primary content without running JavaScript. Every indexable page must have a unique title,
description, canonical URL, Open Graph and Twitter metadata, JSON-LD, and a sitemap entry.

## Maintained discovery surfaces

- `website/robots.txt` allows crawling and points to the sitemap.
- `website/sitemap.xml` lists every indexable HTML route with an accurate `lastmod`.
- `website/llms.txt` is a concise map for AI agents.
- `website/llms-full.txt` is a consolidated source-grounded context document.
- `website/probe-catalog.json` and `website/raw-signal-event.schema.json` are authoritative
  machine-readable contracts.
- `website/assets/social-preview.png` is the shared 1200 by 630 social preview.
- JSON-LD identifies the project, software package, documentation pages, articles, and breadcrumbs.

Run this after adding or renaming an HTML page:

```sh
npm run docs:seo
npm run verify:pages
```

The sync command derives metadata from each page head and rebuilds the sitemap. Commit both the
source page and generated discovery changes. Do not hand-edit content between the
`SEO_DISCOVERY_METADATA` markers.

## Google Search Console

1. Add the URL-prefix property
   `https://afanasievn.github.io/react-native-device-risk-signals/`.
2. Choose HTML tag or HTML file verification.
3. Add the exact token supplied by Google. Never commit a placeholder or a token copied from another
   property.
4. Submit
   `https://afanasievn.github.io/react-native-device-risk-signals/sitemap.xml`.
5. Inspect and request indexing for the home page, signal catalog, integration, backend, guides, use
   cases, and AI prompt library.
6. Review Page indexing, Crawl stats, Core Web Vitals, and search query reports after Google has had
   time to recrawl.

If HTML-file verification is used, place the exact Google-provided file under `website/`. If a meta
tag is used, add it only to `website/index.html`, outside generated discovery markers.

## Bing Webmaster Tools

1. Add the same GitHub Pages URL-prefix site.
2. Import the verified property from Google Search Console or use Bing's supplied verification
   method.
3. Submit the same sitemap.
4. Inspect the important URLs and monitor crawl or indexing errors.

## Release checklist

- Run `npm run verify`.
- Confirm the deployed sitemap returns XML and all listed pages return HTTP 200.
- Confirm a missing route returns HTTP 404.
- Validate the home-page JSON-LD with Google's Rich Results Test or Schema.org validator.
- Check the social preview in a real link debugger after changing the preview asset.
- Search for the exact package name on npm and GitHub.
- Record Search Console indexed-page and query changes monthly rather than inferring them from a
  public `site:` query.

## Authority work outside the repository

Technical metadata cannot create authority by itself. Maintain a small, factual promotion program:

- keep the React Native Directory entry current;
- propose the package to relevant curated React Native lists;
- publish useful implementation or comparison guides that link to the canonical documentation;
- answer real integration questions in GitHub Discussions and community channels;
- encourage adopters to share sanitized physical-device compatibility reports;
- avoid copied, doorway, keyword-stuffed, or mass-generated pages.

No search position or AI ingestion is guaranteed. `robots.txt`, sitemap, structured data, and
`llms.txt` improve discovery and interpretation; external references, useful content, project
history, and user engagement build authority.
