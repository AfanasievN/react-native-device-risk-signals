#import "JailbreakDetector.h"
#import <UIKit/UIKit.h>
#import <mach-o/dyld.h>
#import <sys/stat.h>
#import <sys/sysctl.h>
#import <sys/wait.h>
#import <string.h>
#import <unistd.h>

// Jailbreak store / scheme handlers. MUST be mirrored in the TS canonical list
// (src/knownAppsSchemes.ts) and declared in the host Info.plist LSApplicationQueriesSchemes, or
// canOpenURL silently returns NO — see knownAppsSchemes.ts and the drift spec.
static NSString *const kJailbreakSchemes[] = {
  @"cydia", @"sileo", @"zbra", @"filza", @"undecimus", @"activator"
};

// File paths readable only on a jailbroken device.
static NSString *const kJailbreakPaths[] = {
  @"/Applications/Cydia.app",
  @"/Applications/Sileo.app",
  @"/Library/MobileSubstrate/MobileSubstrate.dylib",
  @"/Library/MobileSubstrate/DynamicLibraries/",
  @"/usr/sbin/sshd",
  @"/usr/bin/ssh",
  @"/etc/apt",
  @"/private/var/lib/apt/",
  @"/private/var/lib/cydia",
  @"/private/var/stash",
  @"/usr/libexec/cydia",
  @"/usr/lib/libjailbreak.dylib",
  @"/var/jb",
  @"/var/jb/usr/lib/libjailbreak.dylib",
};

// Shells that only exist post-jailbreak.
static NSString *const kJailbreakShells[] = { @"/bin/bash", @"/bin/sh", @"/usr/bin/bash" };

// Paths that become symlinks on many jailbreaks.
static NSString *const kSymlinkPaths[] = {
  @"/Applications", @"/var/stash", @"/Library/Ringtones", @"/Library/Wallpaper", @"/usr/libexec"
};

// dyld image path fragments that indicate an injected hook framework.
static const char *kInjectionSignatures[] = {
  "MobileSubstrate", "substrate", "SubstrateLoader", "TweakInject", "libsubstitute",
  "substitute", "libhooker", "frida", "cynject", "cycript", "RocketBootstrap"
};

@implementation JailbreakDetector

- (NSDictionary *)osIntegrity
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];

  // Baseline (required by the contract on both platforms).
  result[@"isEmulator"] = @([self isSimulator]);
  result[@"isDebuggerAttached"] = @([self isDebuggerAttached]);
  result[@"developerModeEnabled"] = @NO; // No public API on iOS; kept for cross-platform contract.

  // Files / binaries.
  NSArray<NSString *> *suspiciousPaths = [self foundSuspiciousPaths];
  result[@"suBinaryFound"] = @([self anyShellPresent]);
  result[@"suspiciousFilePathsFound"] = @(suspiciousPaths.count > 0);
  result[@"suspiciousFilePaths"] = suspiciousPaths;
  result[@"symbolicLinksSuspicious"] = @([self suspiciousSymlinksPresent]);

  // Sandbox write test (writing outside the container succeeds only when jailbroken).
  result[@"writableSystemPathFound"] = @([self canWriteOutsideSandbox]);

  // URL scheme handlers.
  BOOL scheme = [self canOpenJailbreakScheme];
  result[@"canOpenJailbreakScheme"] = @(scheme);
  result[@"rootManagementAppFound"] = @(scheme);

  // Injected dylibs.
  NSArray<NSString *> *injected = [self injectedImageNames];
  result[@"injectedLibrariesFound"] = @(injected.count > 0);
  result[@"injectedLibraryNames"] = injected;
  result[@"hookFrameworkFound"] = @(injected.count > 0);
  result[@"dyldImageCount"] = @((NSInteger)_dyld_image_count());

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

#pragma mark - Checks

- (BOOL)isSimulator
{
#if TARGET_OS_SIMULATOR
  return YES;
#else
  return NO;
#endif
}

- (BOOL)isDebuggerAttached
{
  int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()};
  struct kinfo_proc info;
  info.kp_proc.p_flag = 0;
  size_t size = sizeof(info);
  if (sysctl(mib, 4, &info, &size, NULL, 0) != 0) {
    return NO;
  }
  return (info.kp_proc.p_flag & P_TRACED) != 0;
}

- (NSArray<NSString *> *)foundSuspiciousPaths
{
  NSMutableArray<NSString *> *found = [NSMutableArray array];
  for (NSUInteger i = 0; i < sizeof(kJailbreakPaths) / sizeof(kJailbreakPaths[0]); i++) {
    NSString *path = kJailbreakPaths[i];
    if (access(path.fileSystemRepresentation, F_OK) == 0) {
      [found addObject:path];
    }
  }
  return found;
}

- (BOOL)anyShellPresent
{
  for (NSUInteger i = 0; i < sizeof(kJailbreakShells) / sizeof(kJailbreakShells[0]); i++) {
    if (access(kJailbreakShells[i].fileSystemRepresentation, F_OK) == 0) {
      return YES;
    }
  }
  return NO;
}

- (BOOL)suspiciousSymlinksPresent
{
  for (NSUInteger i = 0; i < sizeof(kSymlinkPaths) / sizeof(kSymlinkPaths[0]); i++) {
    struct stat s;
    if (lstat(kSymlinkPaths[i].fileSystemRepresentation, &s) == 0 && S_ISLNK(s.st_mode)) {
      return YES;
    }
  }
  return NO;
}

- (BOOL)canWriteOutsideSandbox
{
  NSString *path = @"/private/jailbreak_probe.txt";
  NSError *error = nil;
  BOOL wrote = [@"probe" writeToFile:path
                          atomically:YES
                            encoding:NSUTF8StringEncoding
                               error:&error];
  if (wrote) {
    // Clean up in every branch — never leave the probe file behind.
    [[NSFileManager defaultManager] removeItemAtPath:path error:nil];
    return YES;
  }
  return NO;
}

- (BOOL)canOpenJailbreakScheme
{
  // canOpenURL/sharedApplication must be touched on the main thread; TurboModule methods run off it.
  __block BOOL canOpen = NO;
  void (^work)(void) = ^{
    UIApplication *app = [UIApplication sharedApplication];
    for (NSUInteger i = 0; i < sizeof(kJailbreakSchemes) / sizeof(kJailbreakSchemes[0]); i++) {
      NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"%@://", kJailbreakSchemes[i]]];
      if (url && [app canOpenURL:url]) {
        canOpen = YES;
        break;
      }
    }
  };
  if ([NSThread isMainThread]) {
    work();
  } else {
    dispatch_sync(dispatch_get_main_queue(), work);
  }
  return canOpen;
}

- (NSArray<NSString *> *)injectedImageNames
{
  NSMutableArray<NSString *> *hits = [NSMutableArray array];
  uint32_t count = _dyld_image_count();
  size_t sigCount = sizeof(kInjectionSignatures) / sizeof(kInjectionSignatures[0]);
  for (uint32_t i = 0; i < count; i++) {
    const char *name = _dyld_get_image_name(i);
    if (name == NULL) {
      continue;
    }
    for (size_t j = 0; j < sigCount; j++) {
      if (strcasestr(name, kInjectionSignatures[j]) != NULL) {
        // stringWithUTF8String: returns nil for a non-UTF-8 path (an attacker on an already-compromised
        // device controls the injected dylib's filename bytes); skip rather than insert nil and crash.
        NSString *full = [NSString stringWithUTF8String:name];
        if (full != nil) {
          [hits addObject:(full.lastPathComponent ?: full)];
        }
        break;
      }
    }
  }
  return hits;
}

@end
