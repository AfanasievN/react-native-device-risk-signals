#import "DeviceInfoProvider.h"
#import <TargetConditionals.h>
#import <UIKit/UIKit.h>
#import <sys/sysctl.h>

@implementation DeviceInfoProvider

- (NSDictionary *)deviceIdentity
{
  UIDevice *device = [UIDevice currentDevice];
  NSMutableDictionary *result = [NSMutableDictionary dictionary];
  result[@"manufacturer"] = @"Apple";
  result[@"model"] = [self hardwareModel];
  result[@"brand"] = @"Apple";
  result[@"systemName"] = device.systemName;
  result[@"systemVersion"] = device.systemVersion;
  result[@"isTablet"] = @(device.userInterfaceIdiom == UIUserInterfaceIdiomPad);
#if TARGET_OS_MACCATALYST
  result[@"isMacCatalystApp"] = @YES;
#else
  result[@"isMacCatalystApp"] = @NO;
#endif
  if (@available(iOS 14.0, *)) {
    NSProcessInfo *processInfo = NSProcessInfo.processInfo;
    if ([processInfo respondsToSelector:@selector(isiOSAppOnMac)]) {
      result[@"isIosAppOnMac"] = @(processInfo.isiOSAppOnMac);
    }
  }

  // OS / kernel fingerprint. NONE of these sysctl keys are Apple Required-Reason APIs — only
  // kern.boottime is, and it is deliberately NOT read. androidBuild is Android-only (omitted here).
  [self putString:result key:@"osBuild" value:[self sysctlString:"kern.osversion"]];
  [self putString:result key:@"kernelVersion" value:[self sysctlString:"kern.version"]];
  [self putString:result key:@"kernelOsRelease" value:[self sysctlString:"kern.osrelease"]];
  [self putString:result key:@"kernelOsType" value:[self sysctlString:"kern.ostype"]];

  return result;
}

- (NSString *)hardwareModel
{
  return [self sysctlString:"hw.machine"] ?: @"";
}

- (NSString *_Nullable)sysctlString:(const char *)name
{
  size_t size = 0;
  if (sysctlbyname(name, NULL, &size, NULL, 0) != 0 || size == 0) {
    return nil;
  }
  char *buffer = malloc(size);
  if (buffer == NULL) {
    return nil;
  }
  NSString *result = nil;
  if (sysctlbyname(name, buffer, &size, NULL, 0) == 0) {
    result = [NSString stringWithUTF8String:buffer];
  }
  free(buffer);
  return result;
}

- (void)putString:(NSMutableDictionary *)dict key:(NSString *)key value:(NSString *_Nullable)value
{
  if (value.length > 0) {
    dict[key] = value;
  }
}

@end
