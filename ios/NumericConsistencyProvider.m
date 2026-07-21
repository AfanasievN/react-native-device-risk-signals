#import "NumericConsistencyProvider.h"
#import <float.h>
#import <math.h>
#import <stdint.h>

@implementation NumericConsistencyProvider

- (NSDictionary *)numericConsistencySignals
{
  uint32_t hash = 2166136261U;
  for (uint32_t index = 0; index < 1024; index++) {
    hash ^= index * 2654435761U;
    hash *= 16777619U;
  }
  NSArray<NSNumber *> *floatVector = @[
    @(sqrt(2.0)), @(sin(0.5)), @(cos(0.5)), @(log(2.0)), @(exp(0.25))
  ];
  double negativeZero = -0.0;
  return @{
    @"integerVectorResult": @((double)hash),
    @"floatVector": floatVector,
    @"signedZeroPreserved": @(isinf(1.0 / negativeZero) && signbit(1.0 / negativeZero)),
    @"subnormalPreserved": @(DBL_TRUE_MIN > 0.0 && DBL_TRUE_MIN * 1.0 == DBL_TRUE_MIN),
  };
}

@end
