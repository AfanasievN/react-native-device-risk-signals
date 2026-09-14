#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * device_identity — plain, synchronous, permission-free reads. JailbreakDetector owns the separate
 * os_integrity category.
 */
@interface DeviceInfoProvider : NSObject

- (NSDictionary *)deviceIdentity;

@end

NS_ASSUME_NONNULL_END
