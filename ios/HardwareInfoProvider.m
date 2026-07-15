#import "HardwareInfoProvider.h"
#import <CommonCrypto/CommonCrypto.h>
#import <UIKit/UIKit.h>
#import <mach/mach.h>

@implementation HardwareInfoProvider

- (NSDictionary *)hardwareSignals
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];

  // CPU + total RAM — thread-safe, not a Required-Reason API.
  NSProcessInfo *processInfo = [NSProcessInfo processInfo];
  result[@"processorCount"] = @(processInfo.processorCount);
  result[@"totalMemoryBytes"] = @(processInfo.physicalMemory);
  int64_t freeMemory = [self freeMemoryBytes];
  if (freeMemory > 0) {
    result[@"freeMemoryBytes"] = @(freeMemory);
  }

  // UIScreen / UIDevice / UIFont must be touched on the main thread; TurboModule methods run off it.
  void (^work)(void) = ^{
    UIScreen *screen = [UIScreen mainScreen];
    CGRect bounds = screen.bounds;
    CGFloat scale = screen.scale;
    result[@"screenWidthPx"] = @((NSInteger)(bounds.size.width * scale));
    result[@"screenHeightPx"] = @((NSInteger)(bounds.size.height * scale));
    result[@"screenDensity"] = @(scale);

    CGRect native = screen.nativeBounds; // already in physical pixels
    result[@"screenPhysicalWidthPx"] = @((NSInteger)native.size.width);
    result[@"screenPhysicalHeightPx"] = @((NSInteger)native.size.height);
    result[@"screenPhysicalDensity"] = @(screen.nativeScale);
    result[@"screenBrightness"] = @(screen.brightness);

    UIDevice *device = [UIDevice currentDevice];
    // UIDevice is a process-wide singleton; enabling battery monitoring is required to read the level/
    // state but is global state — save and restore it so this passive probe leaves no side effect.
    BOOL previousBatteryMonitoring = device.batteryMonitoringEnabled;
    device.batteryMonitoringEnabled = YES;
    float level = device.batteryLevel; // -1 when unknown
    if (level >= 0) {
      result[@"batteryLevel"] = @(level);
    }
    result[@"batteryState"] = [self batteryStateString:device.batteryState];
    device.batteryMonitoringEnabled = previousBatteryMonitoring;
  };
  if ([NSThread isMainThread]) {
    work();
  } else {
    dispatch_sync(dispatch_get_main_queue(), work);
  }

  return result;
}

// fonts — split out of hardwareSignals into its own probe (isolated, generous timeout on the JS side).
// UIFont must be touched on the main thread; TurboModule methods run off it, so dispatch there.
- (NSDictionary *)fontsFingerprint
{
  __block NSString *digest = nil;
  void (^work)(void) = ^{
    digest = [self fontsDigest];
  };
  if ([NSThread isMainThread]) {
    work();
  } else {
    dispatch_sync(dispatch_get_main_queue(), work);
  }
  return digest ? @{@"fontsDigest" : digest} : @{};
}

- (int64_t)freeMemoryBytes
{
  // mach_host_self() adds a send right to our IPC space on every call — it MUST be balanced with
  // mach_port_deallocate (unlike mach_task_self(), which is cached and must not be deallocated).
  // Single exit path so the port is released exactly once regardless of which read fails.
  mach_port_t host = mach_host_self();
  int64_t freeBytes = -1;
  vm_size_t pageSize = 0;
  if (host_page_size(host, &pageSize) == KERN_SUCCESS) {
    vm_statistics64_data_t stats;
    mach_msg_type_number_t count = HOST_VM_INFO64_COUNT;
    if (host_statistics64(host, HOST_VM_INFO64, (host_info64_t)&stats, &count) == KERN_SUCCESS) {
      freeBytes = (int64_t)stats.free_count * (int64_t)pageSize;
    }
  }
  mach_port_deallocate(mach_task_self(), host);
  return freeBytes;
}

- (NSString *)batteryStateString:(UIDeviceBatteryState)state
{
  switch (state) {
    case UIDeviceBatteryStateCharging:
      return @"charging";
    case UIDeviceBatteryStateFull:
      return @"full";
    case UIDeviceBatteryStateUnplugged:
      return @"unplugged";
    default:
      return @"unknown";
  }
}

- (NSString *)fontsDigest
{
  CC_SHA256_CTX ctx;
  CC_SHA256_Init(&ctx);
  for (NSString *family in [[UIFont familyNames] sortedArrayUsingSelector:@selector(compare:)]) {
    NSData *familyData = [family dataUsingEncoding:NSUTF8StringEncoding];
    CC_SHA256_Update(&ctx, familyData.bytes, (CC_LONG)familyData.length);
    for (NSString *name in [[UIFont fontNamesForFamilyName:family] sortedArrayUsingSelector:@selector(compare:)]) {
      NSData *nameData = [name dataUsingEncoding:NSUTF8StringEncoding];
      CC_SHA256_Update(&ctx, nameData.bytes, (CC_LONG)nameData.length);
    }
  }
  unsigned char digest[CC_SHA256_DIGEST_LENGTH];
  CC_SHA256_Final(digest, &ctx);
  NSMutableString *hex = [NSMutableString stringWithCapacity:CC_SHA256_DIGEST_LENGTH * 2];
  for (int i = 0; i < CC_SHA256_DIGEST_LENGTH; i++) {
    [hex appendFormat:@"%02x", digest[i]];
  }
  return hex;
}

@end
