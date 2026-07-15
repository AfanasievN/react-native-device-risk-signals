#import "DeviceIntel.h"
#import "ApplicationInfoProvider.h"
#import "AudioLatencyProvider.h"
#import "DeviceInfoProvider.h"
#import "GeolocationInfoProvider.h"
#import "GpuBenchmarkProvider.h"
#import "HardwareInfoProvider.h"
#import "JailbreakDetector.h"
#import "LocaleInfoProvider.h"
#import "MediaBluetoothAppsProvider.h"
#import "NetworkInfoProvider.h"
#import "TelephonyInfoProvider.h"
#import <React/RCTBridgeModule.h>

@implementation DeviceIntel {
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
}

RCT_EXPORT_MODULE(DeviceIntel)

- (instancetype)init
{
  self = [super init];
  if (self) {
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
  }
  return self;
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

@end
