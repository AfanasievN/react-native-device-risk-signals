#import "ApplicationInfoProvider.h"

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
  return result;
}

@end
