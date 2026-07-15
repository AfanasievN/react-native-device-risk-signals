#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * geolocation — OPPORTUNISTIC. Never calls requestWhenInUseAuthorization (that would prompt); reads
 * the authorization status and a cached last-known location only if already authorized. iOS exposes
 * no third-party mock-location flag, so isFromMockProvider is Android-only (omitted here).
 */
@interface GeolocationInfoProvider : NSObject

- (NSDictionary *)geolocationSignals;

@end

NS_ASSUME_NONNULL_END
