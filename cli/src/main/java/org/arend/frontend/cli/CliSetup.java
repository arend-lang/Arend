package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.library.ZipSourceLibrary;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.frontend.TimedProgressReporter;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Two-phase setup for the CLI:
 *
 * <ol>
 *   <li>{@link #bootstrap}: parse {@code -L} dirs, build the {@link ArendServer}, attach the
 *       prelude and error reporter. After this phase {@link CommandContext#server} is usable
 *       (e.g. by {@code -i} REPL) but no user libraries are loaded yet.</li>
 *   <li>{@link #loadRequestedLibraries}: walk positional args to collect requested modules
 *       and libraries (with {@code -s}'s synthetic library), then call {@code updateLibrary}
 *       for each one plus its transitive deps.</li>
 * </ol>
 */
public final class CliSetup {
  private CliSetup() {}

  /**
   * Build server + reporter + libDirs. Does NOT load any libraries yet.
   * Returns true on success; sets {@link CommandContext#exitWithError} on a non-fatal error
   * (e.g. a {@code -L} value that isn't a directory).
   */
  public static boolean bootstrap(CommandContext ctx, CommandLine cmdLine) {
    ctx.doubleCheck = cmdLine.hasOption("c");
    ctx.recompile = cmdLine.hasOption("r");
    ctx.libraryManager = new LibraryManager(ctx.systemErrErrorReporter);
    ctx.requester = new CliServerRequester(ctx.libraryManager);
    if (ctx.recompile) {
      ctx.requester.setRecompile(true);
    }
    ctx.server = new ArendServerImpl(ctx.requester, false, false, !ctx.doubleCheck);
    ctx.server.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> Objects.requireNonNull(new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE)));
    ctx.server.addErrorReporter(ctx.errorReporter);

    if (cmdLine.hasOption("L")) {
      for (String libDirString : cmdLine.getOptionValues("L")) {
        Path libDir = Paths.get(libDirString);
        if (Files.isDirectory(libDir)) {
          ctx.libDirs.add(libDir);
        } else {
          ctx.exitWithError = true;
          System.err.println("[ERROR] " + libDir + " is not a directory");
        }
      }
    } else {
      Path defaultLibrariesRoot = FileUtils.defaultLibrariesRoot();
      if (Files.isDirectory(defaultLibrariesRoot)) {
        ctx.libDirs.add(defaultLibrariesRoot);
      }
    }
    return true;
  }

  /**
   * Walk positional args, the {@code -s/-b/-e/-m} flags, and default to ./arend.yaml when
   * nothing was specified. Populates {@link CommandContext#requestedLibraries} and
   * {@link CommandContext#requestedModules}, then loads each library (and its dependencies)
   * into the server.
   *
   * @return true on success, false if a fatal error was already reported. {@code "Nothing to load"}
   *         is also a true return (the command may still want to run).
   */
  public static boolean loadRequestedLibraries(CommandContext ctx, CommandLine cmdLine) {
    String sourceDirStr = cmdLine.getOptionValue("s");
    Path sourceDir = sourceDirStr == null ? null : Paths.get(sourceDirStr);

    String binaryDirStr = cmdLine.getOptionValue("b");
    Path outDir = binaryDirStr != null ? Paths.get(binaryDirStr) : null;

    String extDirStr = cmdLine.getOptionValue("e");
    Path extDir = extDirStr != null ? Paths.get(extDirStr) : null;
    String extMainClass = cmdLine.getOptionValue("m");

    Collection<String> argFiles = cmdLine.getArgList();
    for (String fileName : argFiles) {
      Path path = Paths.get(fileName);
      if (Files.exists(path)) {
        if (Files.isDirectory(path)) {
          loadFileLibrary(ctx, path.resolve(FileUtils.LIBRARY_CONFIG_FILE), ctx.requestedLibraries);
        } else if (path.endsWith(FileUtils.LIBRARY_CONFIG_FILE)) {
          loadFileLibrary(ctx, path, ctx.requestedLibraries);
        } else if (fileName.endsWith(FileUtils.ZIP_EXTENSION)) {
          loadZipLibrary(ctx, path, ctx.requestedLibraries);
        } else {
          ctx.systemErrErrorReporter.report(new LibraryIOError(fileName, "not a library"));
        }
      } else if (!findLibrary(ctx, fileName, ctx.libDirs, ctx.requestedLibraries)) {
        int colonIndex = fileName.indexOf(':');
        if (colonIndex >= 0) {
          Pair<ModulePath, LongName> parsed = ctx.parseFullName(fileName);
          if (parsed != null && parsed.proj2 != null) {
            ctx.requestedModules.add(parsed);
          } else if (parsed != null) {
            ctx.systemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR,
                "Definition name missing after ':' in " + fileName));
          }
        } else {
          ModulePath modulePath = ModulePath.fromString(fileName);
          if (FileUtils.isCorrectModulePath(modulePath)) {
            ctx.requestedModules.add(new Pair<>(modulePath, null));
          } else {
            ctx.systemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR,
                "File " + fileName + " not found"));
          }
        }
      }
    }

    if (sourceDir != null) {
      if (outDir != null) {
        try {
          Files.createDirectories(outDir);
        } catch (IOException e) {
          ctx.systemErrErrorReporter.report(
              new LibraryIOError(outDir.toString(), "Cannot create output directory", e.getLocalizedMessage()));
          outDir = null;
        }
      }
      ctx.requestedLibraries.add(new FileSourceLibrary("\\default", false, -1,
          ctx.requestedLibraries.stream().map(SourceLibrary::getLibraryName).toList(), null, null, extMainClass, null,
          sourceDir, outDir, null, extDir == null ? null : new FileClassLoaderDelegate(extDir)));
    }

    if (ctx.requestedLibraries.isEmpty()) {
      Path config = Paths.get(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.isRegularFile(config)) {
        loadFileLibrary(ctx, config, ctx.requestedLibraries);
      } else {
        System.out.println("Nothing to load");
        return true;
      }
    }

    if (ctx.exitWithError) {
      return false;
    }

    for (SourceLibrary library : ctx.requestedLibraries) {
      loadLibrary(ctx, library);
    }

    for (SourceLibrary library : ctx.requestedLibraries) {
      if (!loadDependencies(ctx, library)) {
        return false;
      }
    }

    return !ctx.exitWithError;
  }

  // ───────── helpers (private; verbatim from old ConsoleMain) ─────────

  private static void loadLibrary(CommandContext ctx, SourceLibrary library) {
    System.out.println("[INFO] Loading " + library.getLibraryName());
    long time = System.currentTimeMillis();
    ctx.libraryManager.updateLibrary(library, ctx.server);
    System.out.println("[INFO] " + "Loaded " + library.getLibraryName()
        + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
  }

  private static boolean loadDependencies(CommandContext ctx, SourceLibrary library) {
    for (String dependency : library.getLibraryDependencies()) {
      if (!ctx.libraryManager.containsLibrary(dependency)) {
        List<SourceLibrary> libDependency = new ArrayList<>(1);
        findLibrary(ctx, dependency, ctx.libDirs, libDependency);
        if (libDependency.isEmpty()) return false;
        loadLibrary(ctx, libDependency.getFirst());
        if (!loadDependencies(ctx, libDependency.getFirst())) return false;
      }
    }
    return true;
  }

  private static boolean findLibrary(CommandContext ctx, String libName, List<Path> libDirs, List<SourceLibrary> result) {
    if (!FileUtils.isLibraryName(libName)) return false;

    for (Path libDir : libDirs) {
      Path configFile = libDir.resolve(libName).resolve(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.isRegularFile(configFile)) {
        loadFileLibrary(ctx, configFile, result);
        return true;
      } else {
        Path zipFile = libDir.resolve(libName + FileUtils.ZIP_EXTENSION);
        if (Files.isRegularFile(zipFile)) {
          loadZipLibrary(ctx, zipFile, result);
          return true;
        }
      }
    }
    return false;
  }

  private static void loadFileLibrary(CommandContext ctx, Path configFile, List<SourceLibrary> result) {
    SourceLibrary library = FileSourceLibrary.fromConfigFile(configFile, false, ctx.systemErrErrorReporter);
    if (library != null) {
      result.add(library);
    } else {
      ctx.exitWithError = true;
    }
  }

  private static void loadZipLibrary(CommandContext ctx, Path zipFile, List<SourceLibrary> result) {
    SourceLibrary library = ZipSourceLibrary.fromFile(zipFile.toFile(), ctx.systemErrErrorReporter);
    if (library != null) {
      result.add(library);
    } else {
      ctx.exitWithError = true;
    }
  }
}
