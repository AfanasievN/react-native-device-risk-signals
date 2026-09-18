plugins {
  id("com.android.application") version "8.7.2" apply false
  id("com.android.library") version "8.7.2"
  id("org.jetbrains.kotlin.android") version "2.0.21"
  id("maven-publish")
  // Signing is applied unconditionally but only *configured* when a key is actually present (see
  // the `signing` block at the end of this file). Applying the plugin costs nothing without a key;
  // it is what lets a release build produce the `.asc` signatures Maven Central requires.
  id("signing")
  // Dokka renders the KDoc into the javadoc jar Maven Central requires. `dokka-javadoc` is
  // applied alone: it brings the Dokka base plugin with it, and applying both declares the
  // `dokkaPlugin` configuration twice.
  id("org.jetbrains.dokka-javadoc") version "2.0.0"
}

group = "io.github.afanasievn"

// Single declared place for this component's version: `gradle.properties` in this directory. A
// release changes that one line; CI overrides it without editing a file, either with
// `-PdeviceRiskSignalsVersion=X.Y.Z` or with the `ORG_GRADLE_PROJECT_deviceRiskSignalsVersion`
// environment variable. The default stays `0.1.0-SNAPSHOT`: no release version is implied here.
version = providers.gradleProperty("deviceRiskSignalsVersion").get()

// Coordinates are normative in device-risk-signals.json (component "android",
// distribution.name = "io.github.afanasievn:android-device-risk-signals"). They are spelled out
// here instead of inherited from rootProject.name so that renaming the Gradle project can never
// silently change the published artifact id.
val publishedArtifactId = "android-device-risk-signals"
val projectUrl = "https://github.com/AfanasievN/react-native-device-risk-signals"

android {
  namespace = "io.github.afanasievn.devicerisksignals"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
    // Instrumented tests only; the published library adds no test runtime to consumers.
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  publishing {
    // Only the release variant is published; a debug variant would ship an unoptimised build with
    // no consumer value. The sources jar is required by Maven Central and lets consumers step into
    // the collector implementation. The javadoc jar is rendered
    // from the KDoc by Dokka and attached to the publication below.
    singleVariant("release") {
      withSourcesJar()
    }
  }

  lint {
    // Lint is a release gate for this component: any finding fails the build, including warnings.
    abortOnError = true
    warningsAsErrors = true
    checkAllWarnings = true
    checkReleaseBuilds = true
    // Collector tests exercise platform constants directly, so they are held to the same bar.
    checkTestSources = true
    lintConfig = file("lint.xml")
    explainIssues = true
    // Text report goes to the build log so a CI failure is readable without downloading artifacts.
    textReport = true
    htmlReport = true
    xmlReport = true
    disable +=
      setOf(
        // Version-freshness only: fires whenever AGP publishes a release, needs network, and says
        // nothing about this component's code. AGP upgrades are a deliberate, tested change.
        "AndroidGradlePluginVersion",
        // Version-freshness only: would fail CI on the day an unrelated test dependency ships an
        // update. Dependency bumps are reviewed, not lint-driven.
        "GradleDependency",
        // Version-freshness only, and network-dependent by its own definition (queries Maven
        // Central on every run), so it makes the gate non-deterministic.
        "NewerVersionAvailable",
        // Dex-method-count micro-optimization aimed at apps near the 64K limit. It fires on
        // idiomatic Kotlin private top-level helpers called from a class in the same file
        // (GpuResourceManagement.kt, SignalModels.kt); avoiding it would mean widening the
        // visibility of internal helpers, which is worse for an SDK's public surface.
        "SyntheticAccessor",
      )
  }
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
  }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  // ActivityScenario, for instrumented tests that need a real Activity and a real window callback
  // chain. Test-only: no consumer of the published library resolves it.
  androidTestImplementation("androidx.test:core:1.6.1")
}

