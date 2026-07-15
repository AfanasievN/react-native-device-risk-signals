const needsTransform = ["@react-native-community", "@react-native", "react-native.*"];

/** @type {import('jest').Config} */
export default {
  displayName: "device-risk-signals",
  moduleFileExtensions: ["ts", "tsx", "js", "jsx", "json", "node"],
  // The example is an independent React Native app with its own Jest preset and test command.
  testPathIgnorePatterns: ["<rootDir>/node_modules/", "<rootDir>/dist/", "<rootDir>/example/"],
  modulePathIgnorePatterns: ["<rootDir>/node_modules/", "<rootDir>/dist/"],
  transformIgnorePatterns: [`node_modules/(?!(${needsTransform.join("|")})/)`],
  // Coverage focuses on the testable LOGIC. Excluded (not unit-testable / not logic): the codegen
  // native spec (types + TurboModuleRegistry bridge), the barrel, test helpers, the transport I/O
  // layer (exercised via mocks at the DeviceIntel level), and the doc-only wire envelope.
  collectCoverageFrom: [
    "src/**/*.ts",
    "!src/**/*.spec.ts",
    "!src/NativeDeviceIntel.ts",
    "!src/index.ts",
    "!src/testing/**",
    "!src/transport/**",
  ],
};
