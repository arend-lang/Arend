package org.arend.frontend.repl;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.query.ConsoleQueryTool;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.frontend.library.*;
import org.arend.frontend.parser.ArendLexer;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.parser.ReporterErrorListener;
import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.prelude.GeneratedVersion;
import org.arend.prelude.Prelude;
import org.arend.repl.Repl;
import org.arend.repl.action.ReplCommand;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.server.DelegateServerRequester;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.util.FileUtils;
import org.arend.util.SingletonList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

import static org.arend.repl.action.LoadLibraryCommand.CUR_DIR;

public abstract class CommonCliRepl extends Repl {
  public static final @NotNull String APP_NAME = "Arend REPL";
  private final @NotNull EnumSet<@NotNull PrettyPrinterFlag> myPrettyPrinterFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
  private @Nullable NormalizationMode myNormalizationMode = NormalizationMode.ENF;

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
  private FileSourceLibrary myReplLibrary;
  private final Map<String, SourceLibrary> myReplLibraries = new HashMap<>();
  /** Library search path from the {@code -L} option, used to resolve libraries referenced by name. */
  private final @NotNull List<Path> myLibDirs;

  //region Tricky constructors (expand to read more...)
  // These two constructors are used for convincing javac that the
  // initialization is of well order.
  // All of the parameters introduced here are used more than once,
  // and one cannot introduce them as variable before the `this` or
  // `super` call because that's the rule of javac.
  public CommonCliRepl(@NotNull ArendServer server) {
    this(server, Collections.emptyList());
  }

  public CommonCliRepl(@NotNull ArendServer server, @NotNull Collection<? extends Path> libDirs) {
    this(server, libDirs, new ListErrorReporter(new ArrayList<>()));
  }

  private CommonCliRepl(
      @NotNull ArendServer server,
      @NotNull Collection<? extends Path> libDirs,
      @NotNull ListErrorReporter errorReporter) {
    super(
      errorReporter,
      server
    );
    myLibDirs = List.copyOf(libDirs);
    Path configFile = pwd.resolve(FileUtils.LIBRARY_CONFIG_FILE);
    myReplLibrary = getNewFileSourceLibrary();
    // Only auto-load the current directory as a project when it actually is one; otherwise starting
    // the REPL from a directory without an arend.yaml would report a spurious read error.
    if (Files.isRegularFile(configFile)) {
      FileSourceLibrary sourceLibrary = FileSourceLibrary.fromConfigFile(configFile, false, errorReporter);
      if (sourceLibrary != null) {
        loadLibrary(sourceLibrary);
      }
    }
    try {
      if (Files.exists(config)) {
        var properties = new YAMLMapper().readValue(config.toFile(), ReplConfig.class);
        myNormalizationMode = properties.normalizationMode;
        if (properties.prompt != null) prompt = properties.prompt;
        myPrettyPrinterFlags.clear();
        myPrettyPrinterFlags.addAll(properties.prettyPrinterFlags);
      }
    } catch (IOException e) {
      errorReporter.report(new GeneralError(GeneralError.Level.WARNING, "Failed to load repl config: " + e.getLocalizedMessage()));
    }
  }
  //endregion

