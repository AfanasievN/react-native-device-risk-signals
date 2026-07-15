#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * media_bluetooth_apps (iOS side). This class covers audio route, screen-capture/mirroring, accessibility (assistive
 * tech is a device-farm tell), and an app-audit via canOpenURL over VPN/anonymity schemes that are
 * ALREADY declared in Info.plist. Bluetooth is intentionally EXCLUDED on iOS (would need a new
 * NSBluetoothAlwaysUsageDescription prompt). Android provides Bluetooth.
 */
@interface MediaBluetoothAppsProvider : NSObject

- (NSDictionary *)mediaBluetoothAppsSignals;

@end

NS_ASSUME_NONNULL_END
