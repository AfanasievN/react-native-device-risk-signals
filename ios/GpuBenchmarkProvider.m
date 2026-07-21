#import "GpuBenchmarkProvider.h"
#import "SignalStatistics.h"
#import <Metal/Metal.h>
#import <QuartzCore/QuartzCore.h>

static const NSUInteger kBufferBytes = 1 << 20; // 1 MB blit workload
static const double kBudgetMs = 50.0;

@implementation GpuBenchmarkProvider

- (NSDictionary *)gpuBenchmark
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];

#if TARGET_OS_SIMULATOR
  // GPU timing on the simulator is meaningless as an entropy signal.
  result[@"benchmarkPerformed"] = @NO;
  result[@"skippedReason"] = @"emulator";
  return result;
#else
  id<MTLDevice> device = MTLCreateSystemDefaultDevice();
  if (device == nil) {
    result[@"benchmarkPerformed"] = @NO;
    result[@"skippedReason"] = @"unsupported";
    return result;
  }

  @try {
    if (device.name.length > 0) {
      result[@"rendererName"] = device.name; // e.g. "Apple A15 GPU" — the fingerprint.
    }

    id<MTLCommandQueue> queue = [device newCommandQueue];
    id<MTLBuffer> buffer = [device newBufferWithLength:kBufferBytes options:MTLResourceStorageModePrivate];
    if (queue == nil || buffer == nil) {
      result[@"benchmarkPerformed"] = @NO;
      result[@"skippedReason"] = @"unsupported";
      return result;
    }

    NSInteger drawCalls = 0;
    double gpuTimeMs = 0;
    NSMutableArray<NSNumber *> *operationTimesMs = [NSMutableArray array];
    CFTimeInterval start = CFAbsoluteTimeGetCurrent();
    while ((CFAbsoluteTimeGetCurrent() - start) * 1000.0 < kBudgetMs) {
      CFTimeInterval operationStart = CFAbsoluteTimeGetCurrent();
      id<MTLCommandBuffer> cmd = [queue commandBuffer];
      id<MTLBlitCommandEncoder> blit = [cmd blitCommandEncoder];
      [blit fillBuffer:buffer range:NSMakeRange(0, kBufferBytes) value:(uint8_t)(drawCalls & 0xFF)];
      [blit endEncoding];
      [cmd commit];
      [cmd waitUntilCompleted];
      if (cmd.GPUEndTime > cmd.GPUStartTime) {
        gpuTimeMs = (cmd.GPUEndTime - cmd.GPUStartTime) * 1000.0; // last buffer's GPU time
      }
      [operationTimesMs addObject:@((CFAbsoluteTimeGetCurrent() - operationStart) * 1000.0)];
      drawCalls++;
    }
    double durationMs = (CFAbsoluteTimeGetCurrent() - start) * 1000.0;

    result[@"benchmarkPerformed"] = @YES;
    result[@"drawCallsCompleted"] = @(drawCalls);
    result[@"durationMs"] = @((NSInteger)durationMs);
    if (gpuTimeMs > 0) {
      result[@"gpuTimeMs"] = @(gpuTimeMs);
    }
    NSDictionary<NSString *, NSNumber *> *summary = RNDISummarize(operationTimesMs);
    if (summary != nil) {
      result[@"operationTimeP50Ms"] = summary[@"median"];
      result[@"operationTimeP95Ms"] = summary[@"p95"];
      result[@"operationTimeMadMs"] = summary[@"mad"];
      if (summary[@"coefficientOfVariation"] != nil) {
        result[@"operationTimeCoefficientOfVariation"] = summary[@"coefficientOfVariation"];
      }
    }
    NSNumber *warmupSlope = RNDIWarmupSlope(operationTimesMs);
    if (warmupSlope != nil) result[@"warmupSlope"] = warmupSlope;
  } @catch (NSException *exception) {
    result[@"benchmarkPerformed"] = @NO;
    result[@"skippedReason"] = @"error";
  }
  return result;
#endif
}

@end
