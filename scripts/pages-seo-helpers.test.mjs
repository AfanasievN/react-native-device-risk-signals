import assert from "node:assert/strict";
import test from "node:test";

import {
  collapseBlankLinesAfterMarker,
  decodeHtmlOnce,
  serializeJsonForHtmlScript,
} from "./pages-seo-helpers.mjs";

test("HTML entities are decoded exactly once", () => {
  assert.equal(decodeHtmlOnce("&amp;lt;/script&amp;gt;"), "&lt;/script&gt;");
  assert.equal(decodeHtmlOnce("&quot;device&amp;risk&quot;"), '"device&risk"');
});

test("structured data cannot terminate its HTML script element", () => {
  const serialized = serializeJsonForHtmlScript({
    description: "</script><script>alert('xss')</script>",
  });

  assert.equal(serialized.includes("</script>"), false);
  assert.equal(serialized.includes("<script>"), false);
  assert.deepEqual(JSON.parse(serialized), {
    description: "</script><script>alert('xss')</script>",
  });
});

test("metadata spacing is collapsed without input-size-sensitive matching", () => {
  const marker = "<!-- SEO_DISCOVERY_METADATA_END -->";
  const input = `${marker}\n${"\n".repeat(20_000)}  <link rel="icon" href="icon.svg">`;

  assert.equal(
    collapseBlankLinesAfterMarker(input),
    `${marker}\n  <link rel="icon" href="icon.svg">`,
  );
});
