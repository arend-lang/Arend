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
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.typechecking.visitor.ArendCheckerFactory;
import org.arend.util.FileUtils;
import org.arend.util.SingletonList;
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
  private final Set<ModulePath> myModules;

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
      new TypecheckingOrderingListener(ArendCheckerFactory.DEFAULT, InstanceScopeProvider.EMPTY /* TODO[server2] */, Collections.emptyMap(), ConcreteProvider.EMPTY /* TODO[server2] */, errorReporter, PositionComparator.INSTANCE, new ArendExtensionProvider() {} /* TODO[server2] */)
    );
    Path configFile = pwd.resolve(FileUtils.LIBRARY_CONFIG_FILE);
    myReplLibrary = Files.exists(configFile) ? FileSourceLibrary.fromConfigFile(configFile, false, errorReporter)
      : new FileSourceLibrary("Repl", true, -1, new ArrayList<>(), null, null, modules, pwd, null, null, null);
    myModules = modules;

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
    registerAction("pwd", new PwdCommand());
    registerAction("cd", new CdCommand());
  }

  /* TODO[server2]
  public @Nullable Library createLibrary(@NotNull String path) {
    var library = myLibraryResolver.resolve(myReplLibrary, path);
    if (library != null) return library;
    return myLibraryResolver.registerLibrary(pwd.resolve(path).toAbsolutePath().normalize());
    return null;
  }
  */

  public final void addLibraryDirectories(@NotNull Collection<? extends Path> libDirs) {
    // TODO[server2]: myLibraryResolver.addLibraryDirectories(libDirs);
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

  private ModuleLocation getLocationByModulePath(ModulePath modulePath) {
    ModuleLocation moduleLocation = myServer.findModule(modulePath, myReplLibrary.getLibraryName(), false, false);
    return moduleLocation != null ? moduleLocation : new ModuleLocation(myReplLibrary.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath);
  }

  public final boolean loadLibrary(@NotNull SourceLibrary library) {
    myServer.updateLibrary(library, errorReporter);
    if (myServer instanceof ArendServerImpl && ((ArendServerImpl) myServer).getRequester() instanceof CliServerRequester cliServerRequester) {
      cliServerRequester.getLibraryManager().updateLibrary(myReplLibrary, myServer);
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
    myModules.add(modulePath);
    ArendChecker arendChecker = myServer.getCheckerFor(new SingletonList<>(getLocationByModulePath(modulePath)));
    arendChecker.typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    return myServer.getModuleScopeProvider(myReplLibrary.getLibraryName(), false).forModule(modulePath);
  }

  public final void loadModules(Collection<@NotNull ModulePath> modulePaths) {
    myModules.addAll(modulePaths);
    ArendChecker arendChecker = myServer.getCheckerFor(modulePaths.stream().map(this::getLocationByModulePath).toList());
    arendChecker.typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  /**
   * Like {@link CommonCliRepl#loadModule(ModulePath)}, this will
   * <strong>not</strong> modify the REPL scope as well.
   *
   * @return true if the module is already loaded before.
   */
  public final boolean unloadModule(@NotNull ModulePath modulePath) {
    boolean isLoadedBefore = myModules.remove(modulePath);
    if (isLoadedBefore) {
      myServer.removeModule(getLocationByModulePath(modulePath));
    }
    return isLoadedBefore;
  }

  private final class ChangePromptCommand implements ReplCommand {
    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "Change the repl prompt (current prompt: '" + prompt + "')";
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
