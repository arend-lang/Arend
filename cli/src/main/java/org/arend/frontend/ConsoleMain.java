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
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.library.error.LibraryIOError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.ModuleLocation;
import org.arend.proof.ArendExpressionMatcher;
import org.arend.proof.ProofSearchQuery;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.util.Triple;
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

import org.arend.core.definition.Definition;
import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.TCDefReferable;
import org.arend.source.PersistableBinarySource;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.arend.ext.prettyprinting.PrettyPrinterConfig.DEFAULT;
import static org.arend.proof.Utils.getSignatures;

public class ConsoleMain {
  private boolean myExitWithError;
  private final Map<ModuleLocation, GeneralError.Level> myModuleResults = new LinkedHashMap<>();

  private final static String SHOW_TIMES = "show-times";
  private final static String SHOW_SIZES = "show-sizes";
  private final static String SHOW_MODULES = "show-modules";
  private final static String SHOW_MODULES_WITH_INSTANCES = "show-modules-with-instances";
  private final static String PRINT_FULL = "print-full";
  private final static String ANSI_GREEN = "\u001B[32m";
  private final static String ANSI_RESET = "\u001B[0m";

  private static class HighlightingPrettyPrintVisitor extends PrettyPrintVisitor {
    private final Set<Concrete.SourceNode> highlightedNodes;
    private int highlightCount = 0;

    public HighlightingPrettyPrintVisitor(StringBuilder builder, int indent, Set<Concrete.SourceNode> highlightedNodes) {
      super(builder, indent);
      this.highlightedNodes = highlightedNodes;
    }

    @Override
    protected PrettyPrintVisitor copy(StringBuilder builder, int indent, boolean doIndent) {
      return new HighlightingPrettyPrintVisitor(builder, indent, highlightedNodes);
    }

    @Override
    public void printExpr(Concrete.Expression expr, Precedence prec) {
      if (highlightedNodes.contains(expr)) {
        myBuilder.append(ANSI_GREEN);
        highlightCount++;
      }
      super.printExpr(expr, prec);
      if (highlightedNodes.contains(expr)) {
        highlightCount--;
        if (highlightCount == 0) {
          myBuilder.append(ANSI_RESET);
        }
      }
    }

