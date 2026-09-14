// iOS boolean boxing verifier.
//
// Run: node scripts/verify-ios-boolean-boxing.mjs
//
// WHY THIS GATE EXISTS
//
// A contract field declared `boolean` in contract/raw-signal-event.schema.json must cross the
// React Native bridge as a CFBoolean, otherwise JavaScript receives `1`/`0` where the published
// schema promises `true`/`false`.
//
// In Objective-C, the boxed-expression literal `@(expr)` picks the NSNumber constructor from the
// STATIC C TYPE of `expr`. C's comparison and logical operators (`==`, `!=`, `>`, `<`, `>=`, `<=`,
// `!`, `&&`, `||`) are defined to yield `int`, not `_Bool`. So `@(count > 0)` has objCType "i" and
// bridges as a number, while `@(someBoolProperty)` has objCType "c"/"B" and bridges as a boolean.
//
// Two traps that measurement proved, and that this checker therefore also catches:
//   - `@(cond ? YES : NO)` is STILL an int. The second and third operands of `?:` are promoted, so
//     the conditional expression's type is `int`, not `BOOL`.
//   - `@(!boolExpr)` is an int, because C's `!` yields `int` even when its operand is a `BOOL`.
//
// Correct spellings, any of which this checker accepts:
//   @((BOOL)(count > 0))                    explicit cast inside the literal
//   BOOL found = count > 0; @(found)        BOOL local hoisted before the literal
//   [NSNumber numberWithBool:count > 0]     not a boxing literal at all
//   @(object.someBoolProperty)              BOOL property or BOOL-returning method
//   @YES / @NO                              boolean literals
//
// WHY .mm FILES ARE EXEMPT
//
// `.mm` is Objective-C++. In C++ the comparison and logical operators are defined to yield `bool`,
// not `int`, so `@(count > 0)` in a `.mm` file already boxes as a CFBoolean and is correct as
// written. Flagging those would be a pure false positive, so `.mm` files are counted and reported
// as exempt rather than scanned. If collection logic ever moves from a `.mm` adapter into a `.m`
// provider, this gate picks it up at that moment.
//
// DETECTION RULE
//
// For every `@( ... )` boxing literal in an Objective-C `.m` file:
//   1. Comments and string/char literals are blanked out first, so operator characters inside them
//      cannot create a finding, and so a `@(` inside a string is never treated as a literal.
//   2. A leading explicit `(BOOL)` / `(bool)` / `(_Bool)` cast is stripped. Stripping rather than
//      whole-literal exemption is deliberate: `@((BOOL)a == b)` is still an int, because the cast
//      binds only to `a`, and after stripping the `==` is still top level, so it is still flagged.
//   3. The remainder is flagged when it contains a comparison or logical operator at TOP LEVEL —
//      outside every `(...)`, `[...]` and `{...}`. Operators nested inside parentheses do not
//      determine the type of the whole expression, and operators inside `[...]` are arguments to a
//      message send whose own return type governs the boxing.
//   4. A top-level `?:` whose two branches are both boolean literals (`YES`/`NO`/`true`/`false`)
//      is flagged as the `@(cond ? YES : NO)` trap.
//   5. `->`, `<<` and `>>` are explicitly NOT comparisons and are never flagged. Bare identifiers,
//      property accesses, message sends and arithmetic are never flagged: they are either already
//      correct or not boolean at all.
//
// ALLOW-LIST
//
// The rule above is a heuristic and can be wrong in both directions. A comment marker on the line
// immediately above the line where the `@(` starts suppresses exactly one finding:
//
//   // drs-allow-int-boxing: the schema declares this field as an integer count, not a boolean
//   result[@"someIntField"] = @(a > b);
//
// The reason is mandatory — a bare `// drs-allow-int-boxing` is itself an error — and a marker that
// suppresses nothing is an error too, so stale suppressions cannot survive a fix. The summary line
// always reports how many suppressions are active, so they cannot accumulate silently.

import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const SCAN_ROOTS = [path.join("ios"), path.join("sdks", "ios", "Sources")];
const SKIP_DIRECTORIES = new Set([".build", "build", "Pods", "DerivedData", "node_modules", ".git"]);
const ALLOW_MARKER = "drs-allow-int-boxing";

function fail(message) {
  console.error(`iOS boolean boxing verification failed:\n- ${message}`);
  process.exit(1);
}

