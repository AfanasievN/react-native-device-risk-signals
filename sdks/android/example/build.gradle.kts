plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "io.github.afanasievn.devicerisksignals.example"
  compileSdk = 36
  defaultConfig {
    applicationId = "io.github.afanasievn.devicerisksignals.example"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  lint {
    // The demo is the native-consumer check, so it is held to the same bar as the library it
    // demonstrates: a warning here means a consumer copying this code inherits it. Default severity
    // would let exactly that class of finding through silently.
    abortOnError = true
    warningsAsErrors = true
    checkAllWarnings = true
    checkReleaseBuilds = true
    explainIssues = true
    textReport = true
    lintConfig = file("lint.xml")
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
