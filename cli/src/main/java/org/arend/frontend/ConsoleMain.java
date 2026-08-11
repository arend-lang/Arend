package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.core.definition.Definition;
import org.arend.core.expr.visitor.SizeExpressionVisitor;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.*;
import org.arend.frontend.query.*;
import org.arend.frontend.query.classhierarchy.ClassHierarchyTool;
import org.arend.frontend.query.findusages.FindUsagesTool;
import org.arend.frontend.query.proofsearch.ProofSearchTool;
import org.arend.frontend.query.scopeinfo.ScopeInfoTool;
import org.arend.frontend.query.symbolsearch.SymbolSearchTool;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.library.error.LibraryIOError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.error.local.GoalError;
import org.arend.typechecking.order.MapTarjanSCC;
import org.arend.util.FileUtils;

import org.arend.source.PersistableBinarySource;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.arend.ext.prettyprinting.PrettyPrinterConfig.DEFAULT;

public class ConsoleMain {

  private boolean myExitWithError;
  private boolean mySuppressErrorOutput;
  private final Map<ModuleLocation, GeneralError.Level> myModuleResults = new LinkedHashMap<>();

  private final static String SHOW_TIMES = "show-times";
  private final static String SHOW_SIZES = "show-sizes";
  private final static String SHOW_MODULES = "show-modules";
  private final static String SHOW_MODULES_WITH_INSTANCES = "show-modules-with-instances";

  /** The query tools, in help/dispatch order; iterated for registration, {@code --help} and dispatch. */
  public static final List<ConsoleQueryTool> QUERY_TOOLS = List.of(
          SymbolSearchTool.INSTANCE, ProofSearchTool.INSTANCE, FindUsagesTool.INSTANCE,
          ClassHierarchyTool.INSTANCE, ScopeInfoTool.INSTANCE);

  private final ErrorReporter mySystemErrErrorReporter = error -> {
    finishProgressLine();
    System.err.println(error);
    System.err.flush();
    myExitWithError = true;
  };

