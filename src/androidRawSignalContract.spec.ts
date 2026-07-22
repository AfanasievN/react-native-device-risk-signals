import {readFileSync} from "node:fs";
import {join} from "node:path";

const androidRoot = join(__dirname, "../android/src/main");

describe("Android raw signal contract", () => {
  it("keeps install provenance catalog fields backed by the native provider", () => {
    const source = readFileSync(
      join(androidRoot, "java/com/reactnativedeviceintel/ApplicationInfoProvider.kt"),
      "utf8",
    );
    for (const field of [
      "installerPackage",
      "installingPackageName",
      "initiatingPackageName",
      "initiatingPackageSigningCertificateSha256",
      "installPackageSource",
      "updateOwnerPackageName",
      "isSystemApp",
      "isUpdatedSystemApp",
    ]) {
      expect(source).toContain(`\"${field}\"`);
    }
  });

  it("keeps transaction observation fields backed by Android native code", () => {
    const source = readFileSync(
      join(androidRoot, "java/com/reactnativedeviceintel/SecurityPostureProvider.kt"),
      "utf8",
    );
    for (const field of [
      "isVisibleInScreenRecording",
      "screenshotObservationActive",
      "screenshotDetectedSinceObservationStart",
      "lastScreenshotDetectedElapsedMs",
      "transactionObservationStartedElapsedMs",
      "observedTouchCount",
      "obscuredTouchObserved",
      "partiallyObscuredTouchObserved",
      "lastObscuredTouchElapsedMs",
      "lastPartiallyObscuredTouchElapsedMs",
    ]) {
      expect(source).toContain(`\"${field}\"`);
    }
  });

  it("does not merge capture permissions or broad package visibility into host apps", () => {
    const manifest = readFileSync(join(androidRoot, "AndroidManifest.xml"), "utf8");
    const declarations = manifest.replace(/<!--[\s\S]*?-->/g, "");
    expect(declarations).not.toMatch(/<uses-permission\b/);
    expect(declarations).not.toContain("QUERY_ALL_PACKAGES");
    expect(declarations).not.toContain("DETECT_SCREEN_CAPTURE");
    expect(declarations).not.toContain("DETECT_SCREEN_RECORDING");
  });
});
