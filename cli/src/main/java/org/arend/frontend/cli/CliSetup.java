package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.TimedProgressReporter;
import org.arend.frontend.library.BinaryLoader;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.library.ZipSourceLibrary;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Two-phase setup for the CLI:
 *
 * <ol>
 *   <li>{@link #bootstrap}: read the flags that shape the run, build the {@link ArendServer},
 *       attach the prelude and the error reporters, and resolve {@code -L}. After this the
 *       server is usable — the REPL needs no more than this — but no user library is loaded.</li>
 *   <li>{@link #loadRequestedLibraries}: work out from the positional arguments what was asked
 *       for, then load each library and its transitive dependencies into the server.</li>
 * </ol>
 */
public final class CliSetup {
  private CliSetup() {}

  /**
   * Builds the server, the reporters and the library search path. Loads nothing.
   *
   * @return false only on a failure that makes the run pointless; a recoverable one (a {@code -L}
   *         value that is not a directory) sets {@link CommandContext#exitWithError} instead, so
   *         that every such argument is reported rather than just the first.
   */
  public static boolean bootstrap(CommandContext ctx, CommandLine cmdLine) {
    ctx.doubleCheck = cmdLine.hasOption("c");
    ctx.recompile = cmdLine.hasOption("r");
    ctx.serialize = cmdLine.hasOption("serialize");

    ctx.libraryManager = new LibraryManager(ctx.systemErrErrorReporter);
    ctx.requester = new CliServerRequester(ctx.libraryManager);
    ctx.binaryLoader = new BinaryLoader(ctx.libraryManager);
    if (ctx.recompile) {
      ctx.binaryLoader.setRecompile(true);
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
   * Loads the requested libraries into the server, falling back to {@code ./arend.yaml} when
   * the positional arguments named none.
   *
   * <p>Expects {@link #classifyRequestedTargets} to have run: the caller does that, because it
   * also decides what to do with the classification (the REPL loads its own startup targets and
   * never gets here). Classifying again here would add every named library a second time, and
   * the whole-library typecheck iterates that list.
   *
   * @return false if a fatal error was already reported. Having nothing to load is not one:
   *         the command may still be worth running.
   */
  public static boolean loadRequestedLibraries(CommandContext ctx, CommandLine cmdLine) {
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

    Set<String> loading = new HashSet<>();
    for (SourceLibrary library : ctx.requestedLibraries) {
      if (!loadLibraryWithDependencies(ctx, library, loading)) {
        return false;
      }
    }
    return !ctx.exitWithError;
  }

  /**
   * Loads {@code library} together with its transitive dependencies, dependencies first.
   *
   * <p>The order is the point, not an implementation detail: a library's extension is set up
   * exactly once, when the library is loaded, from the dependency set loaded by that moment
   * (see {@code LibraryService.setupExtension}). Load a dependent before its dependency and it
   * gets an empty dependency map — arend-lib's {@code LiteralTypechecker} never reaches it and
   * number/string literals fail with a bare {@code Type mismatch}. Pinned by
   * {@code LibraryLoadOrderTest}.
   *
   * @param loading the library names on the current dependency chain; also breaks cycles.
   * @return false if a dependency cannot be found, in which case the dependent is not loaded either.
   */
  public static boolean loadLibraryWithDependencies(CommandContext ctx, SourceLibrary library, Set<String> loading) {
    if (ctx.libraryManager.containsLibrary(library.getLibraryName()) || !loading.add(library.getLibraryName())) return true;

    for (String dependency : library.getLibraryDependencies()) {
      if (ctx.libraryManager.containsLibrary(dependency) || loading.contains(dependency)) continue;
      List<SourceLibrary> libDependency = new ArrayList<>(1);
      findLibrary(ctx, dependency, libDependency);
      if (libDependency.isEmpty()) return false;
      if (!loadLibraryWithDependencies(ctx, libDependency.getFirst(), loading)) return false;
    }

    loadLibrary(ctx, library);
    return true;
  }

  /**
   * Interprets the positional arguments and {@code -s}/{@code -e}/{@code -m} into
   * {@link CommandContext#requestedLibraries} and {@link CommandContext#requestedModules}.
   * Classification only — nothing is loaded into the server here.
   *
   * <p>Split out of {@link #loadRequestedLibraries} because the REPL ({@code -i}) needs the
   * classification without the loading: it loads its own startup targets. Both paths must agree
   * on what an argument means, so they share this one implementation.
   */
  public static void classifyRequestedTargets(CommandContext ctx, CommandLine cmdLine) {
    String sourceDirStr = cmdLine.getOptionValue("s");
    Path sourceDir = sourceDirStr == null ? null : Paths.get(sourceDirStr);

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
      } else if (!findLibrary(ctx, fileName, ctx.requestedLibraries)) {
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
      ctx.requestedLibraries.add(new FileSourceLibrary("\\default", false, -1,
          ctx.requestedLibraries.stream().map(SourceLibrary::getLibraryName).toList(), null, null, extMainClass, null,
          sourceDir, null, null, extDir == null ? null : new FileClassLoaderDelegate(extDir)));
    }
  }

  /**
   * Re-reads the positional arguments as typecheck targets against libraries that are
   * <em>already loaded</em>. The counterpart to {@link #classifyRequestedTargets}, for a context
   * being reused: a positional naming a loaded library is nothing to do, and everything else is
   * a module, or a module and a definition inside it.
   *
   * <p>An argument that is neither is reported and fails the run, exactly as
   * {@code classifyRequestedTargets} reports "File X not found" on a fresh context. The two have
   * to agree: the same argv reaching the same libraries must not get two verdicts depending on
   * which path read it.
   */
  public static void populateRequestedTargets(CommandContext ctx, CommandLine cmdLine) {
    populateRequestedTargets(ctx, cmdLine, null);
  }

  /**
   * @param clientCwd the directory the command was issued from, or null for this process's own.
   *                  It decides whether a positional is an existing path or a module name, and a
   *                  daemon asking that question about its own directory gets a different answer
   *                  from the one the client would have got.
   */
  public static void populateRequestedTargets(CommandContext ctx, CommandLine cmdLine, String clientCwd) {
    Set<String> loadedLibNames = new HashSet<>();
    for (SourceLibrary library : ctx.requestedLibraries) loadedLibNames.add(library.getLibraryName());

    for (String positional : cmdLine.getArgList()) {
      // A positional may name the library the way classifyRequestedTargets accepts one, which
      // includes a path: `arend .` is the library in the current directory.
      if (loadedLibNames.contains(positional) || Files.exists(against(clientCwd, positional))) continue;
      if (positional.indexOf(':') >= 0) {
        // parseFullName reports what is wrong with it and returns null.
        Pair<ModulePath, LongName> parsed = ctx.parseFullName(positional);
        if (parsed != null) ctx.requestedModules.add(parsed);
        continue;
      }
      ModulePath modulePath = ModulePath.fromString(positional);
      if (FileUtils.isCorrectModulePath(modulePath)) {
        ctx.requestedModules.add(new Pair<>(modulePath, null));
      } else {
        ctx.systemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR,
            "File " + positional + " not found"));
      }
    }
  }

  // ───────── helpers ─────────

  /** {@code path} as the client would have seen it: resolved against {@code clientCwd} if given. */
  private static Path against(String clientCwd, String path) {
    Path p = Paths.get(path);
    return clientCwd == null || p.isAbsolute() ? p : Paths.get(clientCwd).resolve(p);
  }

  private static void loadLibrary(CommandContext ctx, SourceLibrary library) {
    System.out.println("[INFO] Loading " + library.getLibraryName());
    long time = System.currentTimeMillis();
    ctx.libraryManager.updateLibrary(library, ctx.server);
    System.out.println("[INFO] " + "Loaded " + library.getLibraryName()
        + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
  }

  private static boolean findLibrary(CommandContext ctx, String libName, List<SourceLibrary> result) {
    if (!FileUtils.isLibraryName(libName)) return false;

    for (Path libDir : ctx.libDirs) {
      Path configFile = libDir.resolve(libName).resolve(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.isRegularFile(configFile)) {
        warnIfShadowsCwd(libName, configFile);
        loadFileLibrary(ctx, configFile, result);
        return true;
      } else {
        Path zipFile = libDir.resolve(libName + FileUtils.ZIP_EXTENSION);
        if (Files.isRegularFile(zipFile)) {
          warnIfShadowsCwd(libName, zipFile);
          loadZipLibrary(ctx, zipFile, result);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Warns when the library just resolved out of a {@code -L} directory has a namesake in the
   * current directory. By convention a library's name is its directory's name, so a cwd
   * {@code arend.yaml} under a directory called {@code libName} is almost certainly the copy the
   * user meant -- but the libdir match wins, because {@code findLibrary} is also reached for
   * transitive dependencies, where the current directory means nothing.
   *
   * <p>Silently picking the other one is the failure this exists to make visible: it typechecks,
   * and every diagnostic afterwards points at source the user is not looking at.
   */
  private static void warnIfShadowsCwd(String libName, Path chosen) {
    Path cwdConfig = Paths.get(FileUtils.LIBRARY_CONFIG_FILE).toAbsolutePath();
    if (!Files.isRegularFile(cwdConfig)) return;
    Path parent = cwdConfig.getParent();
    Path dirName = parent == null ? null : parent.getFileName();
    if (dirName == null || !libName.equals(dirName.toString())) return;
    try {
      if (cwdConfig.toRealPath().equals(chosen.toRealPath())) return;
    } catch (IOException ignored) {
      // Cannot prove they are the same file; warning is the safe side.
    }
    System.err.println("[WARN] library '" + libName + "' has multiple copies: "
        + cwdConfig + " (cwd) and " + chosen.toAbsolutePath()
        + " — using the libdir copy. Pass `-L` explicitly or remove the unused copy to make the choice explicit.");
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
