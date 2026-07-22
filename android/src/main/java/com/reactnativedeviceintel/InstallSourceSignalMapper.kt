package com.reactnativedeviceintel

import android.content.pm.PackageInstaller

internal object InstallSourceSignalMapper {
  fun packageSource(value: Int): String? = when (value) {
    PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED -> "unspecified"
    PackageInstaller.PACKAGE_SOURCE_OTHER -> "other"
    PackageInstaller.PACKAGE_SOURCE_STORE -> "store"
    PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE -> "local_file"
    PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE -> "downloaded_file"
    else -> null
  }
}
