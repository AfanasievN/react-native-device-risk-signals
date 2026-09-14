#import "TelephonyInfoProvider.h"
#import <CoreTelephony/CTCarrier.h>
#import <CoreTelephony/CTTelephonyNetworkInfo.h>

@implementation TelephonyInfoProvider

- (NSDictionary *)telephonySignals
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];

  CTTelephonyNetworkInfo *info = [[CTTelephonyNetworkInfo alloc] init];

  // serviceSubscriberCellularProviders (iOS 12+) maps multiple SIMs to carriers.
  NSDictionary<NSString *, CTCarrier *> *providers = nil;
#if !TARGET_OS_SIMULATOR
  if ([info respondsToSelector:@selector(serviceSubscriberCellularProviders)]) {
    providers = info.serviceSubscriberCellularProviders;
  }
#endif

  CTCarrier *carrier = providers.allValues.firstObject;
  if (carrier == nil) {
    // Fall back to the deprecated single-carrier accessor where the modern one is empty.
#if !TARGET_OS_SIMULATOR
    if ([info respondsToSelector:@selector(subscriberCellularProvider)]) {
      carrier = info.subscriberCellularProvider;
    }
#endif
  }

  if (providers.count > 0) {
    result[@"simCount"] = @(providers.count);
  }

  if (carrier != nil) {
    [self putString:result key:@"networkOperatorName" value:carrier.carrierName];
    [self putString:result key:@"carrierMobileCountryCode" value:carrier.mobileCountryCode];
    [self putString:result key:@"carrierMobileNetworkCode" value:carrier.mobileNetworkCode];
    [self putString:result key:@"networkCountryIso" value:carrier.isoCountryCode];
    result[@"carrierAllowsVoip"] = @(carrier.allowsVOIP);
  }

  // imei intentionally omitted — never exposed on iOS. The optional TS field keeps the absence explicit.
  return result;
}

- (void)putString:(NSMutableDictionary *)dict key:(NSString *)key value:(NSString *_Nullable)value
{
  // CoreTelephony returns "--" / "65535" sentinels when it has nothing real; drop those and nils.
  if (value.length > 0 && ![value isEqualToString:@"--"] && ![value isEqualToString:@"65535"]) {
    dict[key] = value;
  }
}

@end
