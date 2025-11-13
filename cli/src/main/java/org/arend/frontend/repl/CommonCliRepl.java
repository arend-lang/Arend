package org.arend.frontend.repl;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
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

import java.io.IOException;
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

  //region Tricky constructors (expand to read more...)
  // These two constructors are used for convincing javac that the
  // initialization is of well order.
  // All of the parameters introduced here are used more than once,
  // and one cannot introduce them as variable before the `this` or
  // `super` call because that's the rule of javac.
  public CommonCliRepl(@NotNull ArendServer server) {
    this(server, new ListErrorReporter(new ArrayList<>()));
  }

  private CommonCliRepl(
      @NotNull ArendServer server,
      @NotNull ListErrorReporter errorReporter) {
    super(
      errorReporter,
      server
    );
    Path configFile = pwd.resolve(FileUtils.LIBRARY_CONFIG_FILE);
    myReplLibrary = getNewFileSourceLibrary();
    FileSourceLibrary sourceLibrary = FileSourceLibrary.fromConfigFile(configFile, false, errorReporter);
    if (sourceLibrary != null) {
      loadLibrary(sourceLibrary);
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
    return new FileSourceLibrary(REPL_NAME, true, -1, myServer.getLibraries().stream().filter(library -> !Objects.equals(library, REPL_NAME)).toList(), null, null, null, pwd, null, null, null);
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
    return null;
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
