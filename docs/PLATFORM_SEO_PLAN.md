# Platform discovery and SEO copy plan

Last reviewed: 2026-09-08.

This is an editorial and rollout plan, not evidence of live pages or published packages.
The [ecosystem manifest](../device-risk-signals.json) is authoritative for component names,
distribution coordinates and lifecycle. Follow the [architecture](ECOSYSTEM_ARCHITECTURE.md)
and [migration checklist](MIGRATION_ROADMAP.md) when activating a component or changing URLs.

Help a developer answer three questions from search: does this support my platform, what raw
observations are available, and what can I install today? Search phrases below are hypotheses
for content planning; no keyword volume, difficulty or ranking research has been performed.

## Names, audiences and destinations

Routes are relative to the existing Pages base URL. They are proposed destinations; creating
this document does not create these routes or authorize a repository/domain rename.

| Public library name | Audience | Proposed route | Current availability and CTA |
| --- | --- | --- | --- |
| `react-native-device-risk-signals` | React Native Android/iOS teams | `/react-native/` | Active npm package: install and open the tested RN integration guide |
| `android-device-risk-signals` | Native Android Kotlin/Java teams | `/android/` | In development, unpublished: inspect source/native example and migration status |
| `ios-device-risk-signals` | Native iOS Swift/Objective-C teams | `/ios/` | Planned standalone SDK: read scope and migration roadmap |
| `web-device-risk-signals` | Browser application teams | `/web/` | Planned: read browser scope and capability roadmap |
| `flutter-device-risk-signals` | Flutter Android/iOS teams | `/flutter/` | Planned: read binding roadmap; future pub.dev name is `flutter_device_risk_signals` |
| `capacitor-device-risk-signals` | Capacitor Android/iOS/browser teams | `/capacitor/` | Planned: read platform-adapter roadmap |

Keep the public names visible in headings/body copy. Show actual registry coordinates in installation
instructions: Android's intended coordinate is `io.github.afanasievn:android-device-risk-signals`,
iOS's intended Swift Package product is `IOSDeviceRiskSignals`, and Dart uses the underscore name
above. These intended coordinates are not claims that registry artifacts already exist. Additional
platforms need their own manifest entry and architecture decision before entering navigation.

## Ready-to-use platform copy

These drafts intentionally express current status. Recheck it immediately before publishing.
After activation, replace planned wording with verified capabilities and a tested installation CTA;
do not imply that a binding supports every observation available on another platform.

### React Native

- Search intent hypotheses: React Native device signals; React Native root jailbreak observations;
  React Native device context backend; Expo development build device signals.
- Title: `React Native Device Risk Signals SDK | Android & iOS`
- Meta description: `Collect typed Android and iOS device observations with react-native-device-risk-signals. Explore raw fields, privacy requirements and backend integration.`
- H1: `Raw device signals for React Native`
- Intro: `react-native-device-risk-signals collects typed device and runtime observations on Android
  and iOS for your application's backend. Explore available integrity, hardware, network and
  transaction-context fields, their platform limits and optional permissions. Your application
  owns transport and decisions; the SDK does not return a risk score.`
- Primary CTA: `Install the React Native package`.
- Installation snippet, after checking the current integration guide:
  `npm install react-native-device-risk-signals`.
- Required nearby details: supported RN floor/New Architecture, Expo Go limitation and development
  build workflow, platform-specific field omissions, optional permissions and version compatibility.

### Native Android

- Search intent hypotheses: Android device signals SDK Kotlin; native Android raw integrity data;
  Android device context library without React Native.
- Title: `Android Device Risk Signals SDK | In Development`
- Meta description: `Explore android-device-risk-signals, a standalone Android SDK in development for raw device observations. Review its native example and release roadmap.`
- H1: `Raw device signals for native Android apps`
- Intro: `android-device-risk-signals is the standalone Android SDK being extracted from the React
  Native implementation. Its Kotlin models expose local device observations for native apps and
  framework adapters. Review the implemented collectors and native example in the repository;
  a Maven release is not available yet.`
- Primary CTA: `Explore the Android source and example`.
- Required nearby details: implemented API surface, local-build instructions labeled as development,
  supported Android versions, host-owned permissions and remaining publication gates.

### Native iOS

- Search intent hypotheses: iOS device signals SDK Swift; native iOS runtime observations;
  iOS raw jailbreak observations library.
- Title: `iOS Device Risk Signals SDK | Planned`
- Meta description: `Follow ios-device-risk-signals, the planned standalone iOS SDK for raw device observations. Review the extraction roadmap and intended Swift integration.`
- H1: `A standalone iOS device signals SDK is planned`
- Intro: `ios-device-risk-signals will provide the native iOS collection layer for Swift and
  Objective-C apps and framework bindings. The existing iOS collectors currently ship through
  the React Native package. Standalone packaging, the public native API and consumer verification
  remain on the migration roadmap.`
- Primary CTA: `Read the iOS migration roadmap`.
- Required nearby details: planned scope versus existing RN behavior, intended package product,
  privacy resource packaging, unavailable observations and supported destinations once verified.

### Web

- Search intent hypotheses: browser device context SDK; Web raw device signals JavaScript;
  browser runtime observations TypeScript.
- Title: `Web Device Risk Signals SDK | Planned`
- Meta description: `Explore the plan for web-device-risk-signals: browser device and runtime observations with explicit capability limits and a shared raw-signal contract.`
- H1: `Browser device signals with explicit capability limits`
- Intro: `web-device-risk-signals is a planned browser SDK using the shared raw-signal contract.
  Its capability catalog will distinguish browser observations from data available only to native
  Android and iOS apps. The browser API, privacy-conscious defaults and implementation are still
  to be defined; no Web npm package is available yet.`