// ── Source lexing ────────────────────────────────────────────────────────────────────────────

/**
 * Returns a same-length copy of `source` in which every comment and every string/char literal —
 * delimiters included, and including the `@` of an Objective-C `@"..."` literal — is replaced by
 * spaces. Newlines are preserved so byte offsets and line numbers stay in sync with the original.
 *
 * Blanking is what makes `@("a > b")` and `// count > 0` invisible to the operator scan, and what
 * keeps a `@(` that appears inside a string from being mistaken for a boxing literal.
 */
function maskNonCode(source) {
  const out = Array.from(source);
  const blank = (from, to) => {
    for (let i = from; i < to && i < out.length; i += 1) {
      if (out[i] !== "\n") out[i] = " ";
    }
  };

  let index = 0;
  while (index < source.length) {
    const character = source[index];
    const next = source[index + 1];

    if (character === "/" && next === "/") {
      let end = source.indexOf("\n", index);
      if (end === -1) end = source.length;
      blank(index, end);
      index = end;
      continue;
    }

    if (character === "/" && next === "*") {
      const closing = source.indexOf("*/", index + 2);
      const end = closing === -1 ? source.length : closing + 2;
      blank(index, end);
      index = end;
      continue;
    }

    if (character === '"' || character === "'" || (character === "@" && next === '"')) {
      const quote = character === "@" ? '"' : character;
      let cursor = character === "@" ? index + 2 : index + 1;
      while (cursor < source.length) {
        if (source[cursor] === "\\") {
          cursor += 2;
          continue;
        }
        if (source[cursor] === quote) {
          cursor += 1;
          break;
        }
        // An unterminated literal would not compile; stop at the newline rather than eating the file.
        if (source[cursor] === "\n" && quote === "'") break;
        cursor += 1;
      }
      blank(index, cursor);
      index = cursor;
      continue;
    }

    index += 1;
  }

  return out.join("");
}

// ── Boxing literal extraction ────────────────────────────────────────────────────────────────

/**
 * Finds every `@( ... )` boxing literal in the masked source, matching parentheses so that nested
 * parentheses and literals spanning several lines are handled as one unit.
 */
