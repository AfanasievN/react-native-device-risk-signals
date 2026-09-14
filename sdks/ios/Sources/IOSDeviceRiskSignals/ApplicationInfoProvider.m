#import "ApplicationInfoProvider.h"
#import <TargetConditionals.h>

@implementation ApplicationInfoProvider

- (NSDictionary *)applicationSignals
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];
  NSDictionary *info = [NSBundle mainBundle].infoDictionary;

  NSString *version = info[@"CFBundleShortVersionString"];
  NSString *build = info[@"CFBundleVersion"];
  NSString *bundleId = [NSBundle mainBundle].bundleIdentifier;

  if (version.length > 0) {
    result[@"appVersion"] = version;
  }
  if (build.length > 0) {
    result[@"appBuild"] = build;
  }
  if (bundleId.length > 0) {
    result[@"bundleId"] = bundleId;
  }
  NSString *executable = info[@"CFBundleExecutable"];
  if (executable.length > 0) result[@"bundleExecutable"] = executable;
  NSString *minimumOS = info[@"MinimumOSVersion"];
  if (minimumOS.length > 0) {
    result[@"minimumOsVersion"] = minimumOS;
  }
  NSURL *receiptURL = [NSBundle mainBundle].appStoreReceiptURL;
  BOOL receiptPresent = receiptURL != nil && [[NSFileManager defaultManager] fileExistsAtPath:receiptURL.path];
  result[@"receiptPresent"] = @(receiptPresent);
  if (receiptPresent) {
    result[@"receiptEnvironment"] = [receiptURL.lastPathComponent isEqualToString:@"sandboxReceipt"]
        ? @"sandbox" : @"production";
  }
  result[@"isAppExtension"] = @([[NSBundle mainBundle].bundlePath.pathExtension.lowercaseString isEqualToString:@"appex"]);
  NSString *profilePath = [[NSBundle mainBundle].bundlePath stringByAppendingPathComponent:@"embedded.mobileprovision"];
  result[@"embeddedProvisioningProfilePresent"] = @(
    [[NSFileManager defaultManager] fileExistsAtPath:profilePath]
  );
  // getTaskAllowEntitlement is intentionally omitted. SecTask entitlement lookup symbols are not
  // declared by the public iPhoneOS Security headers, so using hand-written declarations would cross
  // the package's public-system-API boundary. The optional contract field remains reserved.
#if TARGET_OS_SIMULATOR
  result[@"isSimulatorBuild"] = @YES;
  result[@"isDebuggable"] = @YES;
#else
  result[@"isSimulatorBuild"] = @NO;
#if DEBUG
  result[@"isDebuggable"] = @YES;
#else
  result[@"isDebuggable"] = @NO;
#endif
#endif
  return result;
}

@end
