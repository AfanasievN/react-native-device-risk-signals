import Darwin
import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key vocabulary, value types, omission rules and derivation rules of
/// `NetworkInfoProvider`.
///
/// **Why this suite is stricter than the others in the package.** `network` is one of the few probes
/// that is *enabled by default*, and its payload is the most sensitive this package emits: local IP
/// addresses, the host's interface-name topology, whether a VPN tunnel is up, and the configured
/// HTTP proxy host and port. Every one of those reaches JavaScript, and from there whatever backend
/// the integrating application chose. So the dropping rules are not cosmetic — each one is a value
/// the previous implementation decided *not* to disclose, and a refactor that quietly stopped
/// dropping it would widen what the SDK discloses without anyone editing a privacy document. The
/// tests below therefore pin the omissions as hard as they pin the emissions.
///
/// The four things the provider deliberately drops:
///
/// 1. **Loopback interfaces** (`IFF_LOOPBACK`), so `lo0`, `127.0.0.1` and `::1` never appear.
/// 2. **Down interfaces** (no `IFF_UP`), so a configured-but-inactive interface is not disclosed.
/// 3. **IPv6 link-local addresses** (the literal `fe80` prefix), which carry an interface-derived
///    host portion and are of no use to a backend.
/// 4. **Non-IP address families** (`AF_LINK` entries, which is where the MAC address would be).
///
/// And the two it never collects at all: `wifiSsid` and `wifiBssid`. `CNCopyCurrentNetworkInfo`
/// needs an entitlement the host app may not carry, and the shared TypeScript contract marks both
/// expected-null on iOS. Their absence is pinned by the vocabulary test.
///
/// **What a host can and cannot exercise.** A test machine has interfaces and addresses, so the
/// inventory rules are genuinely exercised. It almost certainly has no VPN and no HTTP proxy, so
/// `isVpnActive` and `isProxyConfigured` are `false` and the conditional proxy keys never appear.
/// Rather than let those rules go untested, the two private helpers that implement them are called
/// directly through the Objective-C runtime with synthesised proxy dictionaries — the same technique
/// `TelephonyInfoProviderTests` uses for the sentinel filter, and for the same reason: widening the
/// header so a test could see them would have changed the extracted source.
final class NetworkInfoProviderTests: XCTestCase {
    /// Every key `-networkSignals` is allowed to emit. `NetworkSignals` in
    /// `src/NativeDeviceIntel.ts` is a shared cross-platform type and declares more; the rest are
    /// populated by the Kotlin side or are expected-null on iOS, and must stay absent here.
    private static let allowedKeys: Set<String> = [
        "interfaceNames",
        "localIpAddresses",
        "isVpnActive",
        "isProxyConfigured",
        "proxyHost",
        "proxyPort",
        "isConnected",
        "connectionType",
    ]

    /// The keys written on every path, regardless of what the host's network looks like.
    private static let unconditionalKeys: Set<String> = [
        "interfaceNames",
        "localIpAddresses",
        "isVpnActive",
        "isProxyConfigured",
        "isConnected",
        "connectionType",
    ]

    /// The three booleans. All are written unconditionally.
    private static let booleanKeys: Set<String> = ["isVpnActive", "isProxyConfigured", "isConnected"]

    /// The complete value set of `connectionType`: three from the reachability classifier plus the
    /// VPN override.
    private static let connectionTypes: Set<String> = ["none", "wifi", "cellular", "vpn"]

    private var signals: [String: Any] {
        NetworkInfoProvider().networkSignals() as? [String: Any] ?? [:]
    }

    // MARK: - Key vocabulary

    func testEmitsNothingOutsideTheContractedKeyVocabulary() {
        XCTAssertTrue(
            Set(signals.keys).isSubset(of: Self.allowedKeys),
            "unexpected keys: \(Set(signals.keys).subtracting(Self.allowedKeys).sorted())"
        )
    }

