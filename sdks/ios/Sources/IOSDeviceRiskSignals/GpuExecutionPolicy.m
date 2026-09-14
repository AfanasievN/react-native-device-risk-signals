#import "GpuExecutionPolicy.h"

// The message is byte-for-byte the one the Android core raises, so a crash report from either
// platform reads the same.
static NSString *const kRNDIWorkerThreadRequired = @"GPU collection requires a worker thread";

void RNDIRequireWorkerThread(BOOL isMainThread)
{
  if (isMainThread) {
    [NSException raise:NSInternalInconsistencyException format:@"%@", kRNDIWorkerThreadRequired];
  }
}

NSException *_Nullable RNDIExecutionPolicyFailure(void (NS_NOESCAPE ^block)(void))
{
  @try {
    block();
  } @catch (NSException *exception) {
    return exception;
  }
  return nil;
}
