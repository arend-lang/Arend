package org.arend.frontend.repl;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.arend.frontend.ConsoleHelp;
import org.arend.frontend.symbol.ProofSearch;
import org.arend.frontend.symbol.SymbolSearch;
import org.arend.frontend.symbol.UsageSearch;
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
import org.arend.repl.action.AliasableCommand;
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
    SymbolSearchCommand symbolSearch = new SymbolSearchCommand();
    registerAction("symbol-search", symbolSearch);
    registerAction("ss", symbolSearch);
    ProofSearchCommand proofSearch = new ProofSearchCommand();
    registerAction("proof-search", proofSearch);
    registerAction("ps", proofSearch);
    FindUsagesCommand findUsages = new FindUsagesCommand();
    registerAction("find-usages", findUsages);
    registerAction("fu", findUsages);
  }

  /**
   * The {@link LibraryManager} backing this REPL's server, reached through the
   * {@link CliServerRequester} (same path used by {@link #loadLibrary}). It holds
   * every library registered at the REPL, so it is the search scope for
   * {@code :symbol-search}. {@code null} only if the server is wired differently.
   */
  private @Nullable LibraryManager libraryManager() {
    if (myServer instanceof ArendServerImpl arendServer
        && arendServer.getRequester() instanceof DelegateServerRequester delegate
        && delegate.requester instanceof CliServerRequester cliServerRequester) {
      return cliServerRequester.getLibraryManager();
    }
    return null;
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
    loadLibrary(library);
    checkErrors();
    for (String dependency : library.getLibraryDependencies()) {
      if (loaded.contains(dependency) || myServer.getLibrary(dependency) != null) continue;
      ArendLibrary dependencyLibrary = createLibrary(dependency);
      if (dependencyLibrary != null) {
        loadLibraryWithDependencies(dependencyLibrary, loaded);
      } else {
        eprintln("[ERROR] Cannot find dependency library '" + dependency + "' required by '" + library.getLibraryName() + "'.");
      }
    }
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
   * Split a REPL command line into arguments, honouring double quotes the way a
   * shell would (the REPL does no shell processing of its own, so without this a
   * pattern like {@code "re:.-comm"} would keep its quotes and be rejected). A
   * double quote is never a valid Arend identifier character, so a {@code "..."}
   * group is unambiguous: its contents become one argument with the quotes
   * removed and interior spaces preserved. Single quotes are left as-is --
   * apostrophe IS a valid Arend name character ({@code f'}, {@code iabs_-'}),
   * not a quote.
   */
  static List<String> tokenizeArgs(String line) {
    List<String> tokens = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuote = false, started = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuote = !inQuote;
        started = true;
      } else if (!inQuote && Character.isWhitespace(c)) {
        if (started) {
          tokens.add(cur.toString());
          cur.setLength(0);
          started = false;
        }
      } else {
        cur.append(c);
        started = true;
      }
    }
    if (started) tokens.add(cur.toString());
    return tokens;
  }

  /**
   * {@code :symbol-search} / {@code :ss} {@code <pattern> [pattern | option ...]} —
   * same syntax and behaviour as the {@code -ss} CLI flag, searching every library
   * registered at the REPL. No {@code --json}. {@code :? ss} prints the help.
   */
  private final class SymbolSearchCommand extends AliasableCommand {
    SymbolSearchCommand() {
      super(new ArrayList<>());
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "Search registered libraries for definitions by short name (`:? ss` for the full grammar)";
    }

    @Override
    public @Nls @NotNull String help(@NotNull Repl api) {
      return ConsoleHelp.symbolSearchReplHelp();
    }

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
      LibraryManager manager = libraryManager();
      if (manager == null) {
        eprintln("[ERROR] Symbol search is unavailable (no library manager on this server).");
        return;
      }
      // SymbolSearch writes to the given PrintStream AND to System.out/System.err
      // (query echo, warnings). Capture all of it and forward through the REPL's
      // own output stream, so it works for both the plain and jline REPL. No JSON
      // in the REPL, so Options.json stays false.
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      PrintStream realOut = System.out, realErr = System.err;
      System.setOut(capture);
      System.setErr(capture);
      try {
        List<String> args = tokenizeArgs(line);
        SymbolSearch.Parsed parsed = SymbolSearch.parseArgs(args.toArray(new String[0]), errorReporter);
        if (parsed != null) {
          // The synthetic REPL library mirrors the real ones (same source files),
          // so leaving it in scope would duplicate every hit; drop it.
          parsed.options().excludeLibraries.add(REPL_NAME);
          SymbolSearch.run(parsed.patterns(), parsed.options(), manager, myServer, errorReporter, capture);
        }
      } finally {
        System.setOut(realOut);
        System.setErr(realErr);
      }
      print(buffer.toString(StandardCharsets.UTF_8));
    }
  }

  /**
   * {@code :proof-search} / {@code :ps} {@code <pattern> [print-full]} — same
   * matching as the {@code -ps} CLI flag, searching every library registered at
   * the REPL. No {@code --json}. {@code :? ps} prints the help.
   */
  private final class ProofSearchCommand extends AliasableCommand {
    ProofSearchCommand() {
      super(new ArrayList<>());
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "Search registered libraries for definitions by signature shape (`:? ps` for the full grammar)";
    }

    @Override
    public @Nls @NotNull String help(@NotNull Repl api) {
      return ConsoleHelp.proofSearchReplHelp();
    }

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
      LibraryManager manager = libraryManager();
      if (manager == null) {
        eprintln("[ERROR] Proof search is unavailable (no library manager on this server).");
        return;
      }
      // The whole line is one structured pattern with spaces (Monoid -> _ = _), so
      // unlike :ss we do NOT treat separate tokens as separate patterns: pull out the
      // option tokens (`print-full`, `limit=N`) and rejoin the rest as the single
      // pattern.
      List<String> tokens = tokenizeArgs(line);
      List<String> optionTokens = new ArrayList<>();
      List<String> patternTokens = new ArrayList<>();
      for (String t : tokens) {
        if (t.equals("print-full") || t.startsWith("limit=")) optionTokens.add(t);
        else patternTokens.add(t);
      }
      String pattern = String.join(" ", patternTokens).trim();
      List<String> psArgs = new ArrayList<>();
      if (!pattern.isEmpty()) psArgs.add(pattern);
      psArgs.addAll(optionTokens);

      // The synthetic REPL library mirrors the real ones, so keeping it in scope
      // would duplicate every hit; drop it (as :ss does).
      List<SourceLibrary> libs = new ArrayList<>();
      for (String name : manager.getLibraries()) {
        if (name.equals(REPL_NAME)) continue;
        SourceLibrary lib = manager.getLibrary(name);
        if (lib != null) libs.add(lib);
      }

      // ProofSearch writes plain results + the [INFO] resolve chatter to
      // System.out/System.err; capture and forward through the REPL's stream so it
      // works for both the plain and jline REPL. No JSON in the REPL.
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      PrintStream realOut = System.out, realErr = System.err;
      System.setOut(capture);
      System.setErr(capture);
      try {
        ProofSearch.Parsed parsed = ProofSearch.parseArgs(psArgs.toArray(new String[0]));
        if (parsed != null) {
          parsed.options().excludeLibraries.add(REPL_NAME);
          ProofSearch.run(parsed.pattern(), parsed.options(), libs, manager, myServer, capture);
        }
      } finally {
        System.setOut(realOut);
        System.setErr(realErr);
      }
      print(buffer.toString(StandardCharsets.UTF_8));
    }
  }

  /**
   * {@code :find-usages} / {@code :fu} {@code <MODULE_PATH>:<GROUP_PATH> [option ...]} —
   * same syntax and behaviour as the {@code -fu} CLI flag, searching every library
   * registered at the REPL. No {@code --json}. {@code :? fu} prints the help.
   */
  private final class FindUsagesCommand extends AliasableCommand {
    FindUsagesCommand() {
      super(new ArrayList<>());
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "Find every usage of a definition across registered libraries (`:? fu` for the full grammar)";
    }

    @Override
    public @Nls @NotNull String help(@NotNull Repl api) {
      return ConsoleHelp.findUsagesReplHelp();
    }

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
      LibraryManager manager = libraryManager();
      if (manager == null) {
        eprintln("[ERROR] Find usages is unavailable (no library manager on this server).");
        return;
      }
      // The synthetic REPL library mirrors the real ones, so keeping it in scope
      // would duplicate every hit; drop it (as :ss / :ps do).
      List<SourceLibrary> libs = new ArrayList<>();
      for (String name : manager.getLibraries()) {
        if (name.equals(REPL_NAME)) continue;
        SourceLibrary lib = manager.getLibrary(name);
        if (lib != null) libs.add(lib);
      }

      // UsageSearch writes results to the given stream and [INFO]/[WARN] chatter to
      // System.out/System.err; capture all of it and forward through the REPL's own
      // stream so it works for both the plain and jline REPL. No JSON in the REPL.
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8);
      PrintStream realOut = System.out, realErr = System.err;
      System.setOut(capture);
      System.setErr(capture);
      try {
        UsageSearch.Parsed parsed = UsageSearch.parseArgs(tokenizeArgs(line).toArray(new String[0]));
        if (parsed != null) {
          parsed.options().excludeLibraries.add(REPL_NAME);
          UsageSearch.run(parsed.spec(), parsed.options(), libs, manager, myServer, errorReporter, capture);
        }
      } finally {
        System.setOut(realOut);
        System.setErr(realErr);
      }
      print(buffer.toString(StandardCharsets.UTF_8));
    }
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