    func testEveryUnconditionalKeyIsPresent() {
        let keys = Set(signals.keys)
        XCTAssertTrue(
            Self.unconditionalKeys.isSubset(of: keys),
            "missing: \(Self.unconditionalKeys.subtracting(keys).sorted())"
        )
    }

    func testWifiIdentifiersAreNeverEmitted() {
        // A documented decision in `NetworkInfoProvider.h`, not an oversight.
        // `CNCopyCurrentNetworkInfo` requires the `com.apple.developer.networking.wifi-info`
        // entitlement, which the host application may not carry, so the provider does not call it at
        // all and the TS contract marks both fields expected-null on iOS. An SSID is a location
        // proxy and a shared-network identifier; adding one here would be a privacy-surface change
        // needing its own review, and this test is what forces that conversation to happen.
        let signals = self.signals
        for key in ["wifiSsid", "wifiBssid"] {
            XCTAssertNil(signals[key], "\(key) needs a Wi-Fi entitlement and is deliberately not read")
        }
    }

    func testAndroidOnlyContractFieldsAreNeverEmittedFromIOS() {
        let signals = self.signals
        for key in ["isMetered", "linkDownstreamKbps", "linkUpstreamKbps", "dnsServers"] {
            XCTAssertNil(signals[key], "\(key) is populated by the Kotlin side and must stay absent on iOS")
        }
    }

    // MARK: - Interface inventory

    func testInterfaceNamesAreUniqueNonEmptyStrings() throws {
        // The `![names containsObject:name]` de-duplication is load-bearing: `getifaddrs` returns one
        // entry per address *per interface*, so a dual-stack `en0` appears at least twice and the raw
        // list would repeat it. Addresses are deliberately *not* de-duplicated — see below.
        let names = try XCTUnwrap(signals["interfaceNames"] as? [String], "interfaceNames must be [String]")
        XCTAssertEqual(Set(names).count, names.count, "interface names are de-duplicated: \(names)")
        for name in names {
            XCTAssertFalse(name.isEmpty, "an empty name is dropped rather than emitted")
        }
    }

    func testLoopbackIsDroppedFromBothNamesAndAddresses() throws {
        // `(ptr->ifa_flags & IFF_LOOPBACK) != 0` skips the whole entry, so neither the name nor its
        // addresses survive. Every host has a loopback interface, so unlike most rules here this one
        // is genuinely exercised on every run — if the flag check were dropped, `lo0` and
        // `127.0.0.1` would appear immediately.
        let names = try XCTUnwrap(signals["interfaceNames"] as? [String])
        let addresses = try XCTUnwrap(signals["localIpAddresses"] as? [String])

        XCTAssertFalse(names.contains("lo0"), "the loopback interface is skipped: \(names)")
        XCTAssertFalse(addresses.contains("127.0.0.1"), "loopback addresses are skipped: \(addresses)")
        XCTAssertFalse(addresses.contains("::1"), "loopback addresses are skipped: \(addresses)")
    }

    func testIPv6LinkLocalAddressesAreDropped() throws {
        // `![addr hasPrefix:@"fe80"]`, compared against the lowercase text `inet_ntop` produces. A
        // link-local address embeds an interface-derived host portion and is useless to a backend,
        // so it is dropped rather than disclosed. Link-local addresses are present on essentially
        // every interface, so this rule, too, is exercised for real on every run.
        let addresses = try XCTUnwrap(signals["localIpAddresses"] as? [String])
        for address in addresses {
            XCTAssertFalse(
                address.hasPrefix("fe80"),
                "\(address) is an IPv6 link-local address and must be dropped"
            )
        }
    }

    func testIPv4LinkLocalAddressesAreNotDroppedBecauseTheFilterIsIPv6Only() throws {
        // The inverse of the rule above, stated so it cannot be "generalised". The filter is the
        // literal `fe80` prefix and nothing else: a 169.254.0.0/16 IPv4 auto-configuration address
        // is emitted, and so is any address merely *containing* `fe80`. This is what fails if the
        // check is ever widened into a regular expression or a "drop private ranges" rule, which
        // would silently change the emitted set.
        let addresses = try XCTUnwrap(signals["localIpAddresses"] as? [String])
        for address in addresses where address.contains("fe80") {
            XCTAssertTrue(
                address.hasPrefix("fe80"),
                "\(address) contains but does not start with fe80, and the filter is a prefix test"
            )
        }
    }

