#import "CollectionThreadPolicy.h"
void RNDIRequireMainThread(void) {
  if (![NSThread isMainThread]) {
    [NSException raise:NSInternalInconsistencyException
                format:@"This collection requires the main thread; the host owns dispatch."];
  }
}
