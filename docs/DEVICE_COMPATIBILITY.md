# Device compatibility

This page separates automated build compatibility from physical-device evidence. A successful
simulator build proves that native code compiles; it does not prove that every observation behaves
correctly on real hardware, vendor-modified Android builds, rooted devices, or jailbroken devices.

## Automated coverage

Every pull request runs the following compatibility checks:

| Surface | Coverage |
| --- | --- |
| React Native | 0.76.9, 0.81.6, and 0.86.0 |
| Architecture | New Architecture and TurboModule contract parity |
| Android | Native unit tests, example debug build, and Android lint |
| iOS | CocoaPods installation and example simulator build |
| JavaScript | Jest, TypeScript declarations, package contents, and documentation links |

See the current [CI workflow](../.github/workflows/ci.yml) for the executable source of truth.

## Community-reported physical devices

No physical-device combinations are listed until a maintainer or community member supplies a
sanitized reproducible report. This avoids presenting simulator coverage as production validation.

| Package version | Platform | OS | Device class | Build | Result | Report |
| --- | --- | --- | --- | --- | --- | --- |
| _Awaiting reports_ | | | | | | |

A report confirms only the described configuration. It is not a certification of every probe or an
assurance that a rooted, jailbroken, emulated, or instrumented environment will always be detected.

## Submit a compatibility report

Use the [device compatibility issue form](https://github.com/AfanasievN/react-native-device-risk-signals/issues/new?template=03-device-compatibility.yml).
Include:

- exact package and React Native versions;
- platform, OS version, and coarse device model;
- debug or release build and installation method;
- probes exercised and whether collection completed;
- only sanitized excerpts needed to explain unexpected omissions or failures.

Do not include credentials, account or transaction data, precise location, local addresses, stable
identifiers, or a full production event. Reports for intentionally modified devices should describe
the modification at a high level without publishing a new bypass before private review.

## Maintainer validation levels

- **Build verified:** automated compilation and contract checks passed.
- **Community reported:** a contributor supplied a reproducible physical-device result.
- **Maintainer reproduced:** a maintainer repeated the result on comparable hardware.

Unavailable optional fields are not failures by themselves. Availability varies by platform, OS
version, hardware, host permissions, application state, and store policy.
