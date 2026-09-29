#import "JailbreakDetector.h"
#import "OsIntegrityProvider.h"
#import <arpa/inet.h>
#import <netinet/in.h>
#import <sys/socket.h>
#import <sys/wait.h>
#import <unistd.h>
#import <string.h>
@implementation JailbreakDetector
// Legacy RN composition only; excluded from Swift Package.
- (NSDictionary *)osIntegrity
{
  __block NSMutableDictionary *result;
  void (^work)(void) = ^{ result = [[[OsIntegrityProvider new] osIntegrity] mutableCopy]; };
  if ([NSThread isMainThread]) work(); else dispatch_sync(dispatch_get_main_queue(), work);
  result[@"openReverseEngineeringPorts"] = [self openReverseEngineeringPorts];
  return result;
}
- (NSDictionary *)forkJailbreakSignal
{
  BOOL forkSucceeded = NO;
#if !TARGET_OS_SIMULATOR
  // fork() is blocked by the app sandbox on a stock device (returns -1/EPERM). Success ⇒ escaped
  // sandbox ⇒ jailbroken. The child does ONLY async-signal-safe work (_exit) — never exit(), which
  // would double-flush the shared stdio buffers of this multithreaded process. The parent reaps the
  // child with waitpid() so no zombie is left behind.
  pid_t pid = fork();
  if (pid == 0) {
    _exit(0);
  } else if (pid > 0) {
    int status = 0;
    waitpid(pid, &status, 0);
    forkSucceeded = YES;
  }
#endif
  return @{ @"testPerformed" : @YES, @"forkSucceeded" : @(forkSucceeded) };
}

// Reverse-engineering tools bind well-known loopback ports; a successful connect = tool present.
- (NSArray<NSNumber *> *)openReverseEngineeringPorts
{
  const uint16_t ports[] = {27042 /*frida*/, 4444 /*Needle*/, 22 /*OpenSSH*/, 44 /*checkra1n*/};
  NSMutableArray<NSNumber *> *open = [NSMutableArray array];
  for (size_t i = 0; i < sizeof(ports) / sizeof(ports[0]); i++) {
    if ([self isLocalPortOpen:ports[i]]) {
      [open addObject:@(ports[i])];
    }
  }
  return open;
}

- (BOOL)isLocalPortOpen:(uint16_t)port
{
  int sock = socket(AF_INET, SOCK_STREAM, 0);
  if (sock < 0) {
    return NO;
  }
  struct sockaddr_in addr;
  memset(&addr, 0, sizeof(addr));
  addr.sin_family = AF_INET;
  addr.sin_port = htons(port);
  addr.sin_addr.s_addr = inet_addr("127.0.0.1");
  // Loopback refuses a closed port immediately (ECONNREFUSED), so a plain connect is bounded.
  BOOL isOpen = connect(sock, (const struct sockaddr *)&addr, sizeof(addr)) == 0;
  close(sock);
  return isOpen;
}

@end
