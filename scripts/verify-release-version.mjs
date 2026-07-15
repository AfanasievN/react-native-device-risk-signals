import {readFileSync} from "node:fs";
import {resolve} from "node:path";

const releaseTag = process.argv[2];
const packageJson = JSON.parse(readFileSync(resolve("package.json"), "utf8"));
const expectedTag = `v${packageJson.version}`;

if (releaseTag !== expectedTag) {
  console.error(`Release tag ${releaseTag ?? "<missing>"} must match package version ${expectedTag}.`);
  process.exitCode = 1;
} else {
  console.log(`Release tag ${releaseTag} matches package version ${packageJson.version}.`);
}
