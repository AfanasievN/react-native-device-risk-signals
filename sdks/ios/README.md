# ios-device-risk-signals

Planned standalone Swift Package for native iOS applications and cross-platform bindings. Signal
providers will expose Foundation-compatible raw models and will not import React Native. Mac
Catalyst can remain a supported deployment target of the same package.

The current Objective-C++ implementation remains under `ios/` while it is extracted incrementally.
The planned repository/package name is `ios-device-risk-signals`; the Swift product imported by
applications will be `IOSDeviceRiskSignals`. It is not published yet.
