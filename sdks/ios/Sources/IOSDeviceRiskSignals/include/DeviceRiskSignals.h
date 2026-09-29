#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
/** Explicit synchronous collection; init performs no observation.
 Main thread: hardware, integrity, geolocation, media, posture, transaction.
 Worker only: GPU. Device identity may synchronously hop to main.
 All sensitive/expensive methods require an explicit host call; no collect-all default. */
@interface DeviceRiskSignals : NSObject
- (NSDictionary *)collectDeviceIdentity;
- (NSDictionary *)collectHardware;
- (NSDictionary *)collectFonts;
- (NSDictionary *)collectOsIntegrity;
- (NSDictionary *)collectApplication;
- (NSDictionary *)collectLocale;
- (NSDictionary *)collectNetwork;
- (NSDictionary *)collectTelephony;
- (NSDictionary *)collectGeolocation;
- (NSDictionary *)collectMediaBluetoothApps;
- (NSDictionary *)collectDeviceSecurityPosture;
- (NSDictionary *)collectTransactionSafety;
- (NSDictionary *)collectRuntimeTiming;
- (NSDictionary *)collectNumericConsistency;
- (NSDictionary *)collectAudioLatency;
- (NSDictionary *)collectGpuBenchmark;
@end
NS_ASSUME_NONNULL_END