function findBoxingLiterals(masked) {
  const literals = [];
  const pattern = /@\s*\(/g;
  let match;

  while ((match = pattern.exec(masked)) !== null) {
    const openIndex = match.index + match[0].length - 1;
    let depth = 0;
    let cursor = openIndex;
    let closeIndex = -1;

    while (cursor < masked.length) {
      const character = masked[cursor];
      if (character === "(") depth += 1;
      else if (character === ")") {
        depth -= 1;
        if (depth === 0) {
          closeIndex = cursor;
          break;
        }
      }
      cursor += 1;
    }

    if (closeIndex === -1) continue; // Unbalanced source would not compile; nothing to assert here.
    literals.push({ start: match.index, innerStart: openIndex + 1, innerEnd: closeIndex });
    pattern.lastIndex = openIndex + 1; // Allow nested `@(` literals to be found on their own.
  }

  return literals;
}

const LEADING_BOOL_CAST = /^\(\s*(?:BOOL|bool|_Bool)\s*\)/;

/**
 * Strips one leading explicit boolean cast. Returns the offset the remainder starts at, so the
 * masked text and the original text stay aligned.
 */
function stripLeadingBoolCast(maskedInner) {
  const leadingWhitespace = maskedInner.length - maskedInner.trimStart().length;
  const rest = maskedInner.slice(leadingWhitespace);
  const cast = LEADING_BOOL_CAST.exec(rest);
  if (!cast) return { offset: 0, hadCast: false };
  return { offset: leadingWhitespace + cast[0].length, hadCast: true };
}

/**
 * Walks `maskedExpression` and reports the comparison/logical operators that sit at top level —
 * outside every `()`, `[]` and `{}` — plus the offsets of a top-level `?` and its matching `:`.
 */
function scanTopLevel(maskedExpression) {
  const operators = [];
  let parenDepth = 0;
  let bracketDepth = 0;
  let braceDepth = 0;
  let ternaryQuestion = -1;
  let ternaryColon = -1;
  let pendingQuestions = 0;

  const atTopLevel = () => parenDepth === 0 && bracketDepth === 0 && braceDepth === 0;

  for (let index = 0; index < maskedExpression.length; index += 1) {
    const character = maskedExpression[index];
    const next = maskedExpression[index + 1];
    const previous = index > 0 ? maskedExpression[index - 1] : "";

    if (character === "(") parenDepth += 1;
    else if (character === ")") parenDepth -= 1;
    else if (character === "[") bracketDepth += 1;
    else if (character === "]") bracketDepth -= 1;
    else if (character === "{") braceDepth += 1;
    else if (character === "}") braceDepth -= 1;

    if (!atTopLevel()) continue;

    if (character === "?") {
      if (ternaryQuestion === -1) ternaryQuestion = index;
      else pendingQuestions += 1;
      continue;
    }
    if (character === ":" && ternaryQuestion !== -1 && ternaryColon === -1) {
      if (pendingQuestions > 0) pendingQuestions -= 1;
      else ternaryColon = index;
      continue;
    }

    if (character === "=" && next === "=") {
      operators.push({ text: "==", index });
      index += 1;
      continue;
    }
    if (character === "!" && next === "=") {
      operators.push({ text: "!=", index });
      index += 1;
      continue;
    }
    if (character === "&" && next === "&") {
      operators.push({ text: "&&", index });
      index += 1;
      continue;
    }
    if (character === "|" && next === "|") {
      operators.push({ text: "||", index });
      index += 1;
      continue;
    }
    if (character === "<" || character === ">") {
      // `<<` and `>>` are shifts (arithmetic, yields int by design) and `->` is member access.
      if (next === character) {
        index += 1;
        continue;
      }
      if (character === ">" && previous === "-") continue;
      if (next === "=") {
        operators.push({ text: `${character}=`, index });
        index += 1;
        continue;
      }
      operators.push({ text: character, index });
      continue;
    }
    if (character === "!" && next !== "=") {
      // Unary `!`. C defines it as yielding int, so `@(!flag)` is an int even for a BOOL operand.
      operators.push({ text: "!", index });
      continue;
    }
  }

  return { operators, ternaryQuestion, ternaryColon };
}

const BOOLEAN_LITERAL = /^(?:YES|NO|TRUE|FALSE|true|false)$/;

function collapse(text, limit = 140) {
  const single = text.replace(/\s+/g, " ").trim();
  return single.length > limit ? `${single.slice(0, limit - 1)}…` : single;
}

function lineNumberAt(source, index) {
  let line = 1;
  for (let cursor = 0; cursor < index; cursor += 1) {
    if (source[cursor] === "\n") line += 1;
  }
  return line;
}

// ── File discovery ───────────────────────────────────────────────────────────────────────────

function collectSources(directory, found) {
  if (!fs.existsSync(directory)) return;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (SKIP_DIRECTORIES.has(entry.name)) continue;
      collectSources(absolute, found);
      continue;
    }
    if (entry.isFile() && (entry.name.endsWith(".m") || entry.name.endsWith(".mm"))) {
      found.push(absolute);
    }
  }
}

const sources = [];
for (const scanRoot of SCAN_ROOTS) collectSources(path.join(root, scanRoot), sources);

if (sources.length === 0) {
  fail(
    `no Objective-C sources found under ${SCAN_ROOTS.join(" or ")} — the scan roots are stale and ` +
      "this gate is checking nothing",
  );
}

const objectiveCppFiles = sources.filter((file) => file.endsWith(".mm"));
const objectiveCFiles = sources.filter((file) => file.endsWith(".m"));

// ── Scan ─────────────────────────────────────────────────────────────────────────────────────

const failures = [];
let literalsChecked = 0;
let suppressionsActive = 0;

