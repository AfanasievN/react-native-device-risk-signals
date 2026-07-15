#import "NetworkInfoProvider.h"
#import <CFNetwork/CFNetwork.h>
#import <SystemConfiguration/SystemConfiguration.h>
#import <arpa/inet.h>
#import <ifaddrs.h>
#import <net/if.h>
#import <netinet/in.h>

@implementation NetworkInfoProvider

- (NSDictionary *)networkSignals
{
  NSMutableDictionary *result = [NSMutableDictionary dictionary];

  // ── Interface inventory: RAW topology only. We do NOT infer the transport from interface names
  //    (en0 can be Wi-Fi, wired, or an unassociated-but-up radio; utun0/1 exist for system services
  //    with no user VPN) — that guess is what an earlier version got wrong. Transport is derived
  //    from SCNetworkReachability below instead.
  NSMutableArray<NSString *> *names = [NSMutableArray array];
  NSMutableArray<NSString *> *addresses = [NSMutableArray array];
  struct ifaddrs *interfaces = NULL;
  if (getifaddrs(&interfaces) == 0) {
    for (struct ifaddrs *ptr = interfaces; ptr != NULL; ptr = ptr->ifa_next) {
      if (ptr->ifa_addr == NULL) {
        continue;
      }
      if ((ptr->ifa_flags & IFF_UP) == 0 || (ptr->ifa_flags & IFF_LOOPBACK) != 0) {
        continue;
      }
      NSString *name = [NSString stringWithUTF8String:ptr->ifa_name];
      if (name.length > 0 && ![names containsObject:name]) {
        [names addObject:name];
      }

      sa_family_t family = ptr->ifa_addr->sa_family;
      if (family == AF_INET || family == AF_INET6) {
        char buffer[INET6_ADDRSTRLEN] = {0};
        void *addrPtr = NULL;
        if (family == AF_INET) {
          addrPtr = &((struct sockaddr_in *)ptr->ifa_addr)->sin_addr;
        } else {
          addrPtr = &((struct sockaddr_in6 *)ptr->ifa_addr)->sin6_addr;
        }
        if (inet_ntop(family, addrPtr, buffer, sizeof(buffer)) != NULL) {
          NSString *addr = [NSString stringWithUTF8String:buffer];
          if (addr.length > 0 && ![addr hasPrefix:@"fe80"]) {
            [addresses addObject:addr];
          }
        }
      }
    }
    freeifaddrs(interfaces);
  }
  result[@"interfaceNames"] = names;
  result[@"localIpAddresses"] = addresses;

  // ── Proxy + VPN from a single system-proxy-settings fetch.
  NSDictionary *proxies = nil;
  CFDictionaryRef proxyCF = CFNetworkCopySystemProxySettings();
  if (proxyCF != NULL) {
    proxies = (__bridge_transfer NSDictionary *)proxyCF;
  }
  BOOL vpn = [self vpnActiveInProxySettings:proxies];
  result[@"isVpnActive"] = @(vpn);
  [self addProxyInfoFrom:proxies to:result];

  // ── Transport classification via reachability (WWAN flag ⇒ cellular). A VPN tunnel takes priority
  //    as the reported connectionType, matching the Android capabilities-based classifier.
  NSString *type = [self reachabilityConnectionType];
  result[@"isConnected"] = @(![type isEqualToString:@"none"]);
  result[@"connectionType"] = vpn ? @"vpn" : type;

  return result;
}

// "wifi" here means non-cellular reachable (Wi-Fi or, rarely on iOS, wired) — SCNetworkReachability
// cannot separate those two, but it reliably separates cellular via the transient WWAN flag, which a
// name-based check cannot.
- (NSString *)reachabilityConnectionType
{
  struct sockaddr_in zeroAddress;
  bzero(&zeroAddress, sizeof(zeroAddress));
  zeroAddress.sin_len = sizeof(zeroAddress);
  zeroAddress.sin_family = AF_INET;

  SCNetworkReachabilityRef reachability =
      SCNetworkReachabilityCreateWithAddress(kCFAllocatorDefault, (const struct sockaddr *)&zeroAddress);
  if (reachability == NULL) {
    return @"none";
  }
  SCNetworkReachabilityFlags flags = 0;
  BOOL ok = SCNetworkReachabilityGetFlags(reachability, &flags);
  CFRelease(reachability);
  if (!ok) {
    return @"none";
  }

  BOOL reachable = (flags & kSCNetworkReachabilityFlagsReachable) != 0;
  BOOL needsConnection = (flags & kSCNetworkReachabilityFlagsConnectionRequired) != 0;
  if (!reachable || needsConnection) {
    return @"none";
  }
  if ((flags & kSCNetworkReachabilityFlagsIsWWAN) != 0) {
    return @"cellular";
  }
  return @"wifi";
}

// Scoped system-proxy entries are the standard VPN tell (tap/tun/ppp/ipsec/utun interface keys). More
// reliable than matching raw interface names, though iCloud Private Relay can also surface a utun
// entry — acceptable for a raw signal the backend fuses alongside interfaceNames.
- (BOOL)vpnActiveInProxySettings:(NSDictionary *)proxies
{
  NSDictionary *scoped = proxies[@"__SCOPED__"];
  if (![scoped isKindOfClass:[NSDictionary class]]) {
    return NO;
  }
  for (NSString *key in scoped.allKeys) {
    if (![key isKindOfClass:[NSString class]]) {
      continue;
    }
    NSString *lower = key.lowercaseString;
    if ([lower hasPrefix:@"tap"] || [lower hasPrefix:@"tun"] || [lower hasPrefix:@"ppp"] ||
        [lower hasPrefix:@"ipsec"] || [lower hasPrefix:@"utun"]) {
      return YES;
    }
  }
  return NO;
}

- (void)addProxyInfoFrom:(NSDictionary *)proxies to:(NSMutableDictionary *)result
{
  if (proxies == nil) {
    result[@"isProxyConfigured"] = @NO;
    return;
  }
  NSNumber *httpEnabled = proxies[(__bridge NSString *)kCFNetworkProxiesHTTPEnable];
  NSString *host = proxies[(__bridge NSString *)kCFNetworkProxiesHTTPProxy];
  NSNumber *port = proxies[(__bridge NSString *)kCFNetworkProxiesHTTPPort];

  BOOL configured = (httpEnabled.boolValue && host.length > 0);
  result[@"isProxyConfigured"] = @(configured);
  if (configured) {
    result[@"proxyHost"] = host;
    if (port != nil) {
      result[@"proxyPort"] = port;
    }
  }
}

@end
