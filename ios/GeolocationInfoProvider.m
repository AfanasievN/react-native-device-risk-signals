#import "GeolocationInfoProvider.h"
#import <CoreLocation/CoreLocation.h>

@implementation GeolocationInfoProvider

- (NSDictionary *)geolocationSignals
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];

  // Core Location objects should be created/used on a thread with an active run loop. TurboModule
  // methods run off the main thread, so do the read there (guarded against the already-on-main case).
  void (^work)(void) = ^{
    [self collectInto:result];
  };
  if ([NSThread isMainThread]) {
    work();
  } else {
    dispatch_sync(dispatch_get_main_queue(), work);
  }
  return result;
}

- (void)collectInto:(NSMutableDictionary *)result
{
  CLLocationManager *manager = [[CLLocationManager alloc] init];

  CLAuthorizationStatus status;
  if (@available(iOS 14.0, *)) {
    status = manager.authorizationStatus;
  } else {
    status = [CLLocationManager authorizationStatus];
  }
  result[@"authorizationStatus"] = [self statusString:status];

  BOOL authorized = (status == kCLAuthorizationStatusAuthorizedAlways ||
                     status == kCLAuthorizationStatusAuthorizedWhenInUse);
  result[@"hasCoarsePermission"] = @(authorized);
  // gnssSupported intentionally OMITTED on iOS. The only cheap analogue,
  // +[CLLocationManager locationServicesEnabled], reports the user's system-wide Location Services
  // TOGGLE, not GNSS-hardware presence — reporting it here would make a genuine user who disabled
  // Location Services look like an emulator (false tell) and disagree with Android's
  // hasSystemFeature(FEATURE_LOCATION_GPS) semantics. Optional field ⇒ omission reads as "not observed".

  if (authorized) {
    // Cached last-known fix only — reading .location never prompts and never starts updates.
    CLLocation *location = manager.location;
    if (location != nil) {
      result[@"latitude"] = @(location.coordinate.latitude);
      result[@"longitude"] = @(location.coordinate.longitude);
      if (location.horizontalAccuracy >= 0) {
        result[@"accuracyMeters"] = @(location.horizontalAccuracy);
      }
      if (location.verticalAccuracy >= 0) {
        result[@"altitudeMeters"] = @(location.altitude);
      }
      NSTimeInterval age = -[location.timestamp timeIntervalSinceNow];
      if (age >= 0) {
        result[@"locationAgeMs"] = @((NSInteger)(age * 1000));
      }
    }
  }
}

- (NSString *)statusString:(CLAuthorizationStatus)status
{
  switch (status) {
    case kCLAuthorizationStatusNotDetermined:
      return @"notDetermined";
    case kCLAuthorizationStatusRestricted:
      return @"restricted";
    case kCLAuthorizationStatusDenied:
      return @"denied";
    case kCLAuthorizationStatusAuthorizedAlways:
      return @"authorizedAlways";
    case kCLAuthorizationStatusAuthorizedWhenInUse:
      return @"authorizedWhenInUse";
    default:
      return @"unknown";
  }
}

@end
