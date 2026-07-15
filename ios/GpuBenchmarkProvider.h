#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * gpu_benchmark (iOS) — Metal. Reports the GPU device name (the fingerprint) and a blit-throughput
 * count over a fixed time budget, timed with CFAbsoluteTimeGetCurrent (wall clock — deliberately NOT
 * mach_absolute_time/systemUptime, so no Required-Reason API and no PrivacyInfo.xcprivacy entry).
 * GPU time per buffer comes from MTLCommandBuffer GPUStart/GPUEndTime. Self-skips on the simulator.
 * Never runs until config enables the probe (ships disabled — see gpuBenchmarkProbe.ts).
 */
@interface GpuBenchmarkProvider : NSObject

- (NSDictionary *)gpuBenchmark;

@end

NS_ASSUME_NONNULL_END
