#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * hardware — permission-free device-class + fingerprint entropy (screen / CPU / RAM / battery /
 * brightness). Uses NSProcessInfo (CPU count, physical memory) and mach (free memory) — none are Apple
 * Required-Reason APIs. Deliberately omits disk/storage size and device boot time (both Required-Reason
 * on iOS) and any persistent id. cpuArchitecture/uptime/batteryTemperature are Android-only (omitted
 * here). The installed-fonts digest is exposed separately via -fontsFingerprint (its own `fonts` probe).
 * See NativeDeviceIntel.ts.
 */
@interface HardwareInfoProvider : NSObject

- (NSDictionary *)hardwareSignals;
- (NSDictionary *)fontsFingerprint;

@end

NS_ASSUME_NONNULL_END
