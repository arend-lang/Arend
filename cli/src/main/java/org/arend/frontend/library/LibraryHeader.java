package org.arend.frontend.library;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.library.LibraryConfig;
import org.arend.library.error.LibraryIOError;
import org.arend.prelude.Prelude;
import org.arend.util.FileUtils;
import org.arend.util.Range;
import org.arend.util.Version;
import org.arend.util.VersionRange;

import java.util.*;

public record LibraryHeader(Set<ModulePath> modules, List<String> dependencies, Version version,
                            String sourcesDir, String binariesDir, String testDir, String extDir, String extMainClass) {

  public static LibraryHeader fromConfig(LibraryConfig config, String fileName, ErrorReporter errorReporter) {
    if (config.getLangVersion() != null) {
      Range<Version> languageVersion = VersionRange.parseVersionRange(config.getLangVersion());
      if (languageVersion == null) {
        errorReporter.report(new LibraryIOError(fileName, "Cannot parse language version: " + config.getLangVersion()));
        return null;
      }
      if (!languageVersion.inRange(Prelude.VERSION)) {
        errorReporter.report(new LibraryIOError(fileName, "Incompatible language version: " + Prelude.VERSION));
        return null;
      }
    }

    List<String> dependencies = new ArrayList<>();
    if (config.getDependencies() != null) {
      for (String library : config.getDependencies()) {
        if (FileUtils.isLibraryName(library)) {
          dependencies.add(library);
        } else {
          errorReporter.report(new LibraryIOError(fileName, "Illegal dependency name: " + library));
        }
      }
    }

    Version version = null;
    if (config.getVersion() != null) {
      version = Version.fromString(config.getVersion());
      if (version == null) {
        errorReporter.report(new LibraryIOError(fileName, "Cannot parse version: " + config.getVersion()));
        return null;
      }
    }

    Set<ModulePath> modules;
    if (config.getModules() != null) {
      modules = new HashSet<>();
      for (String module : config.getModules()) {
        ModulePath modulePath = FileUtils.modulePath(module);
        if (modulePath != null) {
          modules.add(modulePath);
        } else {
          errorReporter.report(new LibraryIOError(fileName, "Illegal module name: " + module));
        }
      }
    } else {
      modules = null;
    }

    return new LibraryHeader(modules, dependencies, version, config.getSourcesDir(), config.getBinariesDir(), config.getTestsDir(), config.getExtensionsDir(), config.getExtensionMainClass());
  }
}
