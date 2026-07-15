import {rmSync} from "node:fs";
import {resolve} from "node:path";

rmSync(resolve("lib"), {force: true, recursive: true});
