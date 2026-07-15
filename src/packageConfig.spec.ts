import fs from "node:fs";
import path from "node:path";

type PackageJson = {
  version?: string;
  main?: string;
  types?: string;
  "react-native"?: string;
  homepage?: string;
  repository?: {url?: string};
  bugs?: {url?: string};
  files?: string[];
  scripts?: Record<string, string>;
  publishConfig?: {access?: string};
  exports?: Record<string, unknown>;
};

const packageJsonPath = path.resolve(__dirname, "../package.json");
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8")) as PackageJson;

describe("npm package contract", () => {
  it("publishes the first public release with compiled entrypoints", () => {
    expect(packageJson.version).toMatch(/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/);
    expect(packageJson.main).toBe("lib/index.js");
    expect(packageJson.types).toBe("lib/index.d.ts");
    expect(packageJson["react-native"]).toBe("src/index.ts");
    expect(packageJson.files).toEqual(expect.arrayContaining(["lib", "src"]));
    expect(Object.prototype.hasOwnProperty.call(packageJson.exports ?? {}, ".")).toBe(true);
  });

  it("points package metadata at the public GitHub repository", () => {
    expect(packageJson.homepage).toBe(
      "https://github.com/AfanasievN/react-native-device-risk-signals#readme",
    );
    expect(packageJson.repository?.url).toBe(
      "git+https://github.com/AfanasievN/react-native-device-risk-signals.git",
    );
    expect(packageJson.bugs?.url).toBe(
      "https://github.com/AfanasievN/react-native-device-risk-signals/issues",
    );
    expect(JSON.stringify(packageJson)).not.toContain("OWNER");
  });

  it("defines repeatable build and public publishing safeguards", () => {
    expect(packageJson.scripts?.build).toBe("tsc -p tsconfig.build.json");
    expect(packageJson.scripts?.prepublishOnly).toBe("npm run verify");
    expect(packageJson.publishConfig?.access).toBe("public");
  });
});
