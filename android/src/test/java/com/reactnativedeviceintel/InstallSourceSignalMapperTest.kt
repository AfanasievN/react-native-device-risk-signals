package com.reactnativedeviceintel

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallSourceSignalMapperTest {
  @Test
  fun mapsEveryPublicPackageSourceValue() {
    assertEquals("unspecified", InstallSourceSignalMapper.packageSource(PackageInstaller.PACKAGE_SOURCE_UNSPECIFIED))
    assertEquals("other", InstallSourceSignalMapper.packageSource(PackageInstaller.PACKAGE_SOURCE_OTHER))
    assertEquals("store", InstallSourceSignalMapper.packageSource(PackageInstaller.PACKAGE_SOURCE_STORE))
    assertEquals("local_file", InstallSourceSignalMapper.packageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE))
    assertEquals("downloaded_file", InstallSourceSignalMapper.packageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE))
  }

  @Test
  fun omitsUnknownPackageSourceValues() {
    assertNull(InstallSourceSignalMapper.packageSource(Int.MAX_VALUE))
  }
}
