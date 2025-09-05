package org.arend.frontend.repl;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.frontend.PositionComparator;
import org.arend.frontend.library.*;
import org.arend.frontend.parser.ArendLexer;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.parser.ReporterErrorListener;
import org.arend.frontend.repl.action.*;
import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
import org.arend.prelude.GeneratedVersion;
import org.arend.prelude.Prelude;
import org.arend.repl.Repl;
import org.arend.repl.action.ReplCommand;
import org.arend.server.ArendChecker;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.GroupData;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.ArendExtensionProvider;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.order.listener.TypecheckingOrderingListener;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.arend.typechecking.visitor.ArendCheckerFactory;
import org.arend.util.FileUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.arend.frontend.repl.action.LoadModuleCommand.ALL_MODULES;

public abstract class CommonCliRepl extends Repl {
  public static final @NotNull String APP_NAME = "Arend REPL";
  private final @NotNull EnumSet<@NotNull PrettyPrinterFlag> myPrettyPrinterFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
  private @Nullable NormalizationMode myNormalizationMode = NormalizationMode.ENF;

  public @NotNull Path pwd = Paths.get("").toAbsolutePath();
  /**
   * See https://gist.github.com/ice1000/a915b6fcbc6f90b0c3c65db44dab29cc
   */
  @Language("TEXT")
  public static final @NotNull String ASCII_BANNER =
      "    ___                       __\n" +
      "   /   |  _______  ____  ____/ /\n" +
      "  / /| | / __/ _ \\/ __ \\/ __  /  " + APP_NAME + " " + GeneratedVersion.VERSION_STRING + "\n" +
      " / ___ |/ / /  __/ / / / /_/ /   https://arend-lang.github.io\n" +
      "/_/  |_/_/  \\___/_/ /_/\\__,_/    :? for help";

  @NotNull
  protected String prompt = ">";
  private final SourceLibrary myReplLibrary;

  //region Tricky constructors (expand to read more...)
  // These two constructors are used for convincing javac that the
  // initialization is of well order.
  // All of the parameters introduced here are used more than once,
  // and one cannot introduce them as variable before the `this` or
  // `super` call because that's the rule of javac.
  private CommonCliRepl(
      @NotNull Set<ModulePath> modules,
      @NotNull ListErrorReporter errorReporter) {
    this(
        new ArendServerImpl(new CliServerRequester(new LibraryManager(errorReporter)), false, false),
        modules,
        errorReporter
    );
  }

  private CommonCliRepl(
      @NotNull ArendServer server,
      @NotNull Set<ModulePath> modules,
      @NotNull ListErrorReporter errorReporter) {
    super(
      errorReporter,
      server,
      new TypecheckingOrderingListener(
        ArendCheckerFactory.DEFAULT,
        server instanceof ArendServerImpl ? ((ArendServerImpl) server).getInstanceScopeProvider() : InstanceScopeProvider.EMPTY,
        Collections.emptyMap(),
        new SimpleConcreteProvider(Collections.emptyMap()),
        errorReporter,
        PositionComparator.INSTANCE,
        server instanceof ArendServerImpl ? ((ArendServerImpl) server).getExtensionProvider() : new ArendExtensionProvider() {}
      )
    );
    Path configFile = pwd.resolve(FileUtils.LIBRARY_CONFIG_FILE);
    myReplLibrary = Files.exists(configFile) ? FileSourceLibrary.fromConfigFile(configFile, false, errorReporter)
      : new FileSourceLibrary("Repl", true, -1, new ArrayList<>(), null, null, modules, pwd, null, null, null);

    try {
      if (Files.exists(config)) {
        var properties = new YAMLMapper().readValue(config.toFile(), ReplConfig.class);
        myNormalizationMode = properties.normalizationMode;
        if (properties.prompt != null) prompt = properties.prompt;
        myPrettyPrinterFlags.clear();
        myPrettyPrinterFlags.addAll(properties.prettyPrinterFlags);
      }
    } catch (IOException e) {
      errorReporter.report(new GeneralError(GeneralError.Level.ERROR, "Failed to load repl config: " + e.getLocalizedMessage()));
    }
  }
  //endregion

  public static final @NotNull String REPL_CONFIG_FILE = "repl-config.yaml";
  private static final Path config = FileUtils.USER_HOME.resolve(FileUtils.USER_CONFIG_DIR).resolve(REPL_CONFIG_FILE);

  @Override
  public @NotNull EnumSet<PrettyPrinterFlag> getPrettyPrinterFlags() {
    return myPrettyPrinterFlags;
  }

  @Override
  public @Nullable NormalizationMode getNormalizationMode() {
    return myNormalizationMode;
  }

  @Override
  public void setNormalizationMode(@Nullable NormalizationMode mode) {
    myNormalizationMode = mode;
  }

