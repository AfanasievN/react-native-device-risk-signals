# Active probes native example

A one-button Android app that consumes `android-active-probes-device-risk-signals` directly, with no
React Native. The passive core's example stays socket-free by design, so the active probe is
demonstrated here instead.

```sh
example/android/gradlew -p sdks/android-active-probes :example:installDebug --no-daemon
```

Pressing the button runs `DeviceRiskActiveProbes.collectFridaScan()` on a worker thread and prints
the raw map. Collection never starts on its own, nothing is uploaded and no score is calculated.

This demo host declares `INTERNET` itself, with a comment saying why: an Android application that has
not declared it is outside the `inet` group and cannot open a socket at all, loopback included, so
the scan would report `defaultPortOpen: false` and `fridaHandshakeReject: false` even with a listener
running. The component itself declares no permission.

To see the positive branch on a device or emulator, put a listener on the port first and press the
button again:

```sh
adb shell "echo 'REJECT frida' | nc -L -p 27042 -s 127.0.0.1"
```

A `false` flag means the attempt did not complete or nothing answered; it is not proof of absence.
See [the component README](../README.md) for the contract and its known limitations.
