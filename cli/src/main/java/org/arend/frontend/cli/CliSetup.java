package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.cli.ai.AiOutputRouter;
import org.arend.frontend.cli.ai.Granularity;
import org.arend.frontend.cli.ai.InvocationLog;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    // Serialization is on by default: .arc binary caches are part of the normal
    // CLI output and dramatically speed up follow-up runs. --no-serialize keeps
    // the run read-only with respect to binary caches.
    ctx.serialize = !cmdLine.hasOption("no-serialize");
    ctx.aiMode = cmdLine.hasOption("ai");
    ctx.noQuiet = cmdLine.hasOption("no-quiet");
    if (cmdLine.hasOption("slow-warn")) {
      try {
        ctx.slowWarnMs = Long.parseLong(cmdLine.getOptionValue("slow-warn"));
      } catch (NumberFormatException e) {
        System.err.println("[ERROR] --slow-warn expects an integer (ms), got: "
            + cmdLine.getOptionValue("slow-warn"));
        ctx.exitWithError = true;
      }
    }
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
    classifyRequestedTargets(ctx, cmdLine);

    // Install the router unconditionally before any branch can return, so downstream
    // pipelines (TypecheckPipeline.runAiNameResolve, finalizeAi, etc.) can rely on
    // ctx.outputRouter being non-null even when no library was loaded.
    installOutputRouter(ctx);

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
      findLibrary(ctx, dependency, ctx.libDirs, libDependency);
      if (libDependency.isEmpty()) return false;
      if (!loadLibraryWithDependencies(ctx, libDependency.getFirst(), loading)) return false;
    }

    loadLibrary(ctx, library);
    return true;
  }

  /**
   * Interprets the positional arguments and the {@code -s}/{@code -b}/{@code -e}/{@code -m} options
   * into {@link CommandContext#requestedLibraries} and {@link CommandContext#requestedModules}.
   * Classification only — nothing is loaded into the server here.
   *
   * <p>Split out of {@link #loadRequestedLibraries} because the REPL ({@code -i}) needs the
   * classification without the loading: it loads its own startup targets. Both paths must agree
   * on what an argument means, so they share this one implementation.
   */
  public static void classifyRequestedTargets(CommandContext ctx, CommandLine cmdLine) {
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
  }

  /**
   * Construct {@link CommandContext#outputRouter} from the current {@code aiMode}
   * and requested-modules state. Replaces any existing router on the context — this
   * is called once for in-process runs (via {@link #loadRequestedLibraries}) and again
   * per client request inside the daemon, with a fresh {@link InvocationLog} each
   * time so logs don't grow append-only.
   *
   * <p>Non-{@code -ai} runs get a {@link AiOutputRouter.Mode#PROXY} router (everything
   * to stdout/stderr); {@code -ai} runs get {@link AiOutputRouter.Mode#SPLIT} (narration
   * to the log, diagnostics filtered by {@link Granularity}).
   */
  public static void installOutputRouter(CommandContext ctx) {
    // Close any previous log so we don't keep two handles open.
    if (ctx.outputRouter != null && ctx.outputRouter.log() != null) {
      ctx.outputRouter.log().close();
    }
    Granularity granularity = Granularity.from(ctx.requestedModules);
    java.nio.file.Path libRoot = pickLibraryRoot(ctx);
    InvocationLog log = libRoot != null
        ? InvocationLog.forLibraryRoot(libRoot, ctx.requestId)
        : InvocationLog.atExplicitPath(
            java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                "arend-" + ctx.requestId + ".log"));
    AiOutputRouter.Mode mode = (ctx.aiMode && !ctx.noQuiet)
        ? AiOutputRouter.Mode.SPLIT : AiOutputRouter.Mode.PROXY;
    ctx.outputRouter = new AiOutputRouter(granularity, mode, log, System.out, System.err);
    if (ctx.requester != null) ctx.requester.setOutputRouter(ctx.outputRouter);
  }

  private static java.nio.file.Path pickLibraryRoot(CommandContext ctx) {
    for (SourceLibrary lib : ctx.requestedLibraries) {
      if (lib instanceof FileSourceLibrary fsl) {
        java.nio.file.Path base = fsl.getBasePath();
        if (base != null) return base;
      }
    }
    return null;
  }

  // ───────── helpers (private; verbatim from old ConsoleMain) ─────────

  private static void loadLibrary(CommandContext ctx, SourceLibrary library) {
    routeInfo(ctx, "[INFO] Loading " + library.getLibraryName());
    long time = System.currentTimeMillis();
    ctx.libraryManager.updateLibrary(library, ctx.server);
    routeInfo(ctx, "[INFO] " + "Loaded " + library.getLibraryName()
        + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
  }

  /** Route an informational line through the router, falling back to stdout if not yet installed. */
  private static void routeInfo(CommandContext ctx, String line) {
    if (ctx.outputRouter != null) ctx.outputRouter.info(line);
    else System.out.println(line);
  }


  private static boolean findLibrary(CommandContext ctx, String libName, List<Path> libDirs, List<SourceLibrary> result) {
    if (!FileUtils.isLibraryName(libName)) return false;

    for (Path libDir : libDirs) {
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
   * When a libDir-resolved lib of name {@code libName} is about to be loaded, check
   * whether the cwd's own {@code arend.yaml} also declares a library of that name
   * (the convention is "name = parent directory name"). If so, warn — the user
   * almost certainly meant the cwd-local copy, but the libDirs match wins because
   * {@code findLibrary} is also reached for transitive dependencies where cwd is
   * not necessarily the depending library.
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
      // fall through to the warning
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