for (const file of objectiveCFiles) {
  const relative = path.relative(root, file);
  const source = fs.readFileSync(file, "utf8");
  const masked = maskNonCode(source);
  const lines = source.split("\n");

  // Markers are recorded by line so an unused one can be reported as stale.
  const markerLines = new Map();
  lines.forEach((text, zeroBased) => {
    const markerIndex = text.indexOf(ALLOW_MARKER);
    if (markerIndex === -1) return;
    const before = text.slice(0, markerIndex);
    if (!before.includes("//") && !before.includes("/*")) return;
    const reason = text
      .slice(markerIndex + ALLOW_MARKER.length)
      .replace(/\*\/\s*$/, "")
      .replace(/^\s*:/, "")
      .trim();
    markerLines.set(zeroBased + 1, { reason, raw: text.trim(), used: false });
  });

  for (const literal of findBoxingLiterals(masked)) {
    literalsChecked += 1;
    const maskedInner = masked.slice(literal.innerStart, literal.innerEnd);
    const originalInner = source.slice(literal.innerStart, literal.innerEnd);
    const { offset, hadCast } = stripLeadingBoolCast(maskedInner);
    const maskedExpression = maskedInner.slice(offset);
    const { operators, ternaryQuestion, ternaryColon } = scanTopLevel(maskedExpression);

    const reasons = [];
    if (operators.length > 0) {
      const unique = [...new Set(operators.map((operator) => operator.text))];
      reasons.push(
        `top-level ${unique.map((text) => `\`${text}\``).join(", ")} ` +
          `${unique.length === 1 ? "operator yields" : "operators yield"} \`int\` in Objective-C`,
      );
    }
    if (ternaryQuestion !== -1 && ternaryColon !== -1) {
      const thenBranch = maskedExpression.slice(ternaryQuestion + 1, ternaryColon).trim();
      const elseBranch = maskedExpression.slice(ternaryColon + 1).trim();
      if (BOOLEAN_LITERAL.test(thenBranch) && BOOLEAN_LITERAL.test(elseBranch)) {
        reasons.push("`cond ? YES : NO` is promoted to `int`, it is not a `BOOL`");
      }
    }

    if (reasons.length === 0) continue;

    const line = lineNumberAt(source, literal.start);
    const marker = markerLines.get(line - 1);
    if (marker && marker.reason !== "") {
      marker.used = true;
      suppressionsActive += 1;
      continue;
    }
    if (marker && marker.reason === "") {
      marker.used = true;
      failures.push(
        `${relative}:${line - 1}: \`${ALLOW_MARKER}\` marker has no reason.\n` +
          `    Write \`// ${ALLOW_MARKER}: <why this field is genuinely not a boolean>\`.\n` +
          `    A suppression without a stated reason is indistinguishable from an unfixed defect.`,
      );
      continue;
    }

    const expression = collapse(originalInner);
    const castNote = hadCast
      ? "\n    A leading `(BOOL)` cast is present but binds only to the first operand; " +
        "parenthesise the whole expression."
      : "";
    failures.push(
      `${relative}:${line}: boolean contract field boxed as \`int\`\n` +
        `    expression: @(${expression})\n` +
        `    reason:     ${reasons.join("; ")}${castNote}\n` +
        `    minimal fix: @((BOOL)(${expression}))\n` +
        "    alternatives: hoist a `BOOL` local, use `[NSNumber numberWithBool:…]`, or `@YES`/`@NO`.",
    );
  }

  for (const [line, marker] of markerLines) {
    if (marker.used) continue;
    failures.push(
      `${relative}:${line}: stale \`${ALLOW_MARKER}\` marker — the line below has no finding to ` +
        "suppress.\n    Remove the marker so suppressions cannot accumulate silently.",
    );
  }
}

// ── Report ───────────────────────────────────────────────────────────────────────────────────

if (failures.length > 0) {
  console.error("iOS boolean boxing verification failed:");
  for (const failure of failures) console.error(`- ${failure}`);
  console.error("");
  console.error(
    `Checked ${literalsChecked} @(…) boxing ${literalsChecked === 1 ? "literal" : "literals"} in ` +
      `${objectiveCFiles.length} Objective-C .m ${objectiveCFiles.length === 1 ? "file" : "files"}; ` +
      `${objectiveCppFiles.length} Objective-C++ .mm ${objectiveCppFiles.length === 1 ? "file is" : "files are"} ` +
      "exempt (C++ comparisons yield `bool`, so they box correctly); " +
      `${suppressionsActive} active ${suppressionsActive === 1 ? "suppression" : "suppressions"}.`,
  );
  process.exit(1);
}

console.log(
  `iOS boolean boxing is correct: checked ${literalsChecked} @(…) boxing ` +
    `${literalsChecked === 1 ? "literal" : "literals"} across ${objectiveCFiles.length} Objective-C .m ` +
    `${objectiveCFiles.length === 1 ? "file" : "files"}; ${objectiveCppFiles.length} Objective-C++ .mm ` +
    `${objectiveCppFiles.length === 1 ? "file is" : "files are"} exempt (C++ comparisons yield \`bool\`, ` +
    `so they box correctly); ${suppressionsActive} active ` +
    `${suppressionsActive === 1 ? "suppression" : "suppressions"}.`,
);