    func testEveryLocalAddressParsesAsAnIPv4OrIPv6Address() throws {
        // `localIpAddresses` is the most sensitive array this package emits, so it must contain
        // addresses and only addresses. `inet_pton` is an independent code path from the provider's
        // `inet_ntop`, so a swapped family constant, a truncated buffer, or an interface *name*
        // accidentally appended to the address array all fail here.
        let addresses = try XCTUnwrap(signals["localIpAddresses"] as? [String], "localIpAddresses must be [String]")
        for address in addresses {
            XCTAssertFalse(address.isEmpty, "an empty address is dropped rather than emitted")
            var v4 = in_addr()
            var v6 = in6_addr()
            let isV4 = inet_pton(AF_INET, address, &v4) == 1
            let isV6 = inet_pton(AF_INET6, address, &v6) == 1
            XCTAssertTrue(isV4 || isV6, "\(address) is not a parseable IP address")
        }
    }

    func testNoHardwareAddressLeaksIntoTheAddressList() throws {
        // `AF_LINK` entries are where `getifaddrs` exposes the MAC address, and the provider's
        // `family == AF_INET || family == AF_INET6` guard is what keeps them out. A MAC address is
        // a persistent hardware identifier, the one category this package promises never to emit —
        // and it would sail past a "non-empty string" check. The colon test is a second, cruder net
        // behind the parse test above, because an IPv6 address also contains colons but never six
        // two-hex-digit groups.
        let addresses = try XCTUnwrap(signals["localIpAddresses"] as? [String])
        let mac = try NSRegularExpression(pattern: "^([0-9a-fA-F]{1,2}:){5}[0-9a-fA-F]{1,2}$")
        for address in addresses {
            let range = NSRange(address.startIndex..., in: address)
            XCTAssertNil(
                mac.firstMatch(in: address, range: range),
                "\(address) looks like a hardware address; AF_LINK entries must be skipped"
            )
        }
    }

    func testBothInventoriesAreArraysEvenWhenGetifaddrsFails() throws {
        // `result[@"interfaceNames"]` and `result[@"localIpAddresses"]` are assigned outside the
        // `getifaddrs(&interfaces) == 0` branch, so a failed enumeration yields empty arrays rather
        // than absent keys. The JS side indexes into both unconditionally.
        let signals = self.signals
        XCTAssertTrue(signals["interfaceNames"] is [String], "always an array, never absent or null")
        XCTAssertTrue(signals["localIpAddresses"] is [String], "always an array, never absent or null")
    }

    // MARK: - Connection type and the isConnected derivation

    func testConnectionTypeIsOneOfTheFourContractedValues() throws {
        let type = try XCTUnwrap(signals["connectionType"] as? String, "connectionType must be a String")
        XCTAssertTrue(Self.connectionTypes.contains(type), "unexpected connectionType \(type)")
    }

    func testConnectionTypeIsVpnExactlyWhenIsVpnActiveIsTrue() throws {
        // `result[@"connectionType"] = vpn ? @"vpn" : type;` — the override is unconditional, so the
        // two fields cannot disagree in either direction. A VPN tunnel takes priority over the
        // underlying transport, matching the Android capabilities-based classifier.
        let signals = self.signals
        let type = try XCTUnwrap(signals["connectionType"] as? String)
        let vpn = try XCTUnwrap(signals["isVpnActive"] as? NSNumber).boolValue
        XCTAssertEqual(type == "vpn", vpn, "connectionType \(type) disagrees with isVpnActive \(vpn)")
    }

