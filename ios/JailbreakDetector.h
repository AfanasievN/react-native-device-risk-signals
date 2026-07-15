#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Multi-method jailbreak / hook / debugger detection (the iOS `os_integrity` bundle). Every method
 * returns a RAW observation; there is NO on-device verdict (the risk backend fuses the signals).
 *
 * `osIntegrity` is the fast, safe bundle. `forkJailbreakSignal` is isolated because a fork()-based
 * check is the single genuinely risky native call in the SDK — it ships DISABLED (the JS probe is
 * `enabled: () => false`) until QA on real jailbroken and normal devices clears the crash/zombie
 * risk. See NativeDeviceIntel.ts contract notes.
 */
@interface JailbreakDetector : NSObject

- (NSDictionary *)osIntegrity;
- (NSDictionary *)forkJailbreakSignal;

@end

NS_ASSUME_NONNULL_END
