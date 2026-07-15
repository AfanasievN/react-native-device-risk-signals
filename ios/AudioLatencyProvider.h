#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * audio_latency (iOS) — cheap AVAudioSession property reads (no engine, no permission, no session
 * activation). Reports output/input latency, IO buffer duration, and sample rate. Never runs until
 * config enables the probe (ships disabled — see audioLatencyProbe.ts).
 */
@interface AudioLatencyProvider : NSObject

- (NSDictionary *)audioLatency;

@end

NS_ASSUME_NONNULL_END