- Primary CTA: `Read the Web SDK roadmap`.
- Required nearby details: missing browser APIs and omissions, SSR/import behavior, browser test
  matrix and privacy limits when implemented. Do not advertise browser root/jailbreak parity.

### Flutter

- Search intent hypotheses: Flutter device signals plugin; Flutter Android iOS raw device data;
  Flutter device context backend integration.
- Title: `Flutter Device Risk Signals Plugin | Planned`
- Meta description: `Follow flutter-device-risk-signals, a planned Flutter binding for native Android and iOS observations. Its intended pub.dev name is flutter_device_risk_signals.`
- H1: `Raw Android and iOS device signals for Flutter`
- Intro: `flutter-device-risk-signals is a planned thin Flutter binding over the Android and iOS
  SDKs. It will map native observations and unavailable values into Dart without duplicating
  collection logic. The intended pub.dev package name is flutter_device_risk_signals; the binding
  is not published yet.`
- Primary CTA: `Read the Flutter binding roadmap`.
- Required nearby details: Dart naming exception, Android/iOS-only initial scope, native SDK
  compatibility and lifecycle handling after implementation. Do not imply Flutter Web support.

### Capacitor

- Search intent hypotheses: Capacitor device signals plugin; Ionic raw device context;
  Capacitor Android iOS browser observations.
- Title: `Capacitor Device Risk Signals Plugin | Planned`
- Meta description: `Explore capacitor-device-risk-signals, a planned plugin using Android, iOS and Web SDKs with platform-specific raw observations and a shared contract.`
- H1: `Platform-aware device signals for Capacitor`
- Intro: `capacitor-device-risk-signals is a planned binding that selects the Android, iOS or Web
  SDK for the host platform. Applications will receive the observations supported by that platform,
  with unsupported fields omitted. Native and browser capabilities differ; the plugin and its
  installation guide will become available after the adapters are implemented and verified.`
- Primary CTA: `Read the Capacitor binding roadmap`.
- Required nearby details: platform selection, native SDK/Web SDK compatibility, lifecycle cleanup
  and explicit capability differences after implementation.

## Page structure and internal discovery

Each platform destination should provide unique, useful content: status and audience, installation
or development state, one verified platform-specific example, capability/permission links, backend
contract link, compatibility and troubleshooting. Do not publish six near-identical keyword pages.
Keep planned components as clearly labeled sections on the ecosystem overview until their standalone
pages have enough substantive material; a roadmap link is sufficient in the meantime.

The ecosystem homepage should link to each available destination with the platform and library name.
Platform pages should link to the shared signal catalog, event schema, privacy documentation and
backend guides; shared pages should offer a platform selector linking back to installation guidance.
Preserve existing `/integration/` and other RN routes while introducing `/react-native/`. Avoid
copying catalog tables, backend examples or entire RN installation guides across destinations.

## Search engines and AI agents

- Serve meaningful headings, status, copy and links in static HTML without requiring JavaScript.
- Give each substantive page a unique title, description, H1, canonical URL and social metadata.
  Use the deployed Pages base until an approved URL migration occurs; never canonicalize to a
  proposed domain or redirect all platform pages to the homepage.
- Include only canonical, public, useful routes in the sitemap. Keep robots instructions consistent
  with intended crawl access. Neither sitemap inclusion nor crawler access guarantees indexing.
- Synchronize `llms.txt`, longer AI documentation and copyable integration prompts with the manifest.
  Include status, actual registry names, supported platforms, source links and last-reviewed date.
  These are discovery aids, not a guarantee that AI services will read, index or recommend the SDK.
- Keep schema/catalog URLs stable and machine-readable. Link them from all integration instructions;
  preserve version and omission semantics so generated backend code does not invent required fields.
- Google states that its AI search features do not require special AI text files or additional
  markup. Treat `llms.txt` as optional developer documentation rather than a Google indexing
  requirement. [Google Search AI guidance](https://developers.google.com/search/docs/appearance/ai-features)
- Structured data must describe actual page/software status. Do not attach download links, fabricated
  ratings or release claims to unpublished components. Check rendered JSON-LD against visible copy.
- Add platform-specific links to relevant README/package metadata when releases are available.
  Directory listings must link to an installable package and its correct platform documentation.

## Measurement and rollout gates

1. Finish the current implementation slice and synchronize manifest, roadmap and visible statuses.
2. Publish the ecosystem overview and useful platform pages with the copy above adapted to verified
   APIs. Run Pages checks for links, metadata, schema mirrors, status drift and responsive usability.
3. After deployment, verify HTTP responses, canonical URLs, sitemap entries and install links on the
   actual site. Preserve historical routes and follow the architecture's rename checklist.
4. If the maintainer has Search Console access, submit the sitemap and inspect representative URLs.
   Record a baseline and review platform-query impressions, clicks, CTR and landing pages after
   four to six weeks. Low visibility needs investigation; it is not proof of a technical defect.
5. Use registry download trends as a separate adoption indicator. They cannot establish which search
   visit led to installation. Optional website CTA measurement is a separate maintainer decision;
   it must not add telemetry or network behavior to any SDK.
6. Activate an installation CTA only after registry publication and a clean consumer installation
   test. Update title/meta/body, version/compatibility, AI instructions and package metadata together.

Success means a visitor can identify the correct package, understand availability and follow a
working integration path. Search position is measured, not promised.

Editorial guidance checked against [Google's SEO starter guide](https://developers.google.com/search/docs/fundamentals/seo-starter-guide):
use descriptive content and links, keep pages useful and current, and assess changes over weeks
rather than promise immediate results. The six platform drafts and query hypotheses above are
project-specific proposals, not Google-endorsed keywords or measured demand.
