package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.core.definition.Definition;
import org.arend.core.expr.visitor.SizeExpressionVisitor;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.*;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.library.error.LibraryIOError;
import org.arend.module.FullName;
import org.arend.module.ModuleLocation;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ConsoleMain {
  private boolean myExitWithError;
  private final Map<ModuleLocation, GeneralError.Level> myModuleResults = new LinkedHashMap<>();

  private final static String SHOW_TIMES = "show-times";
  private final static String SHOW_SIZES = "show-sizes";
  private final static String SHOW_MODULES = "show-modules";
  private final static String SHOW_MODULES_WITH_INSTANCES = "show-modules-with-instances";

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
              ToAbstractVisitor.convert(definition, PrettyPrinterConfig.DEFAULT).prettyPrint(builder, PrettyPrinterConfig.DEFAULT);
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

    LibraryManager libraryManager = new LibraryManager(mySystemErrErrorReporter);
    ArendServer server = new ArendServerImpl(new CliServerRequester(libraryManager), false, false);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, Objects.requireNonNull(new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE)));
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
          PlainCliRepl.launch(false, libDirs);
          break;
        case "jline":
          JLineCliRepl.launch(false, libDirs);
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
          mySystemErrErrorReporter.report(new LibraryIOError(fileName, "not a library"));
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

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter = timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();

    boolean doubleCheck = cmdLine.hasOption("c");
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
      }
    } else {
      for (ModulePath modulePath : requestedModules) {
        ModuleLocation module = server.findModule(modulePath, null, true, false);
        if (module == null) {
          mySystemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
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

  public static void main(String[] args) {
    ConsoleMain main = new ConsoleMain();
    if (!main.run(args) || main.myExitWithError) {
      System.exit(1);
    }
  }
}
