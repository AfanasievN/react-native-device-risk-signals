#import "RuntimeTimingProvider.h"
#import "SignalStatistics.h"

static const NSUInteger kRuntimeTimingSamples = 256;

@implementation RuntimeTimingProvider

- (NSDictionary *)runtimeTimingSignals
{
  NSMutableArray<NSNumber *> *intervals = [NSMutableArray arrayWithCapacity:kRuntimeTimingSamples];
  CFTimeInterval previous = CFAbsoluteTimeGetCurrent();
  for (NSUInteger index = 0; index < kRuntimeTimingSamples; index++) {
    CFTimeInterval current = CFAbsoluteTimeGetCurrent();
    double deltaNs = (current - previous) * 1000000000.0;
    if (deltaNs > 0) [intervals addObject:@(deltaNs)];
    previous = current;
  }
  NSDictionary<NSString *, NSNumber *> *summary = RNDISummarize(intervals);
  NSNumber *resolution = [intervals valueForKeyPath:@"@min.self"] ?: @0;
  return @{
    @"nativeClockSource": @"cf_absolute_time",
    @"nativeSampleCount": @(intervals.count),
    @"nativeTimerResolutionNs": resolution,
    @"nativeIntervalMedianNs": summary[@"median"] ?: @0,
    @"nativeIntervalP95Ns": summary[@"p95"] ?: @0,
    @"nativeIntervalMadNs": summary[@"mad"] ?: @0,
  };
}

@end