    func testIsConnectedIsDerivedFromTheReachabilityTypeAndNotFromTheReportedOne() throws {
        // The subtle part of this provider, and the reason this is not simply
        // `isConnected == (connectionType != "none")`.
        //
        //     NSString *type = [self reachabilityConnectionType];
        //     BOOL connected = ![type isEqualToString:@"none"];
        //     result[@"connectionType"] = vpn ? @"vpn" : type;
        //
        // `connected` reads the *reachability* answer; `connectionType` may have been overwritten
        // with "vpn" afterwards. So a host with a VPN interface configured but no route out reports
        // `connectionType: "vpn"` with `isConnected: false` — a real, meaningful combination that a
        // "tidying" refactor deriving one field from the other would destroy.
        //
        // What always holds, in both directions:
        let signals = self.signals
        let type = try XCTUnwrap(signals["connectionType"] as? String)
        let vpn = try XCTUnwrap(signals["isVpnActive"] as? NSNumber).boolValue
        let connected = try XCTUnwrap(signals["isConnected"] as? NSNumber).boolValue

        if !vpn {
            // With no VPN override, the reported type *is* the reachability type.
            XCTAssertEqual(connected, type != "none", "isConnected must mirror connectionType \(type)")
        }
        if type == "wifi" || type == "cellular" {
            XCTAssertTrue(connected, "a classified transport means reachability said reachable")
        }
        if connected {
            XCTAssertNotEqual(type, "none", "isConnected true can never be reported as type none")
        }
    }

    func testTheReachabilityClassifierReturnsOnlyItsThreeDocumentedValues() throws {
        // Reaches `-reachabilityConnectionType` directly, so the classifier is pinned separately
        // from the VPN override that can mask it. "wifi" here means non-cellular reachable —
        // `SCNetworkReachability` cannot separate Wi-Fi from wired, but the transient WWAN flag
        // reliably separates cellular, which a name-based check cannot.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        let type = provider.reachabilityConnectionType()
        XCTAssertTrue(["none", "wifi", "cellular"].contains(type), "unexpected classifier value \(type)")
        XCTAssertNotEqual(type, "vpn", "the classifier never returns vpn; only the override does")
    }

    // MARK: - VPN detection

    func testTheVpnHelperIsReachableUnderTheSelectorThisSuiteCallsIt() {
        // Guards the runtime mechanism: `unsafeBitCast` to an `@objc` protocol does not check
        // conformance, so a renamed helper would surface as a crash in an unrelated test rather than
        // as a clear failure here.
        let provider = NetworkInfoProvider()
        for name in ["vpnActiveInProxySettings:", "addProxyInfoFrom:to:", "reachabilityConnectionType"] {
            XCTAssertTrue(
                provider.responds(to: NSSelectorFromString(name)),
                "-\(name) was renamed — update NetworkInternals to match"
            )
        }
    }

    func testVpnIsDetectedFromScopedProxyKeysByTunnelInterfacePrefix() {
        // The detection rule, exercised against synthesised settings because a test host has no VPN.
        // Scoped system-proxy entries are keyed by interface name, which is a more reliable tell
        // than matching raw interface names — `utun0`/`utun1` exist for system services with no user
        // VPN, and an earlier version of this provider got exactly that guess wrong.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)

        for key in ["tap0", "tun0", "ppp0", "ipsec0", "utun3", "TUN0", "UTun5", "IPSec1"] {
            XCTAssertTrue(
                provider.vpnActiveInProxySettings(["__SCOPED__": [key: [:]]]),
                "\(key) is a tunnel interface (the comparison is on the lowercased key)"
            )
        }
    }

    func testOrdinaryInterfacesInScopedProxySettingsAreNotAVpn() {
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        for key in ["en0", "en1", "pdp_ip0", "awdl0", "bridge100", "lo0"] {
            XCTAssertFalse(
                provider.vpnActiveInProxySettings(["__SCOPED__": [key: [:]]]),
                "\(key) is not a tunnel interface"
            )
        }
    }

