const HTML_ENTITIES = {
  "&amp;": "&",
  "&quot;": '"',
  "&#39;": "'",
  "&lt;": "<",
  "&gt;": ">",
};

const metadataEndMarker = "<!-- SEO_DISCOVERY_METADATA_END -->";
const iconLinkStart = '<link rel="icon"';

export function decodeHtmlOnce(value) {
  return value.replace(
    /&(?:amp|quot|#39|lt|gt);/g,
    (entity) => HTML_ENTITIES[entity],
  );
}

export function serializeJsonForHtmlScript(value) {
  return JSON.stringify(value, null, 2).replace(
    /[<>&]/g,
    (character) => `\\u${character.charCodeAt(0).toString(16).padStart(4, "0")}`,
  );
}

export function collapseBlankLinesAfterMarker(html) {
  const markerIndex = html.indexOf(metadataEndMarker);
  if (markerIndex < 0) return html;

  const whitespaceStart = markerIndex + metadataEndMarker.length;
  let cursor = whitespaceStart;
  while (cursor < html.length) {
    const character = html[cursor];
    if (character !== " " && character !== "\t" && character !== "\r" && character !== "\n") break;
    cursor += 1;
  }

  if (!html.startsWith(iconLinkStart, cursor)) return html;
  let indentationStart = cursor;
  while (indentationStart > whitespaceStart) {
    const character = html[indentationStart - 1];
    if (character !== " " && character !== "\t") break;
    indentationStart -= 1;
  }
  const indentation = html.slice(indentationStart, cursor);
  return `${html.slice(0, whitespaceStart)}\n${indentation}${html.slice(cursor)}`;
}
