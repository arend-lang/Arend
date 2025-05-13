package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.*;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.library.error.LibraryIOError;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.impl.ArendServerImpl;
import org.arend.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ConsoleMain {
  private boolean myExitWithError;

  private final ErrorReporter mySystemErrErrorReporter = error -> {
    System.err.println(error);
    System.err.flush();
    myExitWithError = true;
  };

  private CommandLine parseArgs(String[] args) {
    try {
      Options cmdOptions = new Options();
      cmdOptions.addOption("h", "help", false, "print this message");
      cmdOptions.addOption(Option.builder("L").longOpt("libdir").hasArg().argName("dir").desc("directory containing libraries").build());
      cmdOptions.addOption(Option.builder("s").longOpt("sources").hasArg().argName("dir").desc("project source directory").build());
      cmdOptions.addOption(Option.builder("e").longOpt("extensions").hasArg().argName("dir").desc("language extensions directory").build());
      cmdOptions.addOption(Option.builder("m").longOpt("extension-main").hasArg().argName("class").desc("main extension class").build());
      cmdOptions.addOption("v", "version", false, "print language version");
      CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

      if (cmdLine.hasOption("h")) {
        new HelpFormatter().printHelp("arend [FILES]", cmdOptions);
        return null;
      }

      if (cmdLine.hasOption("v")) {
        System.out.println("Arend " + Prelude.VERSION);
        return null;
      }

      return cmdLine;
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      return null;
    }
  }

  private boolean run(String[] args) {
    CommandLine cmdLine = parseArgs(args);
    if (cmdLine == null) return false;

    LibraryManager libraryManager = new LibraryManager(mySystemErrErrorReporter);
    ArendServer server = new ArendServerImpl(new CliServerRequester(libraryManager), false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, Objects.requireNonNull(new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE)));

    // Get library directories
    List<Path> libDirs = new ArrayList<>();
    if (cmdLine.hasOption("L")) {
      for (String libDirString : cmdLine.getOptionValues("L")) {
        Path libDir = Paths.get(libDirString);
        if (Files.isDirectory(libDir)) {
          libDirs.add(libDir);
        } else {
          myExitWithError = true;
          System.err.println("[ERROR] " + libDir + " is not a directory");
        }
      }
    } else {
      Path defaultLibrariesRoot = FileUtils.defaultLibrariesRoot();
      if (Files.isDirectory(defaultLibrariesRoot)) {
        libDirs.add(defaultLibrariesRoot);
      }
    }

    // Get source and output directories
    String sourceDirStr = cmdLine.getOptionValue("s");
    Path sourceDir = sourceDirStr == null ? null : Paths.get(sourceDirStr);

    String binaryDirStr = cmdLine.getOptionValue("b");
    Path outDir = binaryDirStr != null ? Paths.get(binaryDirStr) : null;

    String extDirStr = cmdLine.getOptionValue("e");
    Path extDir = extDirStr != null ? Paths.get(extDirStr) : null;
    String extMainClass = cmdLine.getOptionValue("m");

    // Collect modules and libraries for which typechecking was requested
    Collection<String> argFiles = cmdLine.getArgList();
    Set<ModulePath> requestedModules = new LinkedHashSet<>();
    List<SourceLibrary> requestedLibraries = new ArrayList<>();
    for (String fileName : argFiles) {
      Path path = Paths.get(fileName);
      if (Files.exists(path)) {
        if (Files.isDirectory(path)) {
          loadFileLibrary(path.resolve(FileUtils.LIBRARY_CONFIG_FILE), requestedLibraries);
        } else if (path.endsWith(FileUtils.LIBRARY_CONFIG_FILE)) {
          loadFileLibrary(path, requestedLibraries);
        } else if (fileName.endsWith(FileUtils.ZIP_EXTENSION)) {
          loadZipLibrary(path, requestedLibraries);
        } else {
          ModulePath modulePath;
          if (path.isAbsolute() || path.getNameCount() > 1 || fileName.endsWith(FileUtils.EXTENSION)) {
            modulePath = path.isAbsolute() ? null : FileUtils.modulePath(path, FileUtils.EXTENSION);
          } else {
            modulePath = FileUtils.modulePath(fileName);
          }
          if (modulePath == null) {
            mySystemErrErrorReporter.report(FileUtils.illegalModuleName(fileName));
          } else {
            requestedModules.add(modulePath);
          }
        }
      } else if (!findLibrary(fileName, libDirs, requestedLibraries)) {
        ModulePath modulePath = ModulePath.fromString(fileName);
        if (FileUtils.isCorrectModulePath(modulePath)) {
          requestedModules.add(modulePath);
        } else {
          mySystemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR, "File " + fileName + " not found"));
        }
      }
    }

    if (sourceDir != null) {
      if (outDir != null) {
        try {
          Files.createDirectories(outDir);
        } catch (IOException e) {
          mySystemErrErrorReporter.report(new LibraryIOError(outDir.toString(), "Cannot create output directory", e.getLocalizedMessage()));
          outDir = null;
        }
      }

      requestedLibraries.add(new FileSourceLibrary("\\default", false, -1,
          requestedLibraries.stream().map(SourceLibrary::getLibraryName).toList(), null, extMainClass, null,
          sourceDir, outDir, null, extDir == null ? null : new FileClassLoaderDelegate(extDir)));
    }

    if (requestedLibraries.isEmpty()) {
      Path config = Paths.get(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.isRegularFile(config)) {
        loadFileLibrary(config, requestedLibraries);
      } else {
        System.out.println("Nothing to load");
        return true;
      }
    }

    if (myExitWithError) {
      return false;
    }

    for (SourceLibrary library : requestedLibraries) {
      libraryManager.updateLibrary(library, server);
    }

    for (SourceLibrary library : requestedLibraries) {
      loadDependencies(library, libraryManager, libDirs, server);
    }

    if (myExitWithError) {
      return false;
    }

    // TODO[server2]: Do typechecking

    return true;
  }

  private boolean loadDependencies(SourceLibrary library, LibraryManager libraryManager, List<Path> libDirs, ArendServer server) {
    for (String dependency : library.getLibraryDependencies()) {
      if (!libraryManager.containsLibrary(dependency)) {
        List<SourceLibrary> libDependency = new ArrayList<>(1);
        findLibrary(dependency, libDirs, libDependency);
        if (libDependency.isEmpty()) return false;
        libraryManager.updateLibrary(libDependency.getFirst(), server);
        if (!loadDependencies(libDependency.getFirst(), libraryManager, libDirs, server)) return false;
      }
    }
    return true;
  }

  private boolean findLibrary(String libName, List<Path> libDirs, List<SourceLibrary> result) {
    if (!FileUtils.isLibraryName(libName)) return false;

    for (Path libDir : libDirs) {
      Path configFile = libDir.resolve(libName).resolve(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.isRegularFile(configFile)) {
        loadFileLibrary(configFile, result);
        return true;
      } else {
        Path zipFile = libDir.resolve(libName + FileUtils.ZIP_EXTENSION);
        if (Files.isRegularFile(zipFile)) {
          loadZipLibrary(zipFile, result);
          return true;
        }
      }
    }

    return false;
  }

  private void loadFileLibrary(Path configFile, List<SourceLibrary> result) {
    SourceLibrary library = FileSourceLibrary.fromConfigFile(configFile, false, mySystemErrErrorReporter);
    if (library != null) {
      result.add(library);
    } else {
      myExitWithError = true;
    }
  }

  private void loadZipLibrary(Path zipFile, List<SourceLibrary> result) {
    SourceLibrary library = ZipSourceLibrary.fromFile(zipFile.toFile(), mySystemErrErrorReporter);
    if (library != null) {
      result.add(library);
    } else {
      myExitWithError = true;
    }
  }

  public static void main(String[] args) {
    ConsoleMain main = new ConsoleMain();
    if (!main.run(args) || main.myExitWithError) {
      System.exit(1);
    }
  }
}
