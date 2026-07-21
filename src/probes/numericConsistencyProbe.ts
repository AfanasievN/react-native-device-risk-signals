import NativeDeviceIntel, {type NumericConsistencySignals} from "../NativeDeviceIntel";
import type {Probe} from "./types";

function integerVector(): number {
  let hash = 2166136261 >>> 0;
  for (let index = 0; index < 1024; index++) {
    hash = (hash ^ Math.imul(index, 2654435761)) >>> 0;
    hash = Math.imul(hash, 16777619) >>> 0;
  }
  return hash;
}

function floatVector(): number[] {
  return [Math.sqrt(2), Math.sin(0.5), Math.cos(0.5), Math.log(2), Math.exp(0.25)];
}

async function collectNumericConsistency(): Promise<NumericConsistencySignals> {
  const native = await NativeDeviceIntel.getNumericConsistencySignals();
  const jsInteger = integerVector();
  const jsFloats = floatVector();
  const differences = jsFloats.map((value, index) => Math.abs(value - (native.floatVector[index] ?? Number.NaN)));
  const finiteDifferences = differences.filter(Number.isFinite);
  const floatMismatchCount = differences.filter((difference) => !Number.isFinite(difference) || difference > 1e-12).length;
  const integerVectorMatches = jsInteger === native.integerVectorResult;
  return {
    integerVectorMatches,
    integerMismatchCount: integerVectorMatches ? 0 : 1,
    floatSampleCount: jsFloats.length,
    floatMismatchCount,
    floatMaxAbsoluteDifference: finiteDifferences.length > 0 ? Math.max(...finiteDifferences) : undefined,
    signedZeroPreserved: native.signedZeroPreserved && 1 / -0 === Number.NEGATIVE_INFINITY,
    subnormalPreserved: native.subnormalPreserved && Number.MIN_VALUE > 0,
  };
}

export const numericConsistencyProbes: Probe[] = [
  {
    id: "numeric_consistency",
    timeoutMs: 500,
    enabled: () => false,
    collect: collectNumericConsistency,
  } satisfies Probe<NumericConsistencySignals>,
];
