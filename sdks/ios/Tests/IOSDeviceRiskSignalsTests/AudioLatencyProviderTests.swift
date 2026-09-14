import AVFoundation
import XCTest

@testable import IOSDeviceRiskSignals

/// Locks the emitted key vocabulary, omission rules, millisecond conversions and — the property this
/// provider exists to preserve — the fact that reading audio latency never *activates* an audio
/// session.
///
/// `audio_latency` ships **disabled by default** (`src/probes/audioLatencyProbe.ts`), so no
/// production run exercises it unless an integrator opts in. That makes these tests the only routine
/// guard on its behaviour, and it makes the no-activation property worth asserting mechanically
/// rather than trusting to review: activating `AVAudioSession` is a process-wide side effect that
/// interrupts other audio, can duck or stop the user's music, and — the moment a category with
/// recording is involved — is what would turn a permission-free probe into one that prompts. The
/// header's promise is "no engine, no permission, no session activation"; only the third clause is
/// invisible in the emitted dictionary, so it is spied on instead.
///
/// Everything here is host-dependent in *magnitude* — a simulator, a device with headphones and a
/// device on the built-in speaker report different latencies — so the assertions pin relationships,
/// units and boxing rather than literal numbers, with one exception noted per test.
final class AudioLatencyProviderTests: XCTestCase {
    /// Every key `-audioLatency` is allowed to emit. Four are conditional on a positive reading;
    /// `measured` is unconditional.
    private static let allowedKeys: Set<String> = [
        "outputLatencyMs",
        "inputLatencyMs",
        "ioBufferDurationMs",
        "nativeSampleRate",
        "measured",
    ]

    /// The keys whose presence is gated on `value > 0`.
    private static let conditionalKeys: Set<String> = [
        "outputLatencyMs",
        "inputLatencyMs",
        "ioBufferDurationMs",
        "nativeSampleRate",
    ]

    private var signals: [String: Any] {
        AudioLatencyProvider().audioLatency() as? [String: Any] ?? [:]
    }

    override func tearDown() {
        AudioSessionSpy.uninstall()
        super.tearDown()
    }

    // MARK: - Key vocabulary

    func testEmitsNothingOutsideTheContractedKeyVocabulary() {
        XCTAssertTrue(
            Set(signals.keys).isSubset(of: Self.allowedKeys),
            "unexpected keys: \(Set(signals.keys).subtracting(Self.allowedKeys).sorted())"
        )
    }

    func testMeasuredIsTheOnlyUnconditionalKey() {
        // The four readings are each written only when positive, so a host that reports nothing
        // still produces a one-key dictionary rather than an empty one. That matters to the JS
        // side: `measured` is how a consumer distinguishes "read, and the platform had nothing"
        // from "probe never ran".
        XCTAssertNotNil(signals["measured"], "measured is written on every path")
    }

    // MARK: - The omission rule

    func testNoEmittedReadingIsZeroOrNegative() throws {
        // `if (x > 0)` for all four. A zero output latency is a real simulator value and must be
        // omitted rather than emitted as `0`, which a backend would otherwise read as "measured an
        // impossibly fast route" instead of "no reading".
        let signals = self.signals
        for key in Self.conditionalKeys.sorted() {
            guard let value = signals[key] else { continue }
            let number = try XCTUnwrap(value as? NSNumber, "\(key) must be an NSNumber")
            XCTAssertGreaterThan(number.doubleValue, 0, "\(key) is omitted rather than emitted <= 0")
            XCTAssertTrue(number.doubleValue.isFinite, "\(key) must be finite")
        }
    }

    // MARK: - The documented `measured` rule

