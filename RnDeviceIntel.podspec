require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "RnDeviceIntel"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => ".git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.private_header_files = "ios/**/*.h"

  # Apple privacy manifest — declares NSPrivacyTracking=false and ZERO Required-Reason APIs. Bundled
  # so App Store tooling picks it up. See ios/PrivacyInfo.xcprivacy.
  s.resource_bundles = { "ReactNativeDeviceIntelPrivacy" => ["ios/PrivacyInfo.xcprivacy"] }

  # CoreTelephony: CTTelephonyNetworkInfo carrier reads (TelephonyInfoProvider) — opportunistic and
  # largely nulled by Apple on iOS 16+, linked so the fields exist where still populated.
  # CFNetwork: CFNetworkCopySystemProxySettings proxy read (NetworkInfoProvider).
  # SystemConfiguration: SCNetworkReachability transport classification (NetworkInfoProvider).
  # CoreLocation: opportunistic last-known fix (GeolocationInfoProvider).
  # AVFoundation: AVAudioSession audio-route + latency reads (MediaBluetoothAppsProvider, AudioLatencyProvider).
  # Metal: GPU benchmark/fingerprint (GpuBenchmarkProvider). UIKit/QuartzCore are implicit.
  s.frameworks = "CoreTelephony", "CFNetwork", "SystemConfiguration", "CoreLocation", "AVFoundation", "Metal"

  # New-arch dependency wiring (RN >= 0.76) with a fallback for compatible CocoaPods helpers.
  if respond_to?(:install_modules_dependencies, true)
    install_modules_dependencies(s)
  else
    s.dependency "React-Core"
  end
end