  private FileSourceLibrary getNewFileSourceLibrary() {
    return new FileSourceLibrary(REPL_NAME, true, -1, myServer.getLibraries().stream().filter(library -> !Objects.equals(library, REPL_NAME)).toList(), null, null, null, null, pwd, null, null, null);
  }

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
    return new BuildVisitor(Repl.replModuleLocation, errorReporter);
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
    return createParser(line, Repl.replModuleLocation, errorReporter);
  }

  @Override
  public @NotNull String prompt() {
    return prompt;
  }

  @Override
  protected void loadCommands() {
    super.loadCommands();
    registerAction("prompt", new ChangePromptCommand());
    // Each query tool is its own REPL command: ConsoleQueryTool extends AliasableCommand
    // and each tool supplies its own invoke/description/help, so registration is a single
    // loop over the shared registry -- the names live on the tools, not restated here.
    for (ConsoleQueryTool tool : ConsoleMain.QUERY_TOOLS) {
      // Idempotent across REPL instances: aliases is display-only, and the command map is
      // a static singleton, so without this the same names would accumulate on re-load.
      tool.aliases.clear();
      registerAction(tool.longName(), tool);
      registerAction(tool.shortName(), tool);
    }
  }

  @Override
  protected boolean checkPotentialUnloadedModules(Collection<? extends ConcreteStatement> statements) {
    List<ModulePath> modules = new ArrayList<>();
    for (ConcreteStatement statement : statements) {
      ConcreteNamespaceCommand command = statement.command();
      if (command != null && command.isImport()) {
        var module = new ModulePath(command.module().getPath());
        modules.add(module);
      }
    }
    return getLoadedModules().containsAll(modules);
  }

  @Override
  public final @Nullable ConcreteGroup parseStatements(@NotNull String line) {
    ConcreteGroup fileGroup = buildVisitor().visitStatements(parse(line).statements());
    if (checkErrors()) return null;
    return fileGroup;
  }

  @Override
  protected final @Nullable Concrete.Expression parseExpr(@NotNull String text) {
    return buildVisitor().visitExpr(parse(text).expr());
  }

  @Override
  public @Nullable SourceLibrary createLibrary(@NotNull String libraryName) {
    if (myReplLibraries.containsKey(libraryName)) {
      return myReplLibraries.get(libraryName);
    }
    Path configFile = (pwd.endsWith(libraryName) || libraryName.equals(CUR_DIR)
            ? pwd
            : pwd.resolve(libraryName)
    ).resolve(FileUtils.LIBRARY_CONFIG_FILE);
    if (Files.exists(configFile)) {
      SourceLibrary sourceLibrary = FileSourceLibrary.fromConfigFile(configFile, false, errorReporter);
      myReplLibraries.put(libraryName, sourceLibrary);
      return sourceLibrary;
    }
    // Fall back to the library search path (-L) for named libraries not found under pwd.
    for (Path libDir : myLibDirs) {
      Path candidate = libDir.resolve(libraryName).resolve(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.exists(candidate)) {
        SourceLibrary sourceLibrary = FileSourceLibrary.fromConfigFile(candidate, false, errorReporter);
        myReplLibraries.put(libraryName, sourceLibrary);
        return sourceLibrary;
      }
    }
    return null;
  }

  /**
   * Loads the libraries requested on the command line together with their declared dependencies, then
   * for each requested module both loads it (making its definitions available, as {@code :load} does)
   * and imports it (bringing its names into scope, as {@code :import} does). Errors are reported but
   * never abort startup — the user can inspect the result or run {@code :reset_context} afterwards.
   */
  public void loadStartupTargets(@NotNull Collection<? extends ArendLibrary> libraries, @NotNull Collection<? extends ModulePath> modules) {
    Set<String> loaded = new HashSet<>();
    for (ArendLibrary library : libraries) {
      loadLibraryWithDependencies(library, loaded);
    }
    for (ModulePath module : modules) {
      loadModule(module);
      checkStatements("\\import " + module);
      checkErrors();
    }
  }

  private void loadLibraryWithDependencies(@NotNull ArendLibrary library, @NotNull Set<String> loaded) {
    if (!loaded.add(library.getLibraryName())) return;
    for (String dependency : library.getLibraryDependencies()) {
      if (loaded.contains(dependency) || myServer.getLibrary(dependency) != null) continue;
      ArendLibrary dependencyLibrary = createLibrary(dependency);
      if (dependencyLibrary != null) {
        loadLibraryWithDependencies(dependencyLibrary, loaded);
      } else {
        eprintln("[ERROR] Cannot find dependency library '" + dependency + "' required by '" + library.getLibraryName() + "'.");
      }
    }
    loadLibrary(library);
    checkErrors();
  }

  @Override
  public final void loadLibrary(@NotNull ArendLibrary library) {
    super.loadLibrary(library);
    if (myServer instanceof ArendServerImpl arendServer && arendServer.getRequester() instanceof DelegateServerRequester delegateServerRequester && delegateServerRequester.requester instanceof CliServerRequester cliServerRequester && library instanceof SourceLibrary sourceLibrary) {
      cliServerRequester.getLibraryManager().updateLibrary(sourceLibrary, myServer);
      myReplLibraries.put(library.getLibraryName(), sourceLibrary);
    }
    ConcreteGroup replGroup = myServer.getRawGroup(replModuleLocation);
    myReplLibrary = getNewFileSourceLibrary();
    myReplLibraries.put(REPL_NAME, myReplLibrary);
    myServer.updateLibrary(myReplLibrary, errorReporter);
    if (replGroup != null) {
      updateReplModule(replGroup, true);
    }
  }

  @Override
  public final void unloadLibrary(@NotNull String libraryName) {
    super.unloadLibrary(libraryName);
    myServer.getModules().stream().filter(moduleLocation -> moduleLocation.getLibraryName().equals(libraryName)).forEach(myServer::removeModule);
    if (myServer instanceof ArendServerImpl arendServer && arendServer.getRequester() instanceof DelegateServerRequester delegateServerRequester && delegateServerRequester.requester instanceof CliServerRequester cliServerRequester) {
      cliServerRequester.getLibraryManager().removeLibrary(libraryName, myServer);
    }
    myReplLibraries.remove(libraryName);

    ConcreteGroup replGroup = myServer.getRawGroup(replModuleLocation);
    myReplLibrary = getNewFileSourceLibrary();
    myReplLibraries.put(REPL_NAME, myReplLibrary);
    myServer.updateLibrary(myReplLibrary, errorReporter);
    if (replGroup != null) {
      removeNotLoadedStatements(replGroup, false);
      updateReplModule(replGroup, true);
    }
  }

  @Override
  protected final void loadLibraries() {
    if (myServer.getRawGroup(Prelude.MODULE_LOCATION) != null) {
      ConcreteGroup preludeGroup = new PreludeResourceSource().loadGroup(errorReporter);
      if (preludeGroup != null) {
        myServer.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> preludeGroup);
        typecheckModules(new SingletonList<>(Prelude.MODULE_LOCATION));
        Prelude.initialize(preludeGroup);
      }
    }
    if (myReplLibrary != null) {
      myServer.updateLibrary(myReplLibrary, errorReporter);
      if (myServer instanceof ArendServerImpl arendServer && arendServer.getRequester() instanceof DelegateServerRequester delegateServerRequester && delegateServerRequester.requester instanceof CliServerRequester cliServerRequester) {
        cliServerRequester.getLibraryManager().updateLibrary(myReplLibrary, myServer);
      }
      updateReplModule(new ConcreteGroup(DocFactory.nullDoc(), new FullModuleReferable(replModuleLocation), null, new ArrayList<>(), Collections.emptyList(), Collections.emptyList()), true);
      myReplLibraries.put(REPL_NAME, myReplLibrary);
    }
  }

  @Override
  public @NotNull Set<ModulePath> getAllModules() {
    Set<ModulePath> result = new HashSet<>();
    for (String libraryName : getLibraries()) {
      SourceLibrary library = createLibrary(libraryName);
      if (library != null && !libraryName.equals(REPL_NAME)) {
        result.addAll(library.findModules(false));
      }
    }
    myServer.getModules().stream().filter(module -> module.getLocationKind() == ModuleLocation.LocationKind.GENERATED).map(ModuleLocation::getModulePath).forEach(result::add);
    return result;
  }

  /**
   * Body of a REPL search command: receives the resolved library manager, the
   * search scope (every registered library except the synthetic {@code Repl}
   * mirror), and the capture stream the tool should write to.
   */
  @FunctionalInterface
  public interface SearchInvocation {
    void run(@NotNull LibraryManager manager, @NotNull List<SourceLibrary> libs, @NotNull PrintStream capture);
  }

  /**
   * Shared scaffolding for the {@code :ss}/{@code :ps}/{@code :fu}/{@code :ch}/{@code :sc}
   * handlers. Resolves the library manager (printing {@code unavailableMsg} and bailing
   * if absent), builds the search scope (dropping the synthetic {@code Repl} mirror,
   * whose duplicate source files would otherwise double hits / make bare-name resolution
   * ambiguous), then captures everything the tool writes to {@code System.out}/{@code
   * System.err} and forwards it through the REPL's own stream — so it works for both the
   * plain and jline REPL. No {@code --json} in the REPL.
   */
  public void runSearchCommand(@NotNull String unavailableMsg, @NotNull SearchInvocation body) {
    @Nullable LibraryManager manager = null;
    if (myServer instanceof ArendServerImpl arendServer
        && arendServer.getRequester() instanceof DelegateServerRequester delegate
        && delegate.requester instanceof CliServerRequester cliServerRequester) {
      manager = cliServerRequester.getLibraryManager();
    }
    if (manager == null) {
      eprintln(unavailableMsg);
      return;
    }
    List<SourceLibrary> libs = new ArrayList<>();
    for (String name : manager.getLibraries()) {
      if (name.equals(REPL_NAME)) continue;
      SourceLibrary lib = manager.getLibrary(name);
      if (lib != null) libs.add(lib);
    }
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    PrintStream realOut = System.out, realErr = System.err;
    System.setOut(capture);
    System.setErr(capture);
    try {
      body.run(manager, libs, capture);
    } finally {
      System.setOut(realOut);
      System.setErr(realErr);
    }
    print(buffer.toString(StandardCharsets.UTF_8));
  }

  /**
   * The {@link ConsoleQueryTool.QueryContext} for a REPL query command: the REPL's hot
   * {@code myServer}, output captured into the command buffer, never JSON, and the
   * synthetic {@code Repl} library excluded from the search scope.
   */
  public ConsoleQueryTool.QueryContext replQueryContext(LibraryManager manager, List<SourceLibrary> libs, PrintStream capture) {
    return new ConsoleQueryTool.QueryContext(libs, manager, myServer, errorReporter, capture, false, Set.of(REPL_NAME));
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