    func testMeasuredIsDerivedFromOutputLatencyOrSampleRateAndNothingElse() throws {
        // `BOOL measured = outputMs > 0 || sampleRate > 0;` — exactly two of the four readings feed
        // it. Input latency and IO buffer duration deliberately do not: a simulator commonly reports
        // a plausible `ioBufferDurationMs` with no route behind it, so counting it would make
        // `measured` true on a host that measured nothing. Pinning the rule as an equivalence is
        // what catches a future "simplification" to `result.count > 1`.
        let signals = self.signals
        let measured = try XCTUnwrap(signals["measured"] as? NSNumber).boolValue
        let expected = signals["outputLatencyMs"] != nil || signals["nativeSampleRate"] != nil
        XCTAssertEqual(
            measured,
            expected,
            "measured must be (outputLatencyMs present || nativeSampleRate present); got "
                + "\(measured) with keys \(signals.keys.sorted())"
        )
    }

    func testInputLatencyAndBufferDurationAloneDoNotMakeItMeasured() throws {
        // The interesting half of the rule above, stated separately so a failure says which
        // direction broke. Silent on a host that reports an output latency or a sample rate.
        let signals = self.signals
        guard signals["outputLatencyMs"] == nil, signals["nativeSampleRate"] == nil else { return }
        XCTAssertFalse(
            try XCTUnwrap(signals["measured"] as? NSNumber).boolValue,
            "neither feeder key is present, so measured must be false even though "
                + "\(signals.keys.sorted()) came out"
        )
    }

    // MARK: - Units

    func testLatenciesAreSecondsConvertedToMillisecondsAndTheSampleRateIsNot() throws {
        // The three `* 1000.0` conversions and the one deliberate non-conversion, checked against a
        // live read of the same properties. `AVAudioSession` reports latencies in *seconds*; the
        // contract names the fields `...Ms`. A dropped or doubled factor is invisible in a
        // magnitude-free test, and `nativeSampleRate` sitting next to three converted fields is
        // exactly the kind of value a later edit multiplies "for consistency".
        //
        // Compared with a tolerance rather than for equality: these are host properties read a few
        // microseconds apart, and a route change between the two reads would otherwise be a flake.
        // The tolerance is far tighter than a factor-of-1000 error.
        let session = AVAudioSession.sharedInstance()
        let signals = self.signals

        let pairs: [(String, Double)] = [
            ("outputLatencyMs", session.outputLatency * 1000.0),
            ("inputLatencyMs", session.inputLatency * 1000.0),
            ("ioBufferDurationMs", session.ioBufferDuration * 1000.0),
        ]
        for (key, expected) in pairs {
            guard let value = signals[key] else { continue }
            let actual = try XCTUnwrap(value as? NSNumber).doubleValue
            XCTAssertEqual(actual, expected, accuracy: max(expected * 0.05, 1e-6), "\(key)")
        }

        if let value = signals["nativeSampleRate"] {
            let actual = try XCTUnwrap(value as? NSNumber).doubleValue
            XCTAssertEqual(actual, session.sampleRate, accuracy: 1.0, "nativeSampleRate is in Hz")
            XCTAssertGreaterThan(actual, 1000.0, "a sample rate in Hz, not a rate divided by 1000")
            XCTAssertLessThan(actual, 1_000_000.0, "a sample rate in Hz, not one multiplied by 1000")
        }
    }

    func testEmittedLatenciesAreMillisecondScaledNotSecondScaled() {
        // A units sanity check that holds without reading the session again: audio latencies and
        // buffer durations on any Apple hardware are fractions of a second, so the millisecond value
        // is >= 0.01. A forgotten `* 1000.0` would produce a value in the 1e-3 range and fail here
        // even if the comparison test above were deleted.
        let signals = self.signals
        for key in ["outputLatencyMs", "inputLatencyMs", "ioBufferDurationMs"] {
            guard let number = signals[key] as? NSNumber else { continue }
            XCTAssertGreaterThanOrEqual(
                number.doubleValue, 0.01,
                "\(key) looks like seconds, not milliseconds: \(number.doubleValue)"
            )
            XCTAssertLessThan(
                number.doubleValue, 10_000.0,
                "\(key) looks like microseconds or worse: \(number.doubleValue)"
            )
        }
    }

