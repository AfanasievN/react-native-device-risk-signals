#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * telephony — opportunistic CoreTelephony carrier reads. Apple has progressively nulled CTCarrier
 * for non-carrier apps since iOS 16 (carrierName becomes "--", MCC/MNC "65535"), so most fields are
 * expected-null on modern iOS — documented degradation, not a promised value. IMEI is never exposed
 * on iOS and is not attempted.
 */
@interface TelephonyInfoProvider : NSObject

- (NSDictionary *)telephonySignals;

@end

NS_ASSUME_NONNULL_END
