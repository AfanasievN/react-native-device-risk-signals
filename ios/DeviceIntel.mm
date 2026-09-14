#import "DeviceIntel.h"
#import "GeolocationInfoProvider.h"
#import "HardwareInfoProvider.h"
#import "JailbreakDetector.h"
#import "MediaBluetoothAppsProvider.h"
#import "SecurityPostureProvider.h"
// ApplicationInfoProvider, AudioLatencyProvider, DeviceInfoProvider, GpuBenchmarkProvider,
// LocaleInfoProvider, NetworkInfoProvider, NumericConsistencyProvider, RuntimeTimingProvider,
// TelephonyInfoProvider and their SignalStatistics helper now live in the standalone
// `ios-device-risk-signals` Swift Package (product `IOSDeviceRiskSignals`) under
// `sdks/ios/Sources/IOSDeviceRiskSignals`, with the headers below published from that target's
// `include/` directory. This binding is a thin adapter over them and keeps no copy of the
// collection logic.
#import "ApplicationInfoProvider.h"
#import "AudioLatencyProvider.h"
#import "DeviceInfoProvider.h"
#import "GpuBenchmarkProvider.h"
#import "LocaleInfoProvider.h"
#import "NetworkInfoProvider.h"
#import "NumericConsistencyProvider.h"
#import "RuntimeTimingProvider.h"
#import "TelephonyInfoProvider.h"
#import <React/RCTBridgeModule.h>

@implementation DeviceIntel {
  dispatch_queue_t _probeQueue;
  DeviceInfoProvider *_deviceInfo;
  JailbreakDetector *_jailbreak;
  NetworkInfoProvider *_network;
  TelephonyInfoProvider *_telephony;
  LocaleInfoProvider *_locale;
  GeolocationInfoProvider *_geolocation;
  MediaBluetoothAppsProvider *_mediaBluetoothApps;
  GpuBenchmarkProvider *_gpuBenchmark;
  AudioLatencyProvider *_audioLatency;
  HardwareInfoProvider *_hardware;
  ApplicationInfoProvider *_application;
  SecurityPostureProvider *_securityPosture;
  RuntimeTimingProvider *_runtimeTiming;
  NumericConsistencyProvider *_numericConsistency;
}

RCT_EXPORT_MODULE(DeviceIntel)

- (NSString *)getRandomSessionId
{
  return [NSUUID UUID].UUIDString.lowercaseString;
}

- (instancetype)init
{
  self = [super init];
  if (self) {
    _probeQueue = dispatch_queue_create(
      "com.reactnativedeviceintel.DeviceIntel.probes",
      DISPATCH_QUEUE_CONCURRENT
    );
    _deviceInfo = [DeviceInfoProvider new];
    _jailbreak = [JailbreakDetector new];
    _network = [NetworkInfoProvider new];
    _telephony = [TelephonyInfoProvider new];
    _locale = [LocaleInfoProvider new];
    _geolocation = [GeolocationInfoProvider new];
    _mediaBluetoothApps = [MediaBluetoothAppsProvider new];
    _gpuBenchmark = [GpuBenchmarkProvider new];
    _audioLatency = [AudioLatencyProvider new];
    _hardware = [HardwareInfoProvider new];
    _application = [ApplicationInfoProvider new];
    _securityPosture = [SecurityPostureProvider new];
    _runtimeTiming = [RuntimeTimingProvider new];
    _numericConsistency = [NumericConsistencyProvider new];
  }
  return self;
}

// Each probe runs on this module-owned CONCURRENT queue so the probes the JS registry fires in
// parallel actually execute in parallel. Without a methodQueue of our own, RCTTurboModuleManager
// falls back to `_sharedModuleQueue` — one DISPATCH_QUEUE_SERIAL queue
// ("com.meta.react.turbomodulemanager.queue") shared with *every other* TurboModule in the host app
// that also declares none (RCTTurboModuleManager.mm: the queue is created in -initWithBridge:, and
// -_attachMethodQueue... assigns it whenever [module methodQueue] returns nil). Promise-returning
// methods are dispatched through ModuleNativeMethodCallInvoker::invokeAsync, which is a plain
// dispatch_async onto that queue, so on a serial queue every probe queued behind a slow one (GPU
// benchmark, audio latency, geolocation) blows its short per-probe JS timeout while merely waiting
// its turn — `src/probes/registry.ts` starts each timeout at dispatch, not when native work begins.
// This is the iOS counterpart of the Android fix: see the `probeExecutor` comment in
// android/src/main/java/com/reactnativedeviceintel/DeviceIntelModule.kt.
// Providers are stateless per call, so concurrent invocation is safe. The one piece of
// process-global state a probe touches — UIDevice.batteryMonitoringEnabled — is explicitly
// serialized inside HardwareInfoProvider rather than relying on the queue to do it.
- (dispatch_queue_t)methodQueue
{
  return _probeQueue;
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeDeviceIntelSpecJSI>(params);
}

- (void)getDeviceIdentity:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_deviceInfo deviceIdentity]);
}

- (void)getHardwareSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_hardware hardwareSignals]);
}

- (void)getFontsFingerprint:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_hardware fontsFingerprint]);
}

- (void)getApplicationSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_application applicationSignals]);
}

- (void)getOsIntegrity:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_jailbreak osIntegrity]);
}

- (void)getFridaScanSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  // No frida-port scan on iOS: frida on jailbroken iOS is caught by the dyld image scan in the fast
  // bundle instead. The JS probe is androidOnly-disabled; this stub exists for Spec parity.
  resolve(@{@"scanPerformed" : @NO});
}

- (void)getForkJailbreakSignal:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_jailbreak forkJailbreakSignal]);
}

- (void)getNetworkSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_network networkSignals]);
}

- (void)getTelephonySignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_telephony telephonySignals]);
}

- (void)getLocaleSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_locale localeSignals]);
}

- (void)getGeolocationSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_geolocation geolocationSignals]);
}

- (void)getMediaBluetoothAppsSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_mediaBluetoothApps mediaBluetoothAppsSignals]);
}

- (void)getGpuBenchmark:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_gpuBenchmark gpuBenchmark]);
}

- (void)getAudioLatency:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_audioLatency audioLatency]);
}

- (void)getDeviceSecurityPosture:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_securityPosture deviceSecurityPosture]);
}

- (void)getTransactionSafetySignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_securityPosture transactionSafetySignals]);
}

- (void)getRuntimeTimingSignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_runtimeTiming runtimeTimingSignals]);
}

- (void)getNumericConsistencySignals:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  resolve([_numericConsistency numericConsistencySignals]);
}

@end