    // MARK: - Boxing

    func testMeasuredBoxesAsACFBoolean() throws {
        // The recent boolean-boxing fix on this provider. `measured` was `@(outputMs > 0 || sampleRate > 0)`,
        // a C `||` expression whose static type is `int`, so it boxed as `__NSCFNumber` "i" and
        // reached JavaScript as `1`/`0` where `contract/raw-signal-event.schema.json` declares a
        // boolean. The fix is the `BOOL measured = ...;` local, and CFBoolean identity is the only
        // check that separates the two boxes — `boolValue` and `isEqual:` agree on both. See
        // `BooleanBoxingTests.testCFBooleanIdentityIsWhatDistinguishesTheTwoBoxes`.
        let number = try XCTUnwrap(signals["measured"] as? NSNumber, "measured must be an NSNumber")
        XCTAssertTrue(
            number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
            "measured boxed as objCType \"\(String(cString: number.objCType))\" "
                + "(\(NSStringFromClass(type(of: number)))). It must be a CFBoolean, or it crosses "
                + "the bridge as 1/0 and fails schema validation."
        )
        XCTAssertEqual(String(cString: number.objCType), "c")
    }

    func testNoReadingIsBoxedAsACFBoolean() throws {
        // The inverse guard: an over-broad "cast everything to BOOL" would collapse a 48000 Hz
        // sample rate into `true`.
        let signals = self.signals
        for key in Self.conditionalKeys.sorted() {
            guard let value = signals[key] else { continue }
            let number = try XCTUnwrap(value as? NSNumber)
            XCTAssertFalse(
                number === (kCFBooleanTrue as NSNumber) || number === (kCFBooleanFalse as NSNumber),
                "\(key) is a number in the contract and must not box as a CFBoolean"
            )
        }
    }

    // MARK: - The invisible promise: no session activation

    func testReadingLatencyNeverActivatesOrReconfiguresTheAudioSession() {
        // The header says "no engine, no permission, no session activation", and nothing in the
        // emitted dictionary can show whether that held. So the four mutating entry points on
        // `AVAudioSession` are replaced for the duration of the call and the replacements count
        // their invocations *without* forwarding — a probe that regressed into activating a session
        // is blocked here as well as caught, so this test cannot itself duck the user's audio.
        //
        // Why it matters beyond tidiness: `-setActive:` is process-wide. It interrupts other audio,
        // fires interruption notifications in the host app, and — paired with a record category —
        // is the step that turns a permission-free probe into one that prompts for the microphone.
        // A probe whose whole premise is "cheap property reads" must not do any of that, and this
        // one is disabled by default precisely so it stays cheap.
        AudioSessionSpy.install()
        _ = AudioLatencyProvider().audioLatency()
        let calls = AudioSessionSpy.calls
        AudioSessionSpy.uninstall()

        XCTAssertEqual(
            calls, [:],
            "-audioLatency must only read properties; it called \(calls.keys.sorted())"
        )
    }

    func testTheActivationSpyActuallyObservesAnActivation() {
        // Guards the mechanism above rather than the provider. `AudioSessionSpy` swizzles by
        // selector name; a selector Apple renamed, or a failed `method_setImplementation`, would
        // make the previous test pass for the wrong reason — it would observe zero calls because it
        // observes nothing at all. Here an activation is triggered deliberately and must be seen.
        //
        // The spy does not forward to the original implementation, so no session is really
        // activated by this test either.
        //
        // Measured, not assumed: Swift's `setActive(_:)` does **not** bridge to `setActive:error:`.
        // It bridges to `setActive:withOptions:error:` with empty options — this assertion was
        // written against `setActive:error:` first and failed with
        // `observed = ["setActive:withOptions:error:": 1]`. That is precisely why the spy watches
        // all four mutating selectors rather than the one that looks obvious, and why
        // `testTheSpyInterceptsEverySelectorItClaimsTo` checks that every one of them was really
        // replaced: the no-activation test's value depends on the *set* being complete, not on any
        // single member of it.
        AudioSessionSpy.install()
        _ = try? AVAudioSession.sharedInstance().setActive(true)
        let calls = AudioSessionSpy.calls
        AudioSessionSpy.uninstall()

        XCTAssertEqual(
            calls["setActive:withOptions:error:"], 1,
            "the spy is not intercepting session activation, so the no-activation test above proves "
                + "nothing; observed \(calls)"
        )
    }