  public final void saveUserConfig() {
    try {
      if (Files.notExists(config)) {
        Files.createFile(config);
      }
      var properties = new ReplConfig();
      properties.normalizationMode = myNormalizationMode;
      properties.prompt = prompt;
      properties.prettyPrinterFlags = new ArrayList<>(myPrettyPrinterFlags);
      try (var out = Files.newOutputStream(config)) {
        new YAMLMapper().writeValue(out, properties);
      }
    } catch (IOException e) {
      eprintln("[ERROR] Failed to save repl config: " + e.getLocalizedMessage());
    }
  }

  private @NotNull BuildVisitor buildVisitor() {
    return new BuildVisitor(Repl.replModulePath, errorReporter);
  }

  public static @NotNull ArendParser createParser(@NotNull String text, @NotNull ModuleLocation moduleLocation, @NotNull ErrorReporter reporter) {
    var errorListener = new ReporterErrorListener(reporter, moduleLocation);
    var parser = new ArendParser(
        new CommonTokenStream(createLexer(text, errorListener)));
    parser.removeErrorListeners();
    parser.addErrorListener(errorListener);
    // parser.addErrorListener(new DiagnosticErrorListener());
    // parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
    return parser;
  }

  public static @NotNull ArendLexer createLexer(@NotNull String text, BaseErrorListener errorListener) {
    var input = CharStreams.fromString(text);
    var lexer = new ArendLexer(input);
    lexer.removeErrorListeners();
    lexer.addErrorListener(errorListener);
    return lexer;
  }

  private @NotNull ArendParser parse(String line) {
    return createParser(line, Repl.replModulePath, errorReporter);
  }

  @Override
  public @NotNull String prompt() {
    return prompt;
  }

  @Override
  protected void loadCommands() {
    super.loadCommands();
    registerAction("modules", ListLoadedModulesAction.INSTANCE);
    registerAction("unload", UnloadModuleCommand.INSTANCE);
    registerAction("load", LoadModuleCommand.INSTANCE);
    registerAction("reload", LoadModuleCommand.ReloadModuleCommand.INSTANCE);
    registerAction("prompt", new ChangePromptCommand());
    registerAction("lib", LoadLibraryCommand.INSTANCE);
    registerAction("unlib", UnloadLibraryCommand.INSTANCE);
    registerAction("pwd", new PwdCommand());
    registerAction("cd", new CdCommand());
  }

  public @Nullable SourceLibrary createLibrary(@NotNull String libraryName) {
    Path configFile = ((myReplLibrary.getLibraryName().equals(libraryName))
      ? pwd
      : pwd.resolve(libraryName)
    ).resolve(FileUtils.LIBRARY_CONFIG_FILE);
    return Files.exists(configFile) ? FileSourceLibrary.fromConfigFile(configFile, false, errorReporter) : null;
  }

  @Override
  protected void loadPotentialUnloadedModules(Collection<? extends ConcreteStatement> statements) {
    List<ModulePath> modules = new ArrayList<>();
    for (ConcreteStatement statement : statements) {
      ConcreteNamespaceCommand command = statement.command();
      if (command != null && command.isImport()) {
        var module = new ModulePath(command.module().getPath());
        modules.add(module);
      }
    }
    loadModules(modules);
  }

  @Override
  protected final @Nullable ConcreteGroup parseStatements(@NotNull String line) {
    ConcreteGroup fileGroup = buildVisitor().visitStatements(parse(line).statements());
    if (checkErrors()) return null;
    return fileGroup;
  }

  @Override
  protected final @Nullable Concrete.Expression parseExpr(@NotNull String text) {
    return buildVisitor().visitExpr(parse(text).expr());
  }

  public CommonCliRepl() {
    this(new TreeSet<>(), new ListErrorReporter(new ArrayList<>()));
  }

