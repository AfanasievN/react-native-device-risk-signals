plugins {
  id("com.android.application") version "8.7.2" apply false
  id("com.android.library") version "8.7.2"
  id("org.jetbrains.kotlin.android") version "2.0.21"
  id("maven-publish")
  // Dokka renders the KDoc into the javadoc jar Maven Central requires. `dokka-javadoc` is
  // applied alone: it brings the Dokka base plugin with it, and applying both declares the
  // `dokkaPlugin` configuration twice.
  id("org.jetbrains.dokka-javadoc") version "2.0.0"
}

group = "io.github.afanasievn"
version = "0.1.0-SNAPSHOT"

// Coordinates are normative in device-risk-signals.json (component "android-active-probes",
// distribution.name = "io.github.afanasievn:android-active-probes-device-risk-signals"). They are
// spelled out here instead of inherited from rootProject.name so that renaming the Gradle project
// can never silently change the published artifact id.
val publishedArtifactId = "android-active-probes-device-risk-signals"
val projectUrl = "https://github.com/AfanasievN/react-native-device-risk-signals"

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

  publishing {
    // Only the release variant is published; a debug variant would ship an unoptimised build with
    // no consumer value. The sources jar is required by Maven Central and lets consumers step into
    // the probe implementation. The javadoc jar is
    // rendered from the KDoc by Dokka and attached to the publication below.
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

// The defaults test pins this component's declared probe default to the shared probe catalog, which
// lives outside the module. Gradle cannot see that read, so the file is declared as a test input:
// otherwise editing the catalog leaves `testDebugUnitTest` UP-TO-DATE and the pin silently stops
// guarding anything.
tasks.withType<Test>().configureEach {
  inputs
    .file(rootProject.file("../../contract/source/probe-catalog.source.json"))
    .withPropertyName("sharedProbeCatalogSource")
    .withPathSensitivity(PathSensitivity.RELATIVE)
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
        // The sources jar comes from `withSourcesJar()` above; the javadoc jar has no AGP
        // equivalent, so it is attached explicitly.
        artifact(javadocJar)

        pom {
          name.set("Android Active Probes Device Risk Signals")
          description.set(
            "Standalone Android SDK for opt-in active device observations, such as the local " +
              "loopback port scan, kept out of the passive core. It scores nothing, returns no " +
              "verdict and performs no remote network access.",
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
