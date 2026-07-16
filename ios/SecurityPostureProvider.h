#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface SecurityPostureProvider : NSObject
- (NSDictionary *)deviceSecurityPosture;
- (NSDictionary *)transactionSafetySignals;
@end

NS_ASSUME_NONNULL_END
