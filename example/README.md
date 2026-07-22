# Signal Bench

Signal Bench is the runnable example for `react-native-device-risk-signals`. It uses a restrained
light interface to show exactly what the library observes on the current Android or iOS device.

The app calls `collect()` without configuring a server endpoint. Results stay in the application
and are rendered as formatted, selectable JSON.

## Requirements

Complete the official [React Native environment setup](https://reactnative.dev/docs/set-up-your-environment)
for the platform you want to run. The example requires Node.js 22.11 or newer.

## Install

From this directory:

```sh
npm install
```

For iOS, also install the Ruby and CocoaPods dependencies:

```sh
bundle install
cd ios && bundle exec pod install && cd ..
```

Repeat the CocoaPods step after changing native dependencies.

## Run

Start Metro in one terminal:

```sh
npm start
```

Use a second terminal to launch the app on a running emulator, simulator, or connected device:

```sh
npm run android
```

or:

```sh
npm run ios
```

Press **Collect device signals**. The summary separates observed, skipped, and failed probes, while
the result panel exposes the complete event payload.

The demo does not request optional runtime permissions and does not declare the optional Android
capture-detection permissions. Signals that require unavailable hardware, permissions, platform
support, or an enabled probe can legitimately be omitted, skipped, or failed. Rebuild the native
application after changing the library's Android or iOS implementation.

## Optional Android transaction observations

`transaction_safety` ships disabled. Obscured-touch flags need no permission, but Android 14
screenshot events and Android 15 screen-recording visibility require the host application to add
the corresponding install-time permission to `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.DETECT_SCREEN_CAPTURE" />
<uses-permission android:name="android.permission.DETECT_SCREEN_RECORDING" />
```

The library does not merge either permission and never displays a runtime permission prompt. Android
shows its standard notice when a screenshot is detected. Declare only the permission required by the
protected flow, then enable `transaction_safety` in that flow's collection configuration.

Android UI observation begins on the first enabled collection. Collect once when the protected UI
opens and again immediately before its action; the second event contains observations accumulated
inside that window. Missing capture fields mean the API, host permission, callback, or observation
was unavailable and must not be interpreted as `false`.

## Verify

```sh
npm test
npm run lint
npx tsc --noEmit
```
