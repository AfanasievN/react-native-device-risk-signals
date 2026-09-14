#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * application — host app identity (version / build / bundle id) from the main bundle. Permission-free.
 * Repackaging & version-consistency signal.
 */
@interface ApplicationInfoProvider : NSObject

- (NSDictionary *)applicationSignals;

@end

NS_ASSUME_NONNULL_END
