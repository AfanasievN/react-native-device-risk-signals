import fs from "node:fs";
import path from "node:path";
import ts from "typescript";

const PROBE_TYPE_NAMES = {
  device_identity: "DeviceIdentity",
  hardware: "HardwareSignals",
  fonts: "FontsSignals",
  os_integrity: "OsIntegritySignals",
  os_integrity_frida_scan: "FridaScanSignals",
  os_integrity_fork_test: "ForkJailbreakSignal",
  network: "NetworkSignals",
  telephony: "TelephonySignals",
  locale: "LocaleSignals",
  geolocation: "GeolocationSignals",
  media_bluetooth_apps: "MediaBluetoothAppsSignals",
  gpu_benchmark: "GpuBenchmarkSignals",
  audio_latency: "AudioLatencySignals",
  application: "ApplicationSignals",
  device_security_posture: "DeviceSecurityPostureSignals",
  transaction_safety: "TransactionSafetySignals",
  runtime: "RuntimeSignals",
  runtime_timing: "RuntimeTimingSignals",
  numeric_consistency: "NumericConsistencySignals",
};

function propertyName(node) {
  if (ts.isIdentifier(node) || ts.isStringLiteral(node) || ts.isNumericLiteral(node)) return node.text;
  return undefined;
}

export function readSignalContract(root) {
  const sourcePaths = [
    path.join(root, "src/NativeDeviceIntel.ts"),
    path.join(root, "src/probes/runtimeProbe.ts"),
  ];
  const aliases = new Map();

  for (const sourcePath of sourcePaths) {
    const sourceFile = ts.createSourceFile(
      sourcePath,
      fs.readFileSync(sourcePath, "utf8"),
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TS,
    );
    for (const statement of sourceFile.statements) {
      if (ts.isTypeAliasDeclaration(statement)) {
        aliases.set(statement.name.text, {node: statement.type, sourceFile});
      }
    }
  }

  function objectMembers(node, seen = new Set()) {
    if (ts.isTypeLiteralNode(node)) return node.members;
    if (ts.isIntersectionTypeNode(node)) return node.types.flatMap((part) => objectMembers(part, seen));
    if (ts.isParenthesizedTypeNode(node)) return objectMembers(node.type, seen);
    if (ts.isTypeReferenceNode(node) && ts.isIdentifier(node.typeName)) {
      const name = node.typeName.text;
      if (seen.has(name)) return [];
      const alias = aliases.get(name);
      if (!alias) return [];
      return objectMembers(alias.node, new Set([...seen, name]));
    }
    return [];
  }

  function typeText(node, sourceFile) {
    return node.getText(sourceFile).replace(/\s+/g, " ");
  }

  function schemaFor(node, seen = new Set()) {
    if (node.kind === ts.SyntaxKind.StringKeyword) return {type: "string"};
    if (node.kind === ts.SyntaxKind.NumberKeyword) return {type: "number"};
    if (node.kind === ts.SyntaxKind.BooleanKeyword) return {type: "boolean"};
    if (node.kind === ts.SyntaxKind.NullKeyword) return {type: "null"};
    if (node.kind === ts.SyntaxKind.UnknownKeyword || node.kind === ts.SyntaxKind.AnyKeyword) return {};
    if (ts.isArrayTypeNode(node)) return {type: "array", items: schemaFor(node.elementType, seen)};
    if (ts.isParenthesizedTypeNode(node)) return schemaFor(node.type, seen);
    if (ts.isLiteralTypeNode(node)) {
      if (ts.isStringLiteral(node.literal)) return {type: "string", const: node.literal.text};
      if (ts.isNumericLiteral(node.literal)) return {type: "number", const: Number(node.literal.text)};
      if (node.literal.kind === ts.SyntaxKind.TrueKeyword) return {type: "boolean", const: true};
      if (node.literal.kind === ts.SyntaxKind.FalseKeyword) return {type: "boolean", const: false};
    }
    if (ts.isUnionTypeNode(node)) {
      const variants = node.types
        .filter((part) => part.kind !== ts.SyntaxKind.UndefinedKeyword)
        .map((part) => schemaFor(part, seen));
      return variants.length === 1 ? variants[0] : {anyOf: variants};
    }
    if (ts.isTypeReferenceNode(node) && ts.isIdentifier(node.typeName)) {
      if ((node.typeName.text === "Array" || node.typeName.text === "ReadonlyArray") && node.typeArguments?.[0]) {
        return {type: "array", items: schemaFor(node.typeArguments[0], seen)};
      }
      const name = node.typeName.text;
      if (seen.has(name)) return {};
      const alias = aliases.get(name);
      return alias ? schemaFor(alias.node, new Set([...seen, name])) : {};
    }
    if (ts.isIntersectionTypeNode(node)) {
      const schemas = node.types.map((part) => schemaFor(part, seen));
      const properties = Object.assign({}, ...schemas.map((schema) => schema.properties ?? {}));
      const required = schemas.flatMap((schema) => schema.required ?? []);
      return {
        type: "object",
        properties,
        ...(required.length > 0 ? {required: [...new Set(required)]} : {}),
        additionalProperties: false,
      };
    }
    if (ts.isTypeLiteralNode(node)) {
      const properties = {};
      const required = [];
      for (const member of node.members) {
        if (!ts.isPropertySignature(member) || !member.type) continue;
        const name = propertyName(member.name);
        if (!name) continue;
        properties[name] = schemaFor(member.type, seen);
        if (!member.questionToken) required.push(name);
      }
      return {
        type: "object",
        properties,
        ...(required.length > 0 ? {required} : {}),
        additionalProperties: false,
      };
    }
    return {};
  }

  const contract = {};
  for (const [probeId, typeName] of Object.entries(PROBE_TYPE_NAMES)) {
    const alias = aliases.get(typeName);
    if (!alias) throw new Error(`Missing TypeScript signal type ${typeName}`);
    const fields = {};
    for (const member of objectMembers(alias.node)) {
      if (!ts.isPropertySignature(member) || !member.type) continue;
      const name = propertyName(member.name);
      if (!name) continue;
      fields[name] = {
        type: typeText(member.type, alias.sourceFile),
        optional: Boolean(member.questionToken),
        schema: schemaFor(member.type),
      };
    }
    contract[probeId] = {typeName, fields};
  }

  return contract;
}