    func testTheSpyInterceptsEverySelectorItClaimsTo() {
        // A missing selector would silently narrow the no-activation test's coverage.
        AudioSessionSpy.install()
        let installed = AudioSessionSpy.installedSelectors
        AudioSessionSpy.uninstall()
        XCTAssertEqual(
            installed, Set(AudioSessionSpy.watchedSelectors),
            "not intercepted: \(Set(AudioSessionSpy.watchedSelectors).subtracting(installed).sorted())"
        )
    }

    // MARK: - Stability

    func testRepeatedCallsAgreeOnWhichKeysArePresent() {
        // The latencies themselves legitimately move (a route change between calls is real), but
        // which keys exist and whether the host counts as measured must not flicker within a single
        // collection run.
        let first = AudioLatencyProvider().audioLatency() as? [String: Any] ?? [:]
        let second = AudioLatencyProvider().audioLatency() as? [String: Any] ?? [:]
        XCTAssertEqual(Set(first.keys), Set(second.keys))
        XCTAssertEqual(
            (first["measured"] as? NSNumber)?.boolValue,
            (second["measured"] as? NSNumber)?.boolValue
        )
    }
}

/// Replaces `AVAudioSession`'s mutating entry points with counting stubs.
///
/// Implemented with `method_setImplementation` and `imp_implementationWithBlock` rather than a
/// subclass, because `-[AVAudioSession sharedInstance]` is a singleton the provider reaches directly
/// — there is no seam to inject through, and adding one would have changed the extracted source.
/// The replacements deliberately do **not** call through: the point is to observe a call that must
/// never happen, and forwarding would perform the very side effect under test.
private enum AudioSessionSpy {
    /// Every `AVAudioSession` selector that activates or reconfigures the session. Reads
    /// (`outputLatency`, `inputLatency`, `IOBufferDuration`, `sampleRate`) are not here — those are
    /// what the provider is allowed to do.
    static let watchedSelectors = [
        "setActive:error:",
        "setActive:withOptions:error:",
        "setCategory:error:",
        "setCategory:mode:options:error:",
    ]

    private(set) static var calls: [String: Int] = [:]
    private(set) static var installedSelectors: Set<String> = []
    private static var originals: [(Method, IMP)] = []

    static func install() {
        uninstall()
        calls = [:]
        for name in watchedSelectors {
            let selector = NSSelectorFromString(name)
            guard let method = class_getInstanceMethod(AVAudioSession.self, selector) else { continue }
            // One variadic-free block shape works for all four: the block is invoked with `self`
            // first and the method's own arguments after, and every one of these returns `BOOL`.
            // The arguments are ignored, so the extra parameters of the longer selectors simply go
            // unread — the ABI passes them in registers the block never touches.
            let replacement: @convention(block) (AnyObject) -> ObjCBool = { _ in
                calls[name, default: 0] += 1
                return true
            }
            let imp = imp_implementationWithBlock(replacement)
            originals.append((method, method_setImplementation(method, imp)))
            installedSelectors.insert(name)
        }
    }

    static func uninstall() {
        for (method, imp) in originals.reversed() {
            method_setImplementation(method, imp)
        }
        originals = []
        installedSelectors = []
    }
}
