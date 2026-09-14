#import "SignalStatistics.h"
#import <math.h>

static double RNDIPercentile(NSArray<NSNumber *> *sorted, double percentile)
{
  NSInteger index = (NSInteger)ceil(percentile * sorted.count) - 1;
  index = MAX(0, MIN((NSInteger)sorted.count - 1, index));
  return sorted[(NSUInteger)index].doubleValue;
}

NSDictionary<NSString *, NSNumber *> *_Nullable RNDISummarize(NSArray<NSNumber *> *values)
{
  NSMutableArray<NSNumber *> *finite = [NSMutableArray array];
  for (NSNumber *value in values) {
    if (isfinite(value.doubleValue)) [finite addObject:value];
  }
  if (finite.count == 0) return nil;
  [finite sortUsingComparator:^NSComparisonResult(NSNumber *left, NSNumber *right) {
    return [left compare:right];
  }];
  double median = RNDIPercentile(finite, 0.5);
  NSMutableArray<NSNumber *> *deviations = [NSMutableArray arrayWithCapacity:finite.count];
  double sum = 0;
  for (NSNumber *value in finite) {
    sum += value.doubleValue;
    [deviations addObject:@(fabs(value.doubleValue - median))];
  }
  [deviations sortUsingComparator:^NSComparisonResult(NSNumber *left, NSNumber *right) {
    return [left compare:right];
  }];
  double mean = sum / finite.count;
  double squaredDifferenceSum = 0;
  for (NSNumber *value in finite) {
    squaredDifferenceSum += pow(value.doubleValue - mean, 2);
  }
  double standardDeviation = sqrt(squaredDifferenceSum / finite.count);
  NSMutableDictionary<NSString *, NSNumber *> *result = [@{
    @"sampleCount": @(finite.count),
    @"median": @(median),
    @"p95": @(RNDIPercentile(finite, 0.95)),
    @"mad": @(RNDIPercentile(deviations, 0.5)),
  } mutableCopy];
  if (mean > 0) result[@"coefficientOfVariation"] = @(standardDeviation / mean);
  return result;
}

NSNumber *_Nullable RNDIWarmupSlope(NSArray<NSNumber *> *values)
{
  if (values.count < 4) return nil;
  NSUInteger midpoint = values.count / 2;
  double firstSum = 0;
  double secondSum = 0;
  for (NSUInteger index = 0; index < midpoint; index++) firstSum += values[index].doubleValue;
  for (NSUInteger index = midpoint; index < values.count; index++) secondSum += values[index].doubleValue;
  double firstMean = firstSum / midpoint;
  if (!isfinite(firstMean) || firstMean <= 0) return nil;
  double secondMean = secondSum / (values.count - midpoint);
  if (!isfinite(secondMean)) return nil;
  return @((secondMean - firstMean) / firstMean);
}