    func testTheVpnMatchIsAPrefixTestAndNotASubstringTest() {
        // `hasPrefix:`, not `containsString:`. An interface merely containing "tun" — or a
        // hypothetical vendor interface named "xtun0" — is not a VPN, and generalising the check
        // would turn `isVpnActive` true on hosts that have none. `isVpnActive` also drives
        // `connectionType`, so a false positive there rewrites the reported transport too.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        for key in ["xtun0", "mytap", "notppp", "vipsec", "en0utun"] {
            XCTAssertFalse(
                provider.vpnActiveInProxySettings(["__SCOPED__": [key: [:]]]),
                "\(key) contains a tunnel name but does not start with one"
            )
        }
    }

    func testVpnDetectionIsFalseWhenScopedSettingsAreMissingOrNotADictionary() {
        // The three defensive paths: no proxy settings at all (`CFNetworkCopySystemProxySettings`
        // returned NULL), settings with no `__SCOPED__` entry, and a `__SCOPED__` entry of the wrong
        // type. All must yield `false` rather than crashing or guessing — this dictionary comes from
        // the system and the provider treats its shape as untrusted.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        XCTAssertFalse(provider.vpnActiveInProxySettings(nil), "nil settings are not a VPN")
        XCTAssertFalse(provider.vpnActiveInProxySettings([:]), "no __SCOPED__ key is not a VPN")
        XCTAssertFalse(
            provider.vpnActiveInProxySettings(["__SCOPED__": "utun0"]),
            "a non-dictionary __SCOPED__ is rejected by the isKindOfClass: guard"
        )
        XCTAssertFalse(
            provider.vpnActiveInProxySettings(["__SCOPED__": ["en0": [:]], "HTTPEnable": 1]),
            "unrelated top-level keys are never inspected"
        )
    }

    func testOneTunnelKeyAmongManyIsEnough() {
        // The loop returns on the first match, so ordering must not matter.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        XCTAssertTrue(provider.vpnActiveInProxySettings(["__SCOPED__": ["en0": [:], "utun4": [:]]]))
        XCTAssertTrue(provider.vpnActiveInProxySettings(["__SCOPED__": ["utun4": [:], "en0": [:]]]))
    }

    // MARK: - Proxy detection

    func testProxyIsReportedOnlyWhenBothEnabledAndHostArePresent() {
        // `configured = (httpEnabled.boolValue && host.length > 0)`, and the host and port are
        // written *only* when configured. That conjunction is the privacy-relevant part: a proxy
        // host is an infrastructure detail of the user's network, and a disabled-but-remembered
        // proxy entry must not disclose it.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)

        let configured = NSMutableDictionary()
        provider.addProxyInfo(from: ["HTTPEnable": 1, "HTTPProxy": "proxy.example", "HTTPPort": 8080], to: configured)
        XCTAssertEqual((configured["isProxyConfigured"] as? NSNumber)?.boolValue, true)
        XCTAssertEqual(configured["proxyHost"] as? String, "proxy.example")
        XCTAssertEqual((configured["proxyPort"] as? NSNumber)?.intValue, 8080)
        XCTAssertEqual(configured.count, 3)

        let disabled = NSMutableDictionary()
        provider.addProxyInfo(from: ["HTTPEnable": 0, "HTTPProxy": "proxy.example", "HTTPPort": 8080], to: disabled)
        XCTAssertEqual((disabled["isProxyConfigured"] as? NSNumber)?.boolValue, false)
        XCTAssertNil(disabled["proxyHost"], "a disabled proxy must not disclose its host")
        XCTAssertNil(disabled["proxyPort"], "a disabled proxy must not disclose its port")
        XCTAssertEqual(disabled.count, 1)

        let hostless = NSMutableDictionary()
        provider.addProxyInfo(from: ["HTTPEnable": 1], to: hostless)
        XCTAssertEqual((hostless["isProxyConfigured"] as? NSNumber)?.boolValue, false)
        XCTAssertEqual(hostless.count, 1)

        let emptyHost = NSMutableDictionary()
        provider.addProxyInfo(from: ["HTTPEnable": 1, "HTTPProxy": ""], to: emptyHost)
        XCTAssertEqual((emptyHost["isProxyConfigured"] as? NSNumber)?.boolValue, false)
        XCTAssertNil(emptyHost["proxyHost"], "an empty host is not a configuration")

        let enableMissing = NSMutableDictionary()
        provider.addProxyInfo(from: ["HTTPProxy": "proxy.example"], to: enableMissing)
        XCTAssertEqual(
            (enableMissing["isProxyConfigured"] as? NSNumber)?.boolValue, false,
            "an absent HTTPEnable is nil, and nil.boolValue is NO"
        )
        XCTAssertNil(enableMissing["proxyHost"])
    }

    func testProxyPortIsOmittedWhenTheSystemSuppliesNoneEvenThoughAProxyIsConfigured() {
        // `if (port != nil)` nested inside the configured branch: a proxy with no explicit port
        // yields host-without-port rather than a fabricated 80. The JS contract declares
        // `proxyPort?: number`, so absent is a representable answer and a default would be a value
        // the system never gave.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        let result = NSMutableDictionary()
        provider.addProxyInfo(from: ["HTTPEnable": 1, "HTTPProxy": "proxy.example"], to: result)
        XCTAssertEqual((result["isProxyConfigured"] as? NSNumber)?.boolValue, true)
        XCTAssertEqual(result["proxyHost"] as? String, "proxy.example")
        XCTAssertNil(result["proxyPort"], "no port is emitted when the system supplied none")
        XCTAssertEqual(result.count, 2)
    }

    func testNilProxySettingsShortCircuitToNotConfigured() {
        // `CFNetworkCopySystemProxySettings()` can return NULL. The early return writes
        // `isProxyConfigured = NO` and nothing else, so the key is never absent.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        let result = NSMutableDictionary()
        provider.addProxyInfo(from: nil, to: result)
        XCTAssertEqual((result["isProxyConfigured"] as? NSNumber)?.boolValue, false)
        XCTAssertEqual(result.count, 1, "nothing but the flag is written: \(result.allKeys)")
    }

    func testProxyKeysAppearOnlyTogetherWithAConfiguredProxyInTheRealDictionary() throws {
        // The dictionary-level counterpart of the helper tests: whatever the host's real proxy
        // settings are, the conditional keys can only appear alongside a true flag. Silent on an
        // unproxied host and load-bearing on a proxied one.
        let signals = self.signals
        let configured = try XCTUnwrap(signals["isProxyConfigured"] as? NSNumber).boolValue
        if !configured {
            XCTAssertNil(signals["proxyHost"], "an unconfigured proxy discloses no host")
            XCTAssertNil(signals["proxyPort"], "an unconfigured proxy discloses no port")
        } else {
            let host = try XCTUnwrap(signals["proxyHost"] as? String, "a configured proxy always has a host")
            XCTAssertFalse(host.isEmpty)
        }
        if signals["proxyPort"] != nil {
            XCTAssertNotNil(signals["proxyHost"], "a port never appears without a host")
        }
    }

    // MARK: - Value types and boxing

    func testEveryBooleanBoxesAsACFBoolean() throws {
        // All three were part of the boolean-boxing fix's blast radius, and `isConnected` was the
        // one actually broken: it was `@(![type isEqualToString:@"none"])`, and C's `!` yields
        // `int`, so it boxed as `__NSCFNumber` "i" and reached JavaScript as `1`/`0` where
        // `contract/raw-signal-event.schema.json` declares a boolean. The fix is the
        // `BOOL connected = ...;` local. CFBoolean identity is the only check that separates the two
        // boxes — `boolValue` and `isEqual:` agree on both.
        let signals = self.signals
        for key in Self.booleanKeys.sorted() {
            let number = try XCTUnwrap(signals[key] as? NSNumber, "\(key) must be an NSNumber")
            XCTAssertTrue(
                number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
                "\(key) boxed as objCType \"\(String(cString: number.objCType))\" "
                    + "(\(NSStringFromClass(type(of: number)))). It must be a CFBoolean, or it "
                    + "crosses the bridge as 1/0 and fails schema validation."
            )
            XCTAssertEqual(String(cString: number.objCType), "c", key)
        }
    }

    func testProxyPortIsANumberAndNotABoolean() throws {
        // The inverse guard. `proxyPort` is forwarded as the `NSNumber` the system supplied, so an
        // over-broad "cast everything to BOOL" fix would collapse port 8080 into `true`.
        let provider = unsafeBitCast(NetworkInfoProvider(), to: NetworkInternals.self)
        let result = NSMutableDictionary()
        provider.addProxyInfo(from: ["HTTPEnable": 1, "HTTPProxy": "proxy.example", "HTTPPort": 3128], to: result)
        let port = try XCTUnwrap(result["proxyPort"] as? NSNumber)
        XCTAssertFalse(
            port === (kCFBooleanTrue as NSNumber) || port === (kCFBooleanFalse as NSNumber),
            "proxyPort is a number in the contract and must not box as a CFBoolean"
        )
        XCTAssertEqual(port.intValue, 3128)
    }

    func testEveryEmittedValueHasItsContractedType() throws {
        let signals = self.signals
        XCTAssertTrue(signals["interfaceNames"] is [String])
        XCTAssertTrue(signals["localIpAddresses"] is [String])
        XCTAssertTrue(signals["connectionType"] is String)
        for key in Self.booleanKeys.sorted() {
            XCTAssertTrue(signals[key] is NSNumber, "\(key) must be an NSNumber")
        }
        if let host = signals["proxyHost"] { XCTAssertTrue(host is String) }
        if let port = signals["proxyPort"] { XCTAssertTrue(port is NSNumber) }
    }

    // MARK: - Stability

    func testRepeatedCallsAgreeOnTheStructuralFields() {
        // Addresses and reachability can legitimately change between two calls on a moving device,
        // but the key vocabulary and the flag/type agreement must not flicker within a collection
        // run. Asserting the shape rather than the values keeps this honest on real hardware.
        let first = NetworkInfoProvider().networkSignals() as? [String: Any] ?? [:]
        let second = NetworkInfoProvider().networkSignals() as? [String: Any] ?? [:]
        XCTAssertEqual(Set(first.keys), Set(second.keys))
        XCTAssertEqual(
            (first["isVpnActive"] as? NSNumber)?.boolValue,
            (second["isVpnActive"] as? NSNumber)?.boolValue
        )
        XCTAssertEqual(
            (first["isProxyConfigured"] as? NSNumber)?.boolValue,
            (second["isProxyConfigured"] as? NSNumber)?.boolValue
        )
    }
}

/// `NetworkInfoProvider`'s implementation-only helpers, reached through the Objective-C runtime.
///
/// None of these is declared in `NetworkInfoProvider.h`, so Swift cannot see them even with
/// `@testable import` — `@testable` widens Swift's own access control and does nothing for an
/// Objective-C method that no header declares. Declaring the selectors in an `@objc` protocol and
/// casting to it is the standard way to reach them: dispatch is by selector at run time, so these
/// call the real implementations in the real `.m` file rather than a copy of their logic. The
/// alternative — publishing them from the header purely so a test could see them — would have
/// widened the package's public surface to suit the test, and the extraction moved the provider
/// byte-for-byte unchanged.
///
/// This is what makes the VPN and proxy rules testable at all: a test host has neither, so the only
/// way to exercise the branches that decide what gets disclosed is to feed the helpers synthesised
/// system settings.
@objc private protocol NetworkInternals {
    @objc(vpnActiveInProxySettings:)
    func vpnActiveInProxySettings(_ proxies: NSDictionary?) -> Bool

    @objc(addProxyInfoFrom:to:)
    func addProxyInfo(from proxies: NSDictionary?, to result: NSMutableDictionary)

    @objc(reachabilityConnectionType)
    func reachabilityConnectionType() -> String
}
