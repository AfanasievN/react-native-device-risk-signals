import XCTest
import UIKit
import IOSDeviceRiskSignals

final class NativeFacadeTests: XCTestCase {
    func testBatteryMonitoringRestoredForBothInitialStates() {
        let device = UIDevice.current
        // Simulator may ignore enabling monitoring. Control the property to test both branches.
        var state = false
        let getter = class_getInstanceMethod(UIDevice.self, #selector(getter: UIDevice.isBatteryMonitoringEnabled))!
        let setter = class_getInstanceMethod(UIDevice.self, #selector(setter: UIDevice.isBatteryMonitoringEnabled))!
        let getBlock: @convention(block) (AnyObject) -> Bool = { _ in state }
        let setBlock: @convention(block) (AnyObject, Bool) -> Void = { _, value in state = value }
        let getIMP = imp_implementationWithBlock(getBlock)
        let setIMP = imp_implementationWithBlock(setBlock)
        let oldGet = method_setImplementation(getter, getIMP)
        let oldSet = method_setImplementation(setter, setIMP)
        defer {
            method_setImplementation(getter, oldGet)
            method_setImplementation(setter, oldSet)
            imp_removeBlock(getIMP)
            imp_removeBlock(setIMP)
        }
        for enabled in [false, true] {
            device.isBatteryMonitoringEnabled = enabled
            XCTAssertEqual(device.isBatteryMonitoringEnabled, enabled, "spy control")
            _ = DeviceRiskSignals().collectHardware()
            XCTAssertEqual(device.isBatteryMonitoringEnabled, enabled)
        }
    }

    func testSensitiveOmissionsAndBooleanTypes() {
        let sdk = DeviceRiskSignals()
        let location = sdk.collectGeolocation()
        if (location["hasCoarsePermission"] as? Bool) != true {
            for key in ["latitude", "longitude", "accuracyMeters", "locationAgeMs"] {
                XCTAssertNil(location[key], key)
            }
        }
        let samples: [([AnyHashable: Any], [String])] = [
            (location, ["hasCoarsePermission", "locationServicesEnabled"]),
            (sdk.collectHardware(), ["lowPowerModeEnabled"]),
            (sdk.collectOsIntegrity(), ["isEmulator", "injectedLibrariesFound", "suspiciousFilePathsFound"]),
            (sdk.collectMediaBluetoothApps(), ["isScreenCaptured", "isScreenMirrored", "accessibilityRunning"]),
            (sdk.collectDeviceSecurityPosture(), ["hasSecureLockScreen", "protectedDataAvailable"]),
            (sdk.collectTransactionSafety(), ["isInteractive", "isScreenCaptured", "isScreenMirrored"])
        ]
        for (raw, keys) in samples {
            for key in keys {
                guard let value = raw[key] as? NSNumber else { XCTFail("Missing boolean: \(key)"); continue }
                XCTAssertEqual(CFGetTypeID(value), CFBooleanGetTypeID(), key)
            }
        }
    }

    func testFacadeExposesMissingCollectionsAndPreservesOmissions() {
        let sdk = DeviceRiskSignals()
        let previous = UIDevice.current.isBatteryMonitoringEnabled
        let hardware = sdk.collectHardware()
        XCTAssertNotNil(hardware["processorCount"])
        XCTAssertNil(hardware["storageTotalBytes"])
        XCTAssertEqual(UIDevice.current.isBatteryMonitoringEnabled, previous)
        let fonts = sdk.collectFonts()
        XCTAssertEqual((fonts["fontsDigest"] as? String)?.count, 64)
        let integrity = sdk.collectOsIntegrity()
        XCTAssertNotNil(integrity["isEmulator"])
        XCTAssertNil(integrity["openReverseEngineeringPorts"])
        XCTAssertNil(integrity["forkSucceeded"])
        let location = sdk.collectGeolocation()
        XCTAssertNotNil(location["authorizationStatus"])
        XCTAssertNil(location["gnssSupported"])
        XCTAssertNotNil(sdk.collectMediaBluetoothApps()["accessibilityFeatures"])
        XCTAssertNotNil(sdk.collectDeviceSecurityPosture()["protectedDataAvailable"])
        let transaction = sdk.collectTransactionSafety()
        XCTAssertNotNil(transaction["isScreenCaptured"])
        XCTAssertNil(transaction["screenshotCount"])
        XCTAssertNotNil(sdk.collectDeviceIdentity()["model"])
        XCTAssertNotNil(sdk.collectApplication()["bundleId"])
        XCTAssertFalse(sdk.collectLocale().isEmpty)
        XCTAssertFalse(sdk.collectRuntimeTiming().isEmpty)
        XCTAssertFalse(sdk.collectNumericConsistency().isEmpty)
        _ = sdk.collectNetwork()
        _ = sdk.collectTelephony()
        _ = sdk.collectAudioLatency()
    }

    func testMainThreadCollectionsRejectWorkerInsteadOfDispatching() {
        let done = expectation(description: "worker preconditions")
        DispatchQueue.global().async {
            let sdk = DeviceRiskSignals()
            let calls: [() -> Void] = [
                { _ = sdk.collectHardware() }, { _ = sdk.collectOsIntegrity() },
                { _ = sdk.collectGeolocation() }, { _ = sdk.collectMediaBluetoothApps() },
                { _ = sdk.collectDeviceSecurityPosture() }, { _ = sdk.collectTransactionSafety() }
            ]
            for call in calls {
                XCTAssertNotNil(RNDIExecutionPolicyFailure(call))
            }
            done.fulfill()
        }
        wait(for: [done], timeout: 5)
    }

    func testGpuRetainsWorkerRequirement() {
        XCTAssertNotNil(RNDIExecutionPolicyFailure { _ = DeviceRiskSignals().collectGpuBenchmark() })
    }
}
