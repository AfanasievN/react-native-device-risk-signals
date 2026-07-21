import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

export function readProbeCatalog(root) {
  const sourcePath = path.join(root, "src/probeCatalog.ts");
  const source = fs.readFileSync(sourcePath, "utf8");
  const assignment = "export const PROBE_CATALOG = ";
  const terminator = "] as const satisfies readonly ProbeDescriptor[];";
  const start = source.indexOf(assignment);
  const end = source.indexOf(terminator, start + assignment.length);

  if (start === -1 || end === -1) {
    throw new Error("Could not locate PROBE_CATALOG in src/probeCatalog.ts");
  }

  const expression = source.slice(start + assignment.length, end + 1);
  return vm.runInNewContext(expression, Object.create(null), {
    filename: sourcePath,
    timeout: 1_000,
  });
}