    @Override
    public void prettyPrintParameter(Concrete.Parameter parameter) {
      if (highlightedNodes.contains(parameter)) {
        myBuilder.append(ANSI_GREEN);
        highlightCount++;
      }
      super.prettyPrintParameter(parameter);
      if (highlightedNodes.contains(parameter)) {
        highlightCount--;
        if (highlightCount == 0) {
          myBuilder.append(ANSI_RESET);
        }
      }
    }
  }

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
      cmdOptions.addOption(Option.builder("c").longOpt("double-check").desc("double check correctness of the result").build());
      cmdOptions.addOption(Option.builder("i").longOpt("interactive").hasArg().optionalArg(true).argName("type").desc("start an interactive REPL, type can be plain or jline (default)").build());
      cmdOptions.addOption(Option.builder("p").longOpt("print").hasArg().argName("target").desc("print a definition or a module").build());
      cmdOptions.addOption(Option.builder("ps").longOpt("proof-search").hasArgs().argName("pattern").desc("search for definitions matching the pattern").build());
      cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
      cmdOptions.addOption(null, "serialize", false, "after typechecking, persist typechecked modules as .arc binary caches; without this flag, no .arc files are written");
      cmdOptions.addOption("t", "test", false, "run tests");
      cmdOptions.addOption("v", "version", false, "print language version");
      cmdOptions.addOption(Option.builder().longOpt(SHOW_TIMES).build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_SIZES).build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES).build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES_WITH_INSTANCES).build());
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
    if (recompile) {
      requester.setRecompile(true);
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

    if (cmdLine.hasOption("i")) {
      String replKind = cmdLine.getOptionValue("i", "jline");
      switch (replKind.toLowerCase()) {
        case "plain":
          PlainCliRepl.launch(false, libDirs, server);
          break;
        case "jline":
          JLineCliRepl.launch(false, libDirs, server);
          break;
        default:
          System.err.println("[ERROR] Unrecognized repl type: " + replKind);
          return false;
      }
      return true;
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
    Set<Pair<ModulePath, LongName>> requestedModules = new LinkedHashSet<>();
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
      loadLibrary(libraryManager, library, server);
    }

    for (SourceLibrary library : requestedLibraries) {
      if (!loadDependencies(library, libraryManager, libDirs, server)) {
        return false;
      }
    }

    if (myExitWithError) {
      return false;
    }

    if (cmdLine.hasOption("ps")) {
      String[] psArgs = cmdLine.getOptionValues("ps");
      boolean printFull = false;
      List<String> patterns = new ArrayList<>();
      for (String arg : psArgs) {
        if (arg.equals(PRINT_FULL)) {
          printFull = true;
        } else {
          patterns.add(arg);
        }
      }
      if (patterns.isEmpty()) {
        System.err.println("[ERROR] Missing proof search pattern");
        return false;
      }
      if (patterns.size() > 1) {
        System.err.println("[ERROR] Only one proof search pattern is allowed. Use quotes if the pattern contains spaces.");
        return false;
      }
      return matchAndPrint(server, requestedLibraries, patterns.getFirst(), printFull);
    }

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter = timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();

    // Pre-load binary caches (unless --recompile is set)
    if (!recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      if (requestedModules.isEmpty()) {
        // Whole-library typechecking: pre-load every module of each requested library.
        for (SourceLibrary library : requestedLibraries) {
          List<ModuleLocation> allModules = library.findModules(false).stream()
              .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
          if (!allModules.isEmpty()) {
            // resolveAll forces raw loading of all modules and their transitive dependencies
            server.getCheckerFor(allModules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
            // Now load typechecked definitions from binary caches
            requester.loadBinaryCache(library, server);
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
          for (SourceLibrary library : requestedLibraries) {
            requester.loadBinaryCache(library, server);
          }
        }
      }
      // Report goals from definitions loaded from binary cache
      reportCachedGoals(server, requester.getBinaryCacheLoaded());
      // Re-report errors that were detected on a previous typecheck pass and
      // whose modules are still in memory but won't be re-typechecked this run.
      reportInMemoryErrors(server, myErrorReporter);
    }

    if (requestedModules.isEmpty()) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Typechecking " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(false)) {
          server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath))).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
        }

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
          persistLibrary(library, server, requester.getBinaryCacheLoaded());
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
          persistLibrary(library, server, requester.getBinaryCacheLoaded());
        }
      }
    }

    printDefinitions(server, cmdLine.getOptionValue("p"));

    if (cmdLine.hasOption("t")) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Running tests in " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(true)) {
          server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.TEST, modulePath))).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
        }

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
   * Scans definitions loaded from binary cache for goals ({@code {?}}) and reports them.
   * The goal flag ({@code isGoal}) is preserved in .arc files, so we can detect goals
   * without re-typechecking.
   */
  private void reportCachedGoals(ArendServer server, Set<ModuleLocation> cachedModules) {
    for (ModuleLocation module : cachedModules) {
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
  private void reportInMemoryErrors(ArendServer server, ErrorReporter errorReporter) {
    if (!(server instanceof org.arend.server.impl.ArendServerImpl impl)) return;
    org.arend.server.impl.ErrorService errorService = impl.getErrorService();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) continue;
      for (GeneralError error : errorService.getTypecheckingErrors(module)) {
        errorReporter.report(error);
      }
    }
  }

  private void loadLibrary(LibraryManager libraryManager, SourceLibrary library, ArendServer server) {
    System.out.println("[INFO] Loading " + library.getLibraryName());
    long time = System.currentTimeMillis();
    libraryManager.updateLibrary(library, server);
    System.out.println("[INFO] " + "Loaded " + library.getLibraryName() + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
  }

  private boolean loadDependencies(SourceLibrary library, LibraryManager libraryManager, List<Path> libDirs, ArendServer server) {
    for (String dependency : library.getLibraryDependencies()) {
      if (!libraryManager.containsLibrary(dependency)) {
        List<SourceLibrary> libDependency = new ArrayList<>(1);
        findLibrary(dependency, libDirs, libDependency);
        if (libDependency.isEmpty()) return false;
        loadLibrary(libraryManager, libDependency.getFirst(), server);
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

  private boolean matchAndPrint(ArendServer server, List<SourceLibrary> requestedLibraries, String pattern, boolean printFull) {
    ProofSearchQuery.ParsingResult<ProofSearchQuery> queryResult = ProofSearchQuery.fromString(pattern);
    if (queryResult == null) return false;
    if (queryResult instanceof ProofSearchQuery.ParsingResult.Error<ProofSearchQuery> error) {
      System.err.println("Search pattern error at " + error.range + ": " + error.message);
      return false;
    }
    ProofSearchQuery query = ((ProofSearchQuery.ParsingResult.OK<ProofSearchQuery>) queryResult).value;
    ArendExpressionMatcher matcher = new ArendExpressionMatcher(query);

    for (SourceLibrary library : requestedLibraries) {
      System.out.println("[INFO] Resolving " + library.getLibraryName());
      long time = System.currentTimeMillis();
      server.getCheckerFor(library.findModules(false).stream().map(modulePath -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath)).toList())
              .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      System.out.println("[INFO] " + "Resolved " + library.getLibraryName() + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
    }

    for (ModuleLocation moduleLocation : server.getModules()) {
      for (DefinitionData data : server.getResolvedDefinitions(moduleLocation)) {
        for (Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression> signature : getSignatures(data.definition())) {
          TCDefReferable referable = signature.first().getData();
          List<Concrete.Expression> parameters = signature.second();
          Concrete.Expression codomain = signature.third();

          Scope scope = server.getReferableScope(data.definition().getData());

          ArendExpressionMatcher.ProofSearchMatchingResult result = matcher.match(parameters, codomain, scope);
          if (result == null) continue;
          if (referable.getData() != null) {
            System.out.println(referable.getRefName() + " " + referable.getData().toString());
          } else {
            System.out.println(referable.getRefFullName().toString());
          }

          Set<Concrete.SourceNode> highlightedNodes = new HashSet<>(result.inCodomain());
          if (result.inPattern() != null) {
            for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
              highlightedNodes.addAll(parameterData.proj2);
            }
          }
          highlightedNodes.addAll(result.inCodomain());

          Precedence topPrec = new Precedence(Concrete.Expression.PREC);
          if (printFull) {
            StringBuilder builder = new StringBuilder();
            HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(builder, 0, highlightedNodes);
            data.definition().accept(visitor, null);
            System.out.println(builder);
          } else {
            if (result.inPattern() != null) {
              for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
                StringBuilder builder = new StringBuilder();
                HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(builder, 0, highlightedNodes);
                parameterData.proj1.prettyPrint(visitor, topPrec);
                System.out.print("(" + builder + ") -> ");
              }
            }
            StringBuilder builder = new StringBuilder();
            HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(builder, 0, highlightedNodes);
            codomain.prettyPrint(visitor, topPrec);
            System.out.println(builder);
          }
          System.out.println();
        }
      }
    }
    return true;
  }

  public static void main(String[] args) {
    ConsoleMain main = new ConsoleMain();
    if (!main.run(args) || main.myExitWithError) {
      System.exit(1);
    }
  }
}