// Maven Central rejects a publication without a javadoc artifact. This one is real documentation
// rendered from the KDoc, not an empty placeholder: an empty jar would satisfy the validator while
// telling a consumer nothing.
val javadocJar by tasks.registering(Jar::class) {
  archiveClassifier.set("javadoc")
  from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

publishing {
  repositories {
    // A directory repository inside this component's own build output. Verification publishes and
    // resolves here, so nothing is written outside the repository tree and CI stays hermetic:
    // `~/.m2` is never touched unless someone explicitly asks for `publishToMavenLocal`, which the
    // maven-publish plugin still provides.
    maven {
      name = "localBuild"
      url = uri(layout.buildDirectory.dir("local-maven"))
    }
    // The Maven Central target. Declaring it changes nothing about the local flow above: this
    // repository is only ever contacted by
    // `publishReleasePublicationToCentralPortalOssrhStagingRepository`, and that task fails
    // immediately without credentials. Nothing here can publish by accident, and no credential is
    // stored in the repository - both values come from properties or the matching
    // `ORG_GRADLE_PROJECT_*` environment variables, which CI fills from secrets.
    //
    // Sonatype OSSRH (oss.sonatype.org / s01.oss.sonatype.org) was retired on 2025-06-30 and is
    // replaced by the Central Portal. `maven-publish` has no official Central Portal plugin, so the
    // documented path for a plain `maven-publish` build is the Portal's OSSRH Staging API
    // compatibility endpoint below; the deployment then has to be released from the Portal (the
    // release workflow calls the documented `/manual/upload/defaultRepository/<namespace>` endpoint
    // so the upload becomes visible there).
    // https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/
    // https://central.sonatype.org/publish/publish-portal-snapshots/
    maven {
      name = "centralPortalOssrhStaging"
      url =
        if (version.toString().endsWith("-SNAPSHOT")) {
          uri("https://central.sonatype.com/repository/maven-snapshots/")
        } else {
          uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
        }
      credentials {
        username = providers.gradleProperty("centralPortalUsername").orNull
        password = providers.gradleProperty("centralPortalPassword").orNull
      }
    }
  }

  publications {
    // The release component is created by the Android Gradle plugin during its own evaluation, so
    // the publication has to be registered afterwards.
    afterEvaluate {
      register<MavenPublication>("release") {
        from(components["release"])
        groupId = project.group.toString()
        artifactId = publishedArtifactId
        version = project.version.toString()
        // The sources jar comes from `withSourcesJar()` above; the javadoc jar has no AGP
        // equivalent, so it is attached explicitly.
        artifact(javadocJar)

        pom {
          name.set("Android Device Risk Signals")
          description.set(
            "Standalone Android SDK that collects raw device and runtime observations - root, " +
              "emulator, tampering and integrity signals - without scoring, verdicts or network " +
              "access.",
          )
          url.set(projectUrl)
          inceptionYear.set("2026")

          licenses {
            license {
              name.set("MIT License")
              url.set("$projectUrl/blob/main/LICENSE")
              distribution.set("repo")
            }
          }

          developers {
            developer {
              id.set("AfanasievN")
              name.set("AfanasievN")
              url.set("https://github.com/AfanasievN")
            }
          }

          scm {
            connection.set("scm:git:https://github.com/AfanasievN/react-native-device-risk-signals.git")
            developerConnection.set("scm:git:ssh://git@github.com/AfanasievN/react-native-device-risk-signals.git")
            url.set(projectUrl)
          }

          issueManagement {
            system.set("GitHub Issues")
            url.set("$projectUrl/issues")
          }
        }
      }
    }
  }
}

// Signing is gated on a key actually being available. With no key - every local build, every pull
// request, the whole CI task list - no signing task is created at all, so `:assembleRelease`,
// `:lintRelease` and `:publishReleasePublicationToLocalBuildRepository` behave exactly as they did
// before signing existed. With a key, every artifact in the publication (AAR, sources jar, javadoc
// jar, POM, module metadata) gains the detached `.asc` signature Maven Central requires.
//
// The key is read in memory only; no keyring file is expected and nothing is written to the
// developer's GnuPG home. CI supplies the three values as `ORG_GRADLE_PROJECT_signingInMemoryKey`,
// `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` and, optionally,
// `ORG_GRADLE_PROJECT_signingInMemoryKeyId` (only needed to pick one subkey out of a key that has
// several). The key itself is the ASCII-armored secret key, newlines included.
signing {
  val inMemoryKey = providers.gradleProperty("signingInMemoryKey").orNull
  val inMemoryKeyId = providers.gradleProperty("signingInMemoryKeyId").orNull
  val inMemoryKeyPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull.orEmpty()

  if (inMemoryKey.isNullOrBlank()) {
    logger.info(
      "No signing key available; the release publication will be unsigned. Maven Central rejects " +
        "unsigned deployments, so a real release must set ORG_GRADLE_PROJECT_signingInMemoryKey.",
    )
  } else {
    if (inMemoryKeyId.isNullOrBlank()) {
      useInMemoryPgpKeys(inMemoryKey, inMemoryKeyPassword)
    } else {
      useInMemoryPgpKeys(inMemoryKeyId, inMemoryKey, inMemoryKeyPassword)
    }
    // Registered after the publishing block above so this callback runs after the one that creates
    // the publication: `afterEvaluate` callbacks fire in registration order.
    afterEvaluate { sign(publishing.publications["release"]) }
  }
}
