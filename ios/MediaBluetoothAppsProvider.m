#import "MediaBluetoothAppsProvider.h"
#import <AVFoundation/AVFoundation.h>
#import <UIKit/UIKit.h>

// VPN / anonymity URL schemes for the app-audit. MUST mirror IOS_APP_AUDIT_SCHEMES (src/knownAppsSchemes.ts)
// and every entry MUST be declared in the host Info.plist LSApplicationQueriesSchemes — all of these
// already are. knownAppsSchemes.spec.ts asserts .m ⇔ TS and TS ⊆ Info.plist.
static NSString *const kAppAuditSchemes[] = {
  @"vpn-master", @"tor-browser", @"vpn-express-free-mobile-vpn", @"free-vpn-by-free-vpn-org",
  @"vyprvpn", @"com.simplexsolutionsinc.vpnguard", @"vpnunlimited", @"cyberghost",
  @"expressvpn", @"nordvpn", @"onionbrowser", @"openvpn"
};

@implementation MediaBluetoothAppsProvider

- (NSDictionary *)mediaBluetoothAppsSignals
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];

  // Audio route — AVAudioSession is safe to read off the main thread.
  AVAudioSession *session = [AVAudioSession sharedInstance];
  NSString *route = [self audioRouteFor:session];
  if (route != nil) {
    result[@"audioOutputRoute"] = route;
  }
  result[@"isOtherAudioPlaying"] = @(session.isOtherAudioPlaying);

  // UIKit / UIApplication / UIAccessibility reads must happen on the main thread. TurboModule methods
  // run off it, so hop over (guarded against the already-on-main case to avoid a dispatch_sync deadlock).
  void (^work)(void) = ^{
    result[@"isScreenCaptured"] = @(UIScreen.mainScreen.isCaptured);
    result[@"isScreenMirrored"] = @(UIScreen.screens.count > 1);

    NSMutableArray<NSString *> *features = [NSMutableArray array];
    BOOL voiceOver = UIAccessibilityIsVoiceOverRunning();
    BOOL switchControl = UIAccessibilityIsSwitchControlRunning();
    BOOL guidedAccess = UIAccessibilityIsGuidedAccessEnabled();
    BOOL assistiveTouch = UIAccessibilityIsAssistiveTouchRunning();
    if (voiceOver) [features addObject:@"voiceOver"];
    if (switchControl) [features addObject:@"switchControl"];
    if (guidedAccess) [features addObject:@"guidedAccess"];
    if (assistiveTouch) [features addObject:@"assistiveTouch"];
    if (UIAccessibilityIsSpeakScreenEnabled()) [features addObject:@"speakScreen"];
    result[@"accessibilityFeatures"] = features;
    result[@"accessibilityRunning"] = @(voiceOver || switchControl || guidedAccess || assistiveTouch);

    NSMutableArray<NSString *> *openable = [NSMutableArray array];
    UIApplication *app = [UIApplication sharedApplication];
    for (NSUInteger i = 0; i < sizeof(kAppAuditSchemes) / sizeof(kAppAuditSchemes[0]); i++) {
      NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@://", kAppAuditSchemes[i]]];
      if (url != nil && [app canOpenURL:url]) {
        [openable addObject:kAppAuditSchemes[i]];
      }
    }
    result[@"openableFlaggedSchemes"] = openable;
  };
  if ([NSThread isMainThread]) {
    work();
  } else {
    dispatch_sync(dispatch_get_main_queue(), work);
  }

  return result;
}

- (NSString *_Nullable)audioRouteFor:(AVAudioSession *)session
{
  AVAudioSessionPortDescription *output = session.currentRoute.outputs.firstObject;
  if (output == nil) {
    return nil;
  }
  NSString *portType = output.portType;
  if ([portType isEqualToString:AVAudioSessionPortBluetoothA2DP] ||
      [portType isEqualToString:AVAudioSessionPortBluetoothHFP] ||
      [portType isEqualToString:AVAudioSessionPortBluetoothLE]) {
    return @"bluetooth";
  }
  if ([portType isEqualToString:AVAudioSessionPortHeadphones]) {
    return @"headphones";
  }
  if ([portType isEqualToString:AVAudioSessionPortBuiltInSpeaker]) {
    return @"speaker";
  }
  if ([portType isEqualToString:AVAudioSessionPortBuiltInReceiver]) {
    return @"receiver";
  }
  if ([portType isEqualToString:AVAudioSessionPortCarAudio]) {
    return @"car";
  }
  if ([portType isEqualToString:AVAudioSessionPortUSBAudio]) {
    return @"usb";
  }
  return @"other";
}

@end
