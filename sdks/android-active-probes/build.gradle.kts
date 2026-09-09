plugins {
  id("com.android.application") version "8.7.2" apply false
  id("com.android.library") version "8.7.2"
  id("org.jetbrains.kotlin.android") version "2.0.21"
}

group = "io.github.afanasievn"
version = "0.1.0-SNAPSHOT"

android {
  namespace = "io.github.afanasievn.devicerisksignals.active"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
    // No consumerProguardFiles: like the passive core, this component exposes only ordinary
    // Kotlin models and needs no consumer keep rules.
    // No testInstrumentationRunner: the loopback scan is verified by JVM tests against a local
    // ServerSocket, so this module wires no androidTest runtime and adds no AndroidX dependency.
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  lint {
    // Lint is a release gate for this component: any finding fails the build, including warnings.
    abortOnError = true
    warningsAsErrors = true
    checkAllWarnings = true
    checkReleaseBuilds = true
    // The loopback scan is covered by JVM tests only, so those sources are gated too.
    checkTestSources = true
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
}
