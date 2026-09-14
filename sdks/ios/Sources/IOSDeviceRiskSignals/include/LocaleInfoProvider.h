#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * locale — trivial, permission-free, high-entropy. NOTE: `keyboardLanguages` is deliberately NOT
 * collected on iOS. Reading enabled keyboards via `-[UITextInputMode activeInputModes]` is a
 * Required-Reason API (Active Keyboard, DDA9.1) whose only sanctioned reason is a custom-keyboard
 * extension. Android provides that signal instead. Do not add active-keyboard reads here without an
 * explicit policy decision.
 */
@interface LocaleInfoProvider : NSObject

- (NSDictionary *)localeSignals;

@end

NS_ASSUME_NONNULL_END
