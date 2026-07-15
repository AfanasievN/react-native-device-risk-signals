#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * network — interface topology (getifaddrs), VPN presence (tun/utun/ppp/ipsec interfaces), and
 * system HTTP proxy. Permission-free. No SSID: CNCopyCurrentNetworkInfo needs a Wi-Fi entitlement
 * the host app may not carry, so `wifiSsid`/`wifiBssid` are omitted (expected-null, per the TS contract).
 */
@interface NetworkInfoProvider : NSObject

- (NSDictionary *)networkSignals;

@end

NS_ASSUME_NONNULL_END