  private List<ModuleLocation> getLocationsByModulePath(ModulePath modulePath) {
    List<ModuleLocation> moduleLocations = new ArrayList<>();
    for (String libraryName : getLibraries()) {
      SourceLibrary library = createLibrary(libraryName);
      if (library != null && library.findModules(false).contains(modulePath)) {
        ModuleLocation moduleLocation = new ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, modulePath);
        moduleLocations.add(moduleLocation);
      }
    }
    return moduleLocations;
  }

  private List<ModuleLocation> getLoadedLocationsByModulePath(ModulePath modulePath) {
    List<ModuleLocation> moduleLocations = new ArrayList<>();
    for (String libraryName : getLibraries()) {
      ModuleLocation moduleLocation = myServer.findModule(modulePath, libraryName, false, false);
      if (moduleLocation != null) {
        moduleLocations.add(moduleLocation);
      }
    }
    return moduleLocations;
  }

  public final boolean loadLibrary(@NotNull SourceLibrary library) {
    myServer.updateLibrary(library, errorReporter);
    if (myServer instanceof ArendServerImpl && ((ArendServerImpl) myServer).getRequester() instanceof CliServerRequester cliServerRequester) {
      cliServerRequester.getLibraryManager().updateLibrary(library, myServer);
    }
    return true;
  }

  public final boolean unloadLibrary(@NotNull String libraryName) {
    myServer.removeLibrary(libraryName);
    SourceLibrary library = createLibrary(libraryName);
    if (library == null) {
      return false;
    }
    List<ModulePath> modulePaths = library.findModules(false);
    for (ModulePath modulePath : modulePaths) {
      ModuleLocation moduleLocation = myServer.findModule(modulePath, libraryName, false, false);
      if (moduleLocation != null) {
        myServer.removeModule(moduleLocation);
      }
    }
    if (myServer instanceof ArendServerImpl && ((ArendServerImpl) myServer).getRequester() instanceof CliServerRequester cliServerRequester) {
      cliServerRequester.getLibraryManager().removeLibrary(libraryName, myServer);
    }
    return true;
  }

  @Override
  protected final void loadLibraries() {
    if (myServer instanceof ArendServerImpl) {
      if (!Prelude.isInitialized()) {
        ConcreteGroup preludeGroup = new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE);
        if (preludeGroup != null) {
          myServer.addReadOnlyModule(Prelude.MODULE_LOCATION, preludeGroup);
        }
        myServer.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION)).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      }
      GroupData preludeData = ((ArendServerImpl) myServer).getGroupData(Prelude.MODULE_LOCATION);
      if (preludeData != null) {
        myReplScope.addPreludeScope(preludeData.getFileScope());
      }
    }
    myServer.updateLibrary(myReplLibrary, errorReporter);
    if (myServer instanceof ArendServerImpl && ((ArendServerImpl) myServer).getRequester() instanceof CliServerRequester cliServerRequester) {
      cliServerRequester.getLibraryManager().updateLibrary(myReplLibrary, myServer);
    }
  }

  /**
   * Load a file under the REPL working directory and get its scope.
   * This will <strong>not</strong> modify the REPL scope.
   */
  public final @Nullable Scope loadModule(@NotNull ModulePath modulePath) {
    if (Objects.equals(modulePath.toString(), ALL_MODULES)) {
      Set<ModulePath> allModules = getAllModules();
      ArendChecker arendChecker = myServer.getCheckerFor(allModules.stream().map(this::getLocationsByModulePath).flatMap(List::stream).toList());
      arendChecker.typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      allModules.stream().map(newModulePath -> getAvailableModuleScopeProvider().forModule(newModulePath)).filter(Objects::nonNull).forEach(this::addScope);
      return EmptyScope.INSTANCE;
    }
    ArendChecker arendChecker = myServer.getCheckerFor(getLocationsByModulePath(modulePath));
    arendChecker.typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    return getAvailableModuleScopeProvider().forModule(modulePath);
  }

  public final void loadModules(Collection<@NotNull ModulePath> modulePaths) {
    ArendChecker arendChecker = myServer.getCheckerFor(modulePaths.stream().map(this::getLocationsByModulePath).flatMap(List::stream).toList());
    arendChecker.typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  /**
   * Like {@link CommonCliRepl#loadModule(ModulePath)}, this will
   * <strong>not</strong> modify the REPL scope as well.
   *
   * @return true if the module is already loaded before.
   */
  public final void unloadModule(@NotNull ModulePath modulePath) {
    if (Objects.equals(modulePath.toString(), ALL_MODULES)) {
      Collection<? extends ModuleLocation> moduleLocations = myServer.getModules();
      for (ModuleLocation moduleLocation : moduleLocations) {
        myServer.removeModule(moduleLocation);
      }
    } else {
      for (ModuleLocation moduleLocation : getLoadedLocationsByModulePath(modulePath)) {
        myServer.removeModule(moduleLocation);
      }
    }
  }

  public @NotNull Set<ModulePath> getLoadedModules() {
    return getLoadedModuleLocations().stream().map(ModuleLocation::getModulePath).collect(Collectors.toSet());
  }

  public @NotNull Set<ModuleLocation> getLoadedModuleLocations() {
    return new HashSet<>(myServer.getModules());
  }

  public @NotNull Set<ModulePath> getAllModules() {
    Set<ModulePath> result = new HashSet<>();
    for (String libraryName : getLibraries()) {
      SourceLibrary library = createLibrary(libraryName);
      if (library != null) {
        result.addAll(library.findModules(false));
      } else if (libraryName.equals(Prelude.LIBRARY_NAME)) {
        result.add(Prelude.MODULE_PATH);
      }
    }
    return result;
  }

  public @NotNull Set<String> getLibraries() {
    return myServer.getLibraries();
  }

  public List<GeneralError> getErrorList(ModuleLocation moduleLocation) {
    return myServer.getErrorMap().get(moduleLocation);
  }

  private final class ChangePromptCommand implements ReplCommand {
    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "Change REPL prompt (current prompt: '" + prompt + "')";
    }

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
      boolean start = line.startsWith("\"");
      boolean end = line.endsWith("\"");
      // Maybe we should unescape this string?
      if (start && end) prompt = line.substring(1, line.length() - 1);
      else if (!start && !end) prompt = line;
      else eprintln("[ERROR] Bad prompt format");
    }
  }
}
