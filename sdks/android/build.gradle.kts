plugins {
  id("com.android.application") version "8.7.2" apply false
  id("com.android.library") version "8.7.2"
  id("org.jetbrains.kotlin.android") version "2.0.21"
}

group = "io.github.afanasievn"
version = "0.1.0-SNAPSHOT"

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
