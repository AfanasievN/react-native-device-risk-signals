#import "DeviceRiskSignals.h"
#import "DeviceInfoProvider.h"
#import "HardwareInfoProvider.h"
#import "OsIntegrityProvider.h"
#import "ApplicationInfoProvider.h"
#import "LocaleInfoProvider.h"
#import "NetworkInfoProvider.h"
#import "TelephonyInfoProvider.h"
#import "GeolocationInfoProvider.h"
#import "MediaBluetoothAppsProvider.h"
#import "SecurityPostureProvider.h"
#import "RuntimeTimingProvider.h"
#import "NumericConsistencyProvider.h"
#import "AudioLatencyProvider.h"
#import "GpuBenchmarkProvider.h"
@implementation DeviceRiskSignals
- (NSDictionary *)collectDeviceIdentity { return [[DeviceInfoProvider new] deviceIdentity]; }
- (NSDictionary *)collectHardware { return [[HardwareInfoProvider new] hardwareSignals]; }
- (NSDictionary *)collectFonts { return [[HardwareInfoProvider new] fontsFingerprint]; }
- (NSDictionary *)collectOsIntegrity { return [[OsIntegrityProvider new] osIntegrity]; }
- (NSDictionary *)collectApplication { return [[ApplicationInfoProvider new] applicationSignals]; }
- (NSDictionary *)collectLocale { return [[LocaleInfoProvider new] localeSignals]; }
- (NSDictionary *)collectNetwork { return [[NetworkInfoProvider new] networkSignals]; }
- (NSDictionary *)collectTelephony { return [[TelephonyInfoProvider new] telephonySignals]; }
- (NSDictionary *)collectGeolocation { return [[GeolocationInfoProvider new] geolocationSignals]; }
- (NSDictionary *)collectMediaBluetoothApps { return [[MediaBluetoothAppsProvider new] mediaBluetoothAppsSignals]; }
- (NSDictionary *)collectDeviceSecurityPosture { return [[SecurityPostureProvider new] deviceSecurityPosture]; }
- (NSDictionary *)collectTransactionSafety { return [[SecurityPostureProvider new] transactionSafetySignals]; }
- (NSDictionary *)collectRuntimeTiming { return [[RuntimeTimingProvider new] runtimeTimingSignals]; }
- (NSDictionary *)collectNumericConsistency { return [[NumericConsistencyProvider new] numericConsistencySignals]; }
- (NSDictionary *)collectAudioLatency { return [[AudioLatencyProvider new] audioLatency]; }
- (NSDictionary *)collectGpuBenchmark { return [[GpuBenchmarkProvider new] gpuBenchmark]; }
@end
