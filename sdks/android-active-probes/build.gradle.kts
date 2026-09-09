plugins {
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