  private CommandLine parseArgs(String[] args) {
    if (hasRawFlag(args, "h", "help")) {
      for (ConsoleQueryTool tool : QUERY_TOOLS) {
        if (hasRawFlag(args, tool.shortName(), tool.longName())) { tool.printHelp(); return null; }
      }
      // else fall through: the general -h handler below prints the grouped help.
    }
    try {
      Options cmdOptions = new Options();
      cmdOptions.addOption("h", "help", false, "print this message");
      cmdOptions.addOption(Option.builder("L").longOpt("libdir").hasArg().argName("dir").desc("directory containing libraries").build());
      cmdOptions.addOption(Option.builder("s").longOpt("sources").hasArg().argName("dir").desc("project source directory").build());
      cmdOptions.addOption(Option.builder("e").longOpt("extensions").hasArg().argName("dir").desc("language extensions directory").build());
      cmdOptions.addOption(Option.builder("m").longOpt("extension-main").hasArg().argName("class").desc("main extension class").build());
      cmdOptions.addOption(Option.builder("c").longOpt("double-check").desc("double check correctness of the result").build());
      cmdOptions.addOption(Option.builder("i").longOpt("interactive").hasArg().optionalArg(true).argName("plain|jline").desc("start an interactive REPL").build());
      cmdOptions.addOption(Option.builder("p").longOpt("print").hasArg().argName("MODULE[:DEF]").desc("after the typecheck, print the elaborated/typechecked form of MODULE (or a single DEF inside it): implicits filled in, eliminators desugared.").build());
      // The query tools (-ss/-ps/-fu/-ch/-sc): registered from the shared registry so each flag's option identity lives on its ConsoleQueryTool.
      for (ConsoleQueryTool tool : QUERY_TOOLS) {
        cmdOptions.addOption(Option.builder(tool.shortName()).longOpt(tool.longName())
            .hasArgs().argName(tool.argName()).desc(tool.cliDescription()).build());
      }
      cmdOptions.addOption(Option.builder().longOpt("json").desc("with -ss/-ps/-fu/-sc/-ch: print results as a single JSON object on stdout ({results:[...],count:N}; -sc uses {target,entries:[...],count:N}; -ch uses {target,superclasses:[...],subclasses:[...],instances:[...],newSites:[...],counts}); all diagnostics ([INFO]/[WARN]/[ERROR], query echo) go to a log file, keeping stdout pure JSON and the console clean (ignored in REPL mode). For -fu, each usage is a separate entry (never grouped by row).").build());
      cmdOptions.addOption(Option.builder().longOpt("log-file").hasArg().argName("path").desc("with --json -ss/-ps/-fu/-sc/-ch: write diagnostics here instead of the default <tmpdir>/arend-symbol-search.log").build());
      cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
      cmdOptions.addOption(null, "serialize", false, "after typechecking, persist typechecked modules as .arc binary caches; without this flag, no .arc files are written");
      cmdOptions.addOption("t", "test", false, "run tests");
      cmdOptions.addOption("v", "version", false, "print language version");
      cmdOptions.addOption(Option.builder().longOpt(SHOW_TIMES).desc("after typechecking, print every definition's typecheck duration, sorted descending").build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_SIZES).desc("after typechecking, print every definition's typechecked core-term size (number of subterms), sorted descending").build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES).desc("after typechecking, print the module import-DAG in topological order via Tarjan SCC. Single modules: `[Module]`; import cycles: `[M1, M2, ...]`.").build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES_WITH_INSTANCES).desc("like --show-modules, but restricted to modules that define at least one \\instance -- useful for spotting cycles among instance-providing modules").build());
      CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

      if (cmdLine.hasOption("h")) {
        ConsoleHelp.printGrouped(cmdOptions, List.of(SHOW_TIMES, SHOW_SIZES, SHOW_MODULES, SHOW_MODULES_WITH_INSTANCES));
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

  /** True if {@code -<shortOpt>} or {@code --<longOpt>} appears as a raw arg token (stopping at a bare {@code --}). */
  private static boolean hasRawFlag(String[] args, String shortOpt, String longOpt) {
    String shortFlag = "-" + shortOpt, longFlag = "--" + longOpt;
    for (String arg : args) {
      if (arg.equals("--")) break;
      if (arg.equals(shortFlag) || arg.equals(longFlag)) return true;
    }
    return false;
  }

  /**
   * Parses {@code args} with {@code tool} and runs the resulting {@link ConsoleQueryTool.ConsoleToolRunner}
   * against {@code ctx}; returns {@code null} on parse failure (the tool has already printed the
   * diagnostic), else the tool's exit code.
   */
  private static Integer runQueryTool(ConsoleQueryTool tool, String[] args, ConsoleQueryTool.QueryContext ctx) {
    ConsoleQueryTool.ConsoleToolRunner parsed = tool.parseArgs(args);
    return parsed == null ? null : parsed.run(ctx);
  }

  private void updateSourceResult(ModuleLocation module, GeneralError.Level result) {
    if (module == null) return;
    GeneralError.Level prevResult = myModuleResults.get(module);
    if (prevResult == null || result.ordinal() > prevResult.ordinal()) {
      myModuleResults.put(module, result);
    }
  }

  private void reportTypeCheckResult(ModulePath modulePath, GeneralError.Level result) {
    System.out.println("[" + resultChar(result) + "]" + " " + modulePath);
  }

  private boolean myProgressActive = false;
  private int myLastProgressLength = 0;

  private void reportModuleProgress(int checked, int total, ModulePath modulePath) {
    String line = "[" + (checked + 1) + "/" + total + "] Typechecking " + modulePath;
    StringBuilder sb = new StringBuilder().append('\r').append(line);
    int pad = myLastProgressLength - line.length();
    if (pad > 0) {
      sb.repeat(" ", pad);
    }
    System.err.print(sb);
    System.err.flush();
    myLastProgressLength = line.length();
    myProgressActive = true;
  }

  private void finishProgressLine() {
    if (myProgressActive) {
      System.err.println();
      myProgressActive = false;
      myLastProgressLength = 0;
    }
  }

  private static char resultChar(GeneralError.Level result) {
    if (result == null) {
      return ' ';
    }
    return switch (result) {
      case GOAL -> '◯';
      case ERROR -> '✗';
      default -> '·';
    };
  }

  private final ErrorReporter myErrorReporter = new ErrorReporter() {
    @Override
    public void report(GeneralError error) {
      if (mySuppressErrorOutput) return;
      error.forAffectedDefinitions((referable, err) -> {
        if (referable instanceof LocatedReferable) {
          updateSourceResult(((LocatedReferable) referable).getLocation(), err.level);
        }
      });

      //Print error
      PrettyPrinterConfigWithRenamer ppConfig = new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
      if (error instanceof GoalError) {
        ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
      }
      if (error.level == GeneralError.Level.ERROR) {
        myExitWithError = true;
      }
      String errorText = error.getDoc(ppConfig).toString();

      finishProgressLine();
      if (error.isSevere()) {
        System.err.println(errorText);
        System.err.flush();
      } else {
        System.out.println(errorText);
        System.out.flush();
      }
    }
  };

  private void showSizes(ArendServer server, SourceLibrary library) {
    Map<Definition, Integer> sizes = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
          Definition definition = definitionData.definition().getData().getTypechecked();
          if (definition != null) {
            sizes.put(definition, SizeExpressionVisitor.getSize(definition));
          }
        }
      }
    }

    System.out.println();
    List<Pair<Definition,Integer>> list = new ArrayList<>(sizes.size());
    for (Map.Entry<Definition, Integer> entry : sizes.entrySet()) {
      list.add(new Pair<>(entry.getKey(), entry.getValue()));
    }
    list.sort((o1, o2) -> Long.compare(o2.proj2, o1.proj2));
    for (Pair<Definition, Integer> pair : list) {
      System.out.println(pair.proj1.getReferable().getRefLongName() + ": " + pair.proj2);
    }
  }

  private void printDefinitions(ArendServer server, String printString) {
    if (printString == null) return;

    Pair<ModulePath, LongName> pair = parseFullName(printString);
    if (pair != null) {
      ModuleLocation module = server.findModule(pair.proj1, null, false, false);
      if (module == null) {
        mySystemErrErrorReporter.report(new ModuleNotFoundError(pair.proj1));
      } else {
        boolean found = pair.proj2 == null;
        for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
          if (pair.proj2 == null || definitionData.definition().getData().getRefLongName().equals(pair.proj2)) {
            Definition definition = definitionData.definition().getData().getTypechecked();
            if (definition != null) {
              System.out.println();
              StringBuilder builder = new StringBuilder();
              ToAbstractVisitor.convert(definition, DEFAULT).prettyPrint(builder, DEFAULT);
              System.out.println(builder);
            }

            if (pair.proj2 != null) {
              found = true;
              break;
            }
          }
        }
        if (!found) {
          mySystemErrErrorReporter.report(new DefinitionNotFoundError(new FullName(module, pair.proj2)));
        }
      }
    }
  }

  private void showModules(ArendServer server, SourceLibrary library, boolean allModules) {
    Map<ModulePath, List<ModulePath>> map = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        ConcreteGroup group = server.getRawGroup(module);
        if (group == null) continue;
        boolean withInstances = allModules;
        List<ModulePath> dependencies = new ArrayList<>();
        for (ConcreteStatement statement : group.statements()) {
          ConcreteNamespaceCommand cmd = statement.command();
          if (cmd != null && cmd.isImport()) {
            dependencies.add(new ModulePath(cmd.module().getPath()));
          }
          if (!withInstances && !dependencies.isEmpty()) {
            ConcreteGroup subgroup = statement.group();
            if (subgroup != null && subgroup.referable().getKind() == GlobalReferable.Kind.INSTANCE) {
              withInstances = true;
            }
          }
        }
        if (withInstances) {
          map.put(module.getModulePath(), dependencies);
        }
      }
    }

    new MapTarjanSCC<>(map) {
      @Override
      protected void unitFound(ModulePath unit, boolean withLoops) {
        System.out.println("[" + unit + "]");
      }

      @Override
      protected void sccFound(List<ModulePath> scc) {
        System.out.println(scc);
      }
    }.order();
  }

  private Pair<ModulePath, LongName> parseFullName(String fullName) {
    ModulePath modulePath;
    LongName longName = null;
    int index = fullName.indexOf(':');
    if (index >= 0) {
      longName = LongName.fromString(fullName.substring(index + 1));
      if (!FileUtils.isCorrectDefinitionName(longName)) {
        mySystemErrErrorReporter.report(FileUtils.illegalDefinitionName(longName.toString()));
        return null;
      }
      fullName = fullName.substring(0, index);
    }
    modulePath = ModulePath.fromString(fullName);
    if (!FileUtils.isCorrectModulePath(modulePath)) {
      mySystemErrErrorReporter.report(FileUtils.illegalModuleName(modulePath.toString()));
      return null;
    }
    return new Pair<>(modulePath, longName);
  }

  private boolean run(String[] args) {
    CommandLine cmdLine = parseArgs(args);
    if (cmdLine == null) return false;

    boolean doubleCheck = cmdLine.hasOption("c");
    boolean recompile = cmdLine.hasOption("r");
    boolean serialize = cmdLine.hasOption("serialize");
    LibraryManager libraryManager = new LibraryManager(mySystemErrErrorReporter);
    CliServerRequester requester = new CliServerRequester(libraryManager);
    BinaryLoader binaryLoader = new BinaryLoader(libraryManager);
    if (recompile) {
      binaryLoader.setRecompile(true);
    }
    ArendServer server = new ArendServerImpl(requester, false, false, !doubleCheck);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> Objects.requireNonNull(new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE)));
    server.addErrorReporter(myErrorReporter);

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

    // Collect libraries and modules requested on the command line (shared by batch and REPL modes).
    Set<Pair<ModulePath, LongName>> requestedModules = new LinkedHashSet<>();
    List<SourceLibrary> requestedLibraries = new ArrayList<>();
    classifyRequestedTargets(cmdLine, libDirs, requestedLibraries, requestedModules);

    if (cmdLine.hasOption("i")) {
      // In REPL mode, positional arguments are libraries to add and modules to auto-load; the
      // batch-only options do not apply, so warn and ignore them rather than silently dropping them.
      warnUnsupportedReplOptions(cmdLine);
      List<ModulePath> autoloadModules = requestedModules.stream().map(pair -> pair.proj1).distinct().toList();
      String replKind = cmdLine.getOptionValue("i", "jline");
      switch (replKind.toLowerCase()) {
        case "plain":
          PlainCliRepl.launch(requestedLibraries, autoloadModules, libDirs, server);
          break;
        case "jline":
          JLineCliRepl.launch(requestedLibraries, autoloadModules, libDirs, server);
          break;
        default:
          System.err.println("[ERROR] Unrecognized repl type: " + replKind);
          return false;
      }
      // A failed argument only prints a diagnostic; the interactive prompt still starts.
      return true;
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

    // In `--json` query mode stdout must carry ONLY the JSON. Redirect both System.out and
    // System.err to a log file so every diagnostic (library-loading chatter, query echo,
    // [WARN]/[ERROR]) lands there; the JSON goes to the captured real stdout, and a pointer
    // line on real stderr says where the log went. Fall back to stderr if the file can't be
    // opened. `--json` without a query flag is a no-op.
    boolean anyQueryFlag = QUERY_TOOLS.stream().anyMatch(t -> cmdLine.hasOption(t.shortName()));
    boolean jsonSearch = cmdLine.hasOption("json") && anyQueryFlag;
    PrintStream realStdout = System.out;
    PrintStream realStderr = System.err;
    PrintStream jsonLog = null;
    Path jsonLogPath = null;
    if (jsonSearch) {
      jsonLogPath = resolveJsonLogPath(cmdLine);
      try {
        jsonLog = new PrintStream(Files.newOutputStream(jsonLogPath), true, StandardCharsets.UTF_8);
        System.setOut(jsonLog);
        System.setErr(jsonLog);
      } catch (IOException e) {
        realStderr.println("[WARN] cannot open -ss log file " + jsonLogPath + " (" + e.getMessage()
            + "); routing diagnostics to stderr instead");
        System.setOut(realStderr);
        jsonLog = null;
        jsonLogPath = null;
      }
    }

    // The load loops and the -ss/-ps/-fu/-sc/-ch blocks run inside this try so the
    // redirected streams are ALWAYS restored -- including the early `return false`
    // exits below, which the old code leaked past. The typecheck blocks after it
    // are reached only when none of those matched, hence never in JSON mode.
    try {
      Set<String> loading = new HashSet<>();
      for (SourceLibrary library : requestedLibraries) {
        if (!loadLibraryWithDependencies(library, libraryManager, libDirs, server, loading)) {
          return false;
        }
      }

      if (myExitWithError) {
        return false;
      }

      // Dispatch whichever query flag is present. Each tool parses its own sub-args and
      // runs against this shared context; each tool implements ConsoleQueryTool (its INSTANCE).
      ConsoleQueryTool.QueryContext queryCtx = new ConsoleQueryTool.QueryContext(
          requestedLibraries, libraryManager, server, mySystemErrErrorReporter, realStdout, jsonSearch, Set.of());
      for (ConsoleQueryTool tool : QUERY_TOOLS) {
        if (cmdLine.hasOption(tool.shortName())) {
          Integer code = runQueryTool(tool, cmdLine.getOptionValues(tool.shortName()), queryCtx);
          return code != null && code == 0 && !myExitWithError;
        }
      }
    } finally {
      if (jsonSearch) {
        System.setOut(realStdout);
        System.setErr(realStderr);
        if (jsonLog != null) {
          jsonLog.close();
          String cmd = QUERY_TOOLS.stream().filter(t -> cmdLine.hasOption(t.shortName()))
              .map(t -> "-" + t.shortName()).findFirst().orElse("-ss");
          realStderr.println("[INFO] " + cmd + " diagnostics written to " + jsonLogPath);
        }
      }
    }

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter = timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();

    // Pre-load binary caches (unless --recompile is set)
    if (!recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      Set<String> requestedLibraryNames = requestedLibraryNames(requestedLibraries);
      boolean resolvedRequestedScope = false;
      if (requestedModules.isEmpty()) {
        // Whole-library typechecking: resolve every module of each requested library.
        for (SourceLibrary library : requestedLibraries) {
          List<ModuleLocation> allModules = library.findModules(false).stream()
              .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
          if (!allModules.isEmpty()) {
            server.getCheckerFor(allModules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
            resolvedRequestedScope = true;
          }
        }
      } else {
        // Targeted typechecking: seed resolveAll with just the requested modules
        // so only their transitive import cone is raw-loaded. loadBinaryCache then
        // iterates server.getModules() — by now the cone — and only deserializes
        // ARCs in it, avoiding the cost of touching every cached file in a large
        // dependency library like arend-lib.
        List<ModuleLocation> targets = new ArrayList<>();
        for (Pair<ModulePath, LongName> requested : requestedModules) {
          ModuleLocation module = server.findModule(requested.proj1, null, true, false);
          if (module != null) targets.add(module);
        }
        if (!targets.isEmpty()) {
          server.getCheckerFor(targets).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
          resolvedRequestedScope = true;
        }
      }
      if (resolvedRequestedScope) {
        for (SourceLibrary library : dependencyFirstLibraries(libraryManager, requestedLibraries)) {
          binaryLoader.loadBinaryCache(library, server);
          if (!requestedLibraryNames.contains(library.getLibraryName())) {
            typecheckUncachedDependencyModules(server, binaryLoader, library);
          }
        }
      }
      // Report goals from definitions loaded from binary cache
      reportCachedGoals(server, binaryLoader.getBinaryCacheLoaded(), requestedLibraryNames);
      // Re-report errors that were detected on a previous typecheck pass and
      // whose modules are still in memory but won't be re-typechecked this run.
      reportInMemoryErrors(server, myErrorReporter, requestedLibraryNames);
    }

    if (requestedModules.isEmpty()) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Typechecking " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        List<ModulePath> modulesToTypecheck = library.findModules(false);
        int totalModules = modulesToTypecheck.size();
        int checkedModules = 0;
        for (ModulePath modulePath : modulesToTypecheck) {
          reportModuleProgress(checkedModules, totalModules, modulePath);
          server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath))).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
          checkedModules++;
        }
        finishProgressLine();

        time = System.currentTimeMillis() - time;

        // Output nice per-module typechecking results
        int numWithErrors = 0;
        int numWithGoals = 0;
        for (ModuleLocation module : server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
            GeneralError.Level result = myModuleResults.get(module);
            reportTypeCheckResult(module.getModulePath(), result);
            if (result == GeneralError.Level.ERROR) numWithErrors++;
            if (result == GeneralError.Level.GOAL) numWithGoals++;
          }
        }

        if (numWithErrors > 0) {
          myExitWithError = true;
          System.out.println("Number of modules with errors: " + numWithErrors);
        }
        if (numWithGoals > 0) {
          System.out.println("Number of modules with goals: " + numWithGoals);
        }
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (cmdLine.hasOption(SHOW_SIZES)) {
          showSizes(server, library);
        }

        if (cmdLine.hasOption(SHOW_MODULES)) {
          System.out.println();
          System.out.println("Modules cycles:");
          showModules(server, library, true);
        }

        if (cmdLine.hasOption(SHOW_MODULES_WITH_INSTANCES)) {
          System.out.println();
          System.out.println("Modules with instances cycles:");
          showModules(server, library, false);
        }

        if (doubleCheck && numWithErrors == 0) {
          System.out.println();
          System.out.println("--- Checking " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(myErrorReporter);
            for (ModuleLocation module : server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
                ConcreteGroup group = server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }

        if (serialize) {
          persistLibrary(library, server, binaryLoader.getBinaryCacheLoaded());
        }
      }
    } else {
      for (Pair<ModulePath, LongName> requested : requestedModules) {
        ModulePath modulePath = requested.proj1;
        LongName definitionName = requested.proj2;
        ModuleLocation module = server.findModule(modulePath, null, true, false);
        if (module == null) {
          mySystemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
        } else if (definitionName != null) {
          System.out.println();
          FullName fullName = new FullName(module, definitionName);
          System.out.println("--- Typechecking " + fullName + " ---");
          long time = System.currentTimeMillis();

          server.getCheckerFor(Collections.singletonList(module)).typecheck(Collections.singletonList(fullName), myErrorReporter, UnstoppableCancellationIndicator.INSTANCE, progressReporter);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
        } else {
          System.out.println();
          System.out.println("--- Typechecking " + module + " ---");
          long time = System.currentTimeMillis();

          server.getCheckerFor(Collections.singletonList(module)).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");

          if (doubleCheck) {
            System.out.println();
            System.out.println("--- Checking " + module + " ---");
            time = System.currentTimeMillis();

            try {
              CoreModuleChecker checker = new CoreModuleChecker(myErrorReporter);
              ConcreteGroup group = server.getRawGroup(module);
              if (group != null) {
                checker.checkGroup(group);
              }
            } finally {
              time = System.currentTimeMillis() - time;
              System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
            }
          }
        }
      }
      if (serialize) {
        // Persist all libraries that had modules typechecked
        for (SourceLibrary library : requestedLibraries) {
          persistLibrary(library, server, binaryLoader.getBinaryCacheLoaded());
        }
      }
    }

    printDefinitions(server, cmdLine.getOptionValue("p"));

    if (cmdLine.hasOption("t")) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Running tests in " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        List<ModulePath> testModulesToTypecheck = library.findModules(true);
        int totalTestModules = testModulesToTypecheck.size();
        int checkedTestModules = 0;
        for (ModulePath modulePath : testModulesToTypecheck) {
          reportModuleProgress(checkedTestModules, totalTestModules, modulePath);
          server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.TEST, modulePath))).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
          checkedTestModules++;
        }
        finishProgressLine();

        time = System.currentTimeMillis() - time;

        int[] total = new int[1];
        int[] failed = new int[1];
        for (ModuleLocation module : server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.TEST && module.getLibraryName().equals(library.getLibraryName())) {
            for (ConcreteStatement statement : Objects.requireNonNull(server.getRawGroup(module)).statements()) {
              if (statement.group() != null && statement.group().referable() instanceof TCDefReferable referable) {
                Definition definition = referable.getTypechecked();
                if (definition != null || referable.getKind().isTypecheckable()) {
                  total[0]++;
                  if (definition == null || definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                    failed[0]++;
                  }
                }
              }
            }
          }
        }

        System.out.println("Tests completed: " + total[0] + ", Failed: " + failed[0]);
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (doubleCheck) {
          System.out.println();
          System.out.println("--- Checking tests in " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(myErrorReporter);
            for (ModuleLocation module : server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.TEST && module.getLibraryName().equals(library.getLibraryName())) {
                ConcreteGroup group = server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }
      }
    }

    if (timedProgressReporter != null) {
      timedProgressReporter.print();
    }

    return true;
  }

  /**
   * Interprets the positional arguments and the {@code -s}/{@code -e}/{@code -m} options into the
   * libraries to load and modules to typecheck. Shared by batch mode and REPL ({@code -i}) mode so
   * the two never disagree on what an argument means.
   */
  private void classifyRequestedTargets(CommandLine cmdLine, List<Path> libDirs,
      List<SourceLibrary> requestedLibraries, Set<Pair<ModulePath, LongName>> requestedModules) {
    // Source and output directories
    String sourceDirStr = cmdLine.getOptionValue("s");
    Path sourceDir = sourceDirStr == null ? null : Paths.get(sourceDirStr);

    String binaryDirStr = cmdLine.getOptionValue("b");
    Path outDir = binaryDirStr != null ? Paths.get(binaryDirStr) : null;

    String extDirStr = cmdLine.getOptionValue("e");
    Path extDir = extDirStr != null ? Paths.get(extDirStr) : null;
    String extMainClass = cmdLine.getOptionValue("m");

    // Collect modules and libraries for which typechecking was requested
    Collection<String> argFiles = cmdLine.getArgList();
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
          mySystemErrErrorReporter.report(new LibraryIOError(fileName, "not a library"));
        }
      } else if (!findLibrary(fileName, libDirs, requestedLibraries)) {
        int colonIndex = fileName.indexOf(':');
        if (colonIndex >= 0) {
          Pair<ModulePath, LongName> parsed = parseFullName(fileName);
          if (parsed != null && parsed.proj2 != null) {
            requestedModules.add(parsed);
          } else if (parsed != null) {
            mySystemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR, "Definition name missing after ':' in " + fileName));
          }
        } else {
          ModulePath modulePath = ModulePath.fromString(fileName);
          if (FileUtils.isCorrectModulePath(modulePath)) {
            requestedModules.add(new Pair<>(modulePath, null));
          } else {
            mySystemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR, "File " + fileName + " not found"));
          }
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
          requestedLibraries.stream().map(SourceLibrary::getLibraryName).toList(), null, null, extMainClass, null,
          sourceDir, outDir, null, extDir == null ? null : new FileClassLoaderDelegate(extDir)));
    }
  }

  /** Options REPL ({@code -i}) mode acts on: the mode selector plus everything feeding library/module resolution. */
  private static final Set<String> REPL_CONSUMED_OPTIONS = Set.of("i", "L", "s", "e", "m");

  /**
   * Warns about every parsed option that REPL ({@code -i}) mode does not act on. Rather than track a
   * list of batch-only options, this ignores anything not in {@link #REPL_CONSUMED_OPTIONS}, so newly
   * added batch options are covered automatically.
   */
  private void warnUnsupportedReplOptions(CommandLine cmdLine) {
    for (Option option : cmdLine.getOptions()) {
      // Every consumed option has a short name, so a null short name is necessarily not consumed
      // (and Set.of rejects a null argument to contains).
      if (option.getOpt() != null && REPL_CONSUMED_OPTIONS.contains(option.getOpt())) continue;
      String name = option.getLongOpt() != null ? "--" + option.getLongOpt() : "-" + option.getOpt();
      System.out.println("[WARNING] Option " + name + " is not supported in REPL (-i) mode and will be ignored.");
    }
  }

  private void typecheckUncachedDependencyModules(ArendServer server, BinaryLoader binaryLoader, SourceLibrary library) {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())
          && !binaryLoader.getBinaryCacheLoaded().contains(module)) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) return;

    boolean oldSuppressErrorOutput = mySuppressErrorOutput;
    mySuppressErrorOutput = true;
    try {
      server.getCheckerFor(modules).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    } finally {
      mySuppressErrorOutput = oldSuppressErrorOutput;
    }
  }

  private static Set<String> requestedLibraryNames(List<SourceLibrary> requestedLibraries) {
    Set<String> result = new HashSet<>();
    for (SourceLibrary library : requestedLibraries) {
      result.add(library.getLibraryName());
    }
    return result;
  }

  private static List<SourceLibrary> dependencyFirstLibraries(LibraryManager libraryManager, List<SourceLibrary> requestedLibraries) {
    List<SourceLibrary> result = new ArrayList<>();
    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();
    for (SourceLibrary library : requestedLibraries) {
      collectDependencyFirst(libraryManager, library, visiting, visited, result);
    }
    return result;
  }

  private static void collectDependencyFirst(LibraryManager libraryManager, SourceLibrary library, Set<String> visiting, Set<String> visited, List<SourceLibrary> result) {
    String libraryName = library.getLibraryName();
    if (visited.contains(libraryName) || !visiting.add(libraryName)) return;

    for (String dependencyName : library.getLibraryDependencies()) {
      SourceLibrary dependency = libraryManager.getLibrary(dependencyName);
      if (dependency != null) {
        collectDependencyFirst(libraryManager, dependency, visiting, visited, result);
      }
    }

    visiting.remove(libraryName);
    visited.add(libraryName);
    result.add(library);
  }

  /**
   * Scans definitions loaded from binary cache for goals ({@code {?}}) and reports them.
   * The goal flag ({@code isGoal}) is preserved in .arc files, so we can detect goals
   * without re-typechecking.
   */
  private void reportCachedGoals(ArendServer server, Set<ModuleLocation> cachedModules, Set<String> requestedLibraryNames) {
    for (ModuleLocation module : cachedModules) {
      if (!requestedLibraryNames.contains(module.getLibraryName())) continue;
      ConcreteGroup group = server.getRawGroup(module);
      if (group == null) continue;
      reportGoalsInGroup(group, module);
    }
  }

  private void reportGoalsInGroup(ConcreteGroup group, ModuleLocation module) {
    LocatedReferable ref = group.referable();
    if (ref instanceof TCDefReferable tcRef) {
      Definition def = tcRef.getTypechecked();
      if (def != null && def.getGoals().contains(def)) {
        GeneralError goalError = new GeneralError(GeneralError.Level.GOAL, "Goal") {
          @Override
          public Object getCause() {
            return ref;
          }
        };
        myErrorReporter.report(goalError);
      }
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) {
        reportGoalsInGroup(statement.group(), module);
      }
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      reportGoalsInGroup(dynGroup, module);
    }
  }

  private void persistLibrary(SourceLibrary library, ArendServer server, Set<ModuleLocation> skipModules) {
    if (!library.supportsPersisting()) return;
    int persisted = 0;
    int skipped = 0;
    int failed = 0;
    int skippedWithErrors = 0;
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        if (skipModules.contains(module)) {
          skipped++;
          continue;
        }
        // Skip modules whose typechecked state contains any HAS_ERRORS def.  Persisting
        // them would write a cache that the next load can't use (the deserialized
        // module would still need re-typechecking from source) and, in a long-lived
        // daemon, accumulates orphan FunctionDefinitions pinned by cached expression
        // trees across the deserialize → clear → re-typecheck cycle.
        org.arend.term.group.ConcreteGroup group = server.getRawGroup(module);
        if (group != null && groupHasTypecheckingErrors(group)) {
          skippedWithErrors++;
          continue;
        }
        PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
        if (binarySource != null) {
          if (binarySource.persist(server, mySystemErrErrorReporter)) {
            persisted++;
          } else {
            failed++;
          }
        }
      }
    }
    if (persisted > 0 || failed > 0 || skippedWithErrors > 0) {
      System.out.println("[INFO] Persisted " + persisted + " module(s)"
          + (failed > 0 ? ", " + failed + " failed" : "")
          + (skippedWithErrors > 0 ? ", " + skippedWithErrors + " skipped (had errors)" : "")
          + (skipped > 0 ? " (" + skipped + " up-to-date)" : ""));
    }
  }

  /**
   * Returns true if any typecheckable definition reachable from {@code group} has
   * status {@link Definition.TypeCheckingStatus#HAS_ERRORS}. Used to gate persist:
   * caching a module that contains an erroneous def would only feed the
   * deserialize → orphan-shell-detected → clear → re-typecheck cycle in
   * {@code CliServerRequester.loadBinaryCache}.
   */
  static boolean groupHasTypecheckingErrors(org.arend.term.group.ConcreteGroup group) {
    if (group.referable() instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
      Definition def = tcRef.getTypechecked();
      if (def != null && def.status() == Definition.TypeCheckingStatus.HAS_ERRORS) return true;
    }
    for (org.arend.naming.reference.InternalReferable internalRef : group.getInternalReferables()) {
      if (internalRef instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
        Definition def = tcRef.getTypechecked();
        if (def != null && def.status() == Definition.TypeCheckingStatus.HAS_ERRORS) return true;
      }
    }
    for (org.arend.term.group.ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && groupHasTypecheckingErrors(statement.group())) return true;
    }
    for (org.arend.term.group.ConcreteGroup dynGroup : group.dynamicGroups()) {
      if (groupHasTypecheckingErrors(dynGroup)) return true;
    }
    return false;
  }

  /**
   * Re-reports typechecking errors stored in the server's {@code ErrorService} for
   * source modules in the given library. Without this step, a daemon that
   * bootstrapped with HAS_ERRORS modules would silently drop the error reports on
   * the second and later client requests: persist now skips those modules, load
   * doesn't see them in the binary cache, and the typechecker skips already-
   * typechecked defs — so the per-request {@code moduleResults} map never gets
   * an ERROR entry. Walking the ErrorService here restores the per-invocation
   * "Number of modules with errors" summary that the old re-typecheck cycle
   * incidentally provided.
   */
  private void reportInMemoryErrors(ArendServer server, ErrorReporter errorReporter, Set<String> requestedLibraryNames) {
    if (!(server instanceof org.arend.server.impl.ArendServerImpl impl)) return;
    org.arend.server.impl.ErrorService errorService = impl.getErrorService();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE || !requestedLibraryNames.contains(module.getLibraryName())) continue;
      for (GeneralError error : errorService.getTypecheckingErrors(module)) {
        errorReporter.report(error);
      }
    }
  }

  /**
   * The file that {@code --json -ss} writes its diagnostics to. Honours an
   * explicit {@code --log-file <path>}; otherwise defaults to
   * {@code <java.io.tmpdir>/arend-symbol-search.log}, overwritten each run.
   */
  private static Path resolveJsonLogPath(CommandLine cmdLine) {
    String custom = cmdLine.getOptionValue("log-file");
    if (custom != null && !custom.isEmpty()) return Paths.get(custom);
    return Paths.get(System.getProperty("java.io.tmpdir"), "arend-symbol-search.log");
  }

  private void loadLibrary(LibraryManager libraryManager, SourceLibrary library, ArendServer server) {
    System.out.println("[INFO] Loading " + library.getLibraryName());
    long time = System.currentTimeMillis();
    libraryManager.updateLibrary(library, server);
    System.out.println("[INFO] " + "Loaded " + library.getLibraryName() + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
  }

  /**
   * Loads {@code library} together with its transitive dependencies.
   *
   * @param loading   holds the libraries on the current dependency chain and.
   * @return false if a dependency cannot be found, in which case the dependent is not loaded either.
   */
  boolean loadLibraryWithDependencies(SourceLibrary library, LibraryManager libraryManager, List<Path> libDirs, ArendServer server, Set<String> loading) {
    if (libraryManager.containsLibrary(library.getLibraryName()) || !loading.add(library.getLibraryName())) return true;

    for (String dependency : library.getLibraryDependencies()) {
      if (libraryManager.containsLibrary(dependency) || loading.contains(dependency)) continue;
      List<SourceLibrary> libDependency = new ArrayList<>(1);
      findLibrary(dependency, libDirs, libDependency);
      if (libDependency.isEmpty()) return false;
      if (!loadLibraryWithDependencies(libDependency.getFirst(), libraryManager, libDirs, server, loading)) return false;
    }

    loadLibrary(libraryManager, library, server);
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
