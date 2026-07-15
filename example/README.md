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

The demo does not request optional runtime permissions. Signals that require unavailable hardware,
permissions, or platform support can legitimately appear as skipped or failed. Rebuild the native
application after changing the library's Android or iOS implementation.

## Verify

```sh
npm test
npm run lint
npx tsc --noEmit
```
