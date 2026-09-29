#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
// Main-thread only. No socket I/O or fork; active fields are omitted.
@interface OsIntegrityProvider : NSObject
- (NSDictionary *)osIntegrity;
@end
NS_ASSUME_NONNULL_END
