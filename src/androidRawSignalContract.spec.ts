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
    expect(manifest).not.toMatch(
      /^\s*<uses-permission\b[^>]*android:name\s*=\s*["']android\.permission\.(?:QUERY_ALL_PACKAGES|DETECT_SCREEN_CAPTURE|DETECT_SCREEN_RECORDING)["'][^>]*>/m,
    );
  });

  it("does not execute PATH-resolved commands while collecting root evidence", () => {
    const source = readFileSync(
      join(androidRoot, "java/com/reactnativedeviceintel/OsIntegrityProvider.kt"),
      "utf8",
    );
    expect(source).not.toContain("Runtime.getRuntime().exec");
    expect(source).toContain("PathExecutableProbe.existsOnPath");
  });
});
