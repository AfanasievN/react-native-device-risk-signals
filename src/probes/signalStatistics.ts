export type DistributionSummary = {
  sampleCount: number;
  median: number;
  p95: number;
  mad: number;
};

export function summarize(values: readonly number[]): DistributionSummary | undefined {
  const finite = values.filter(Number.isFinite).slice().sort((a, b) => a - b);
  if (finite.length === 0) return undefined;
  const median = percentile(finite, 0.5);
  const deviations = finite.map((value) => Math.abs(value - median)).sort((a, b) => a - b);
  return {sampleCount: finite.length, median, p95: percentile(finite, 0.95), mad: percentile(deviations, 0.5)};
}

function percentile(sorted: readonly number[], percentileValue: number): number {
  const index = Math.ceil(percentileValue * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(sorted.length - 1, index))] ?? 0;
}
