#import "AudioLatencyProvider.h"
#import <AVFoundation/AVFoundation.h>

@implementation AudioLatencyProvider

- (NSDictionary *)audioLatency
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];
  AVAudioSession *session = [AVAudioSession sharedInstance];

  double outputMs = session.outputLatency * 1000.0;
  double inputMs = session.inputLatency * 1000.0;
  double bufferMs = session.IOBufferDuration * 1000.0;
  double sampleRate = session.sampleRate;

  if (outputMs > 0) {
    result[@"outputLatencyMs"] = @(outputMs);
  }
  if (inputMs > 0) {
    result[@"inputLatencyMs"] = @(inputMs);
  }
  if (bufferMs > 0) {
    result[@"ioBufferDurationMs"] = @(bufferMs);
  }
  if (sampleRate > 0) {
    result[@"nativeSampleRate"] = @(sampleRate);
  }

  result[@"measured"] = @(outputMs > 0 || sampleRate > 0);
  return result;
}

@end
