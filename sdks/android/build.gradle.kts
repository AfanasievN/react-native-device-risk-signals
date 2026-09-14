plugins {
  id("com.android.application") version "8.7.2" apply false
  id("com.android.library") version "8.7.2"
  id("org.jetbrains.kotlin.android") version "2.0.21"
  id("maven-publish")
}

group = "io.github.afanasievn"
version = "0.1.0-SNAPSHOT"

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
    // the collector implementation. No javadoc/dokka jar is produced here - see the README note.
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
    // No remote registry, credentials or signing configuration is declared here on purpose: this
    // component is not published yet (device-risk-signals.json, distribution.published = false).
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
