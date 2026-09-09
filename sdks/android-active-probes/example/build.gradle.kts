plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "io.github.afanasievn.devicerisksignals.active.example"
  compileSdk = 36
  defaultConfig {
    applicationId = "io.github.afanasievn.devicerisksignals.active.example"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
  }
}

dependencies {
  implementation(project(":"))
}
