#import "SecurityPostureProvider.h"
#import <AVFoundation/AVFoundation.h>
#import <LocalAuthentication/LocalAuthentication.h>
#import <UIKit/UIKit.h>

@implementation SecurityPostureProvider

- (NSDictionary *)deviceSecurityPosture
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];
  LAContext *context = [LAContext new];
  NSError *ownerError = nil;
  result[@"hasSecureLockScreen"] = @([context canEvaluatePolicy:LAPolicyDeviceOwnerAuthentication error:&ownerError]);
  NSError *biometryError = nil;
  BOOL biometry = [context canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics error:&biometryError];
  result[@"biometryAvailable"] = @(biometry);
  NSString *biometryType = @"none";
  if (@available(iOS 11.0, *)) {
    switch (context.biometryType) {
      case LABiometryTypeFaceID: biometryType = @"faceId"; break;
      case LABiometryTypeTouchID: biometryType = @"touchId"; break;
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 170000
      case LABiometryTypeOpticID: biometryType = @"opticId"; break;
#endif
      default: break;
    }
  }
  result[@"biometryType"] = biometryType;
  void (^work)(void) = ^{
    result[@"protectedDataAvailable"] = @([UIApplication sharedApplication].protectedDataAvailable);
  };
  if ([NSThread isMainThread]) work(); else dispatch_sync(dispatch_get_main_queue(), work);
  return result;
}

- (NSDictionary *)transactionSafetySignals
{
  // Android-only screenshot-event, screen-recording, and obscured-touch observation fields are
  // intentionally omitted here. iOS continues to expose its supported point-in-time UIKit state.
  NSMutableDictionary *result = [NSMutableDictionary dictionary];
  void (^work)(void) = ^{
    UIApplication *app = [UIApplication sharedApplication];
    result[@"isInteractive"] = @(app.applicationState == UIApplicationStateActive);
    result[@"isScreenCaptured"] = @(UIScreen.mainScreen.isCaptured);
    BOOL isMirrored = NO;
    for (UIScreen *screen in UIScreen.screens) {
      if (screen.mirroredScreen != nil) {
        isMirrored = YES;
        break;
      }
    }
    result[@"isScreenMirrored"] = @(isMirrored);
    BOOL voiceOver = UIAccessibilityIsVoiceOverRunning();
    BOOL switchControl = UIAccessibilityIsSwitchControlRunning();
    BOOL guidedAccess = UIAccessibilityIsGuidedAccessEnabled();
    BOOL assistiveTouch = UIAccessibilityIsAssistiveTouchRunning();
    NSInteger count = (voiceOver ? 1 : 0) + (switchControl ? 1 : 0) +
      (guidedAccess ? 1 : 0) + (assistiveTouch ? 1 : 0);
    result[@"accessibilityRunning"] = @(count > 0);
    result[@"accessibilityFeatureCount"] = @(count);
  };
  if ([NSThread isMainThread]) work(); else dispatch_sync(dispatch_get_main_queue(), work);
  return result;
}

@end
