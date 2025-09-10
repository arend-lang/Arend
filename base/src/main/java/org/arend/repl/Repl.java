package org.arend.repl;

import org.arend.core.expr.Expression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.DefinitionRenamer;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.reference.Precedence;
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer;
import org.arend.extImpl.definitionRenamer.ScopeDefinitionRenamer;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.naming.reference.*;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor;
import org.arend.naming.scope.MergeScope;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.ScopeFactory;
import org.arend.prelude.Prelude;
import org.arend.repl.action.*;
import org.arend.server.ArendChecker;
import org.arend.server.ArendLibrary;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.*;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.instance.ArendInstances;
import org.arend.typechecking.instance.pool.GlobalInstancePool;
import org.arend.typechecking.instance.provider.InstanceScopeProvider;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.arend.typechecking.result.TypecheckingResult;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.arend.typechecking.visitor.DesugarVisitor;
import org.arend.typechecking.visitor.SyntacticDesugarVisitor;
import org.arend.util.SingletonList;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class Repl {
  public static final @NotNull String REPL_NAME = "Repl";
  public static @NotNull ModuleLocation REPL_MODULE_LOCATION = new ModuleLocation(REPL_NAME, ModuleLocation.LocationKind.SOURCE, ModulePath.fromString(REPL_NAME));

  private final List<ReplHandler> myHandlers = new ArrayList<>();
  protected final TCDefReferable myModuleReferable;
  protected final @NotNull PrettyPrinterConfig myPpConfig = new PrettyPrinterConfig() {
    @Contract(" -> new")
    @Override
    public @NotNull DefinitionRenamer getDefinitionRenamer() {
      return new CachingDefinitionRenamer(new ScopeDefinitionRenamer(getServerScope(false)));
    }

    @Override
    public @NotNull EnumSet<PrettyPrinterFlag> getExpressionFlags() {
      return getPrettyPrinterFlags();
    }

    @Override
    public @Nullable NormalizationMode getNormalizationMode() {
      return Repl.this.getNormalizationMode();
    }
  };
  protected final @NotNull ListErrorReporter errorReporter;
  protected final @NotNull ArendServer myServer;
  public final List<ConcreteStatement> statements = new ArrayList<>();

  public Repl(@NotNull ListErrorReporter listErrorReporter,
              @NotNull ArendServer server) {
    errorReporter = listErrorReporter;
    myServer = server;
    myModuleReferable = new LocatedReferableImpl(null, AccessModifier.PUBLIC, Precedence.DEFAULT, REPL_MODULE_LOCATION.getLibraryName(), Precedence.DEFAULT, null, new FullModuleReferable(REPL_MODULE_LOCATION), GlobalReferable.Kind.OTHER);
  }

  protected abstract void loadLibraries();

  private Scope getServerScope(boolean onlyContext) {
    Set<ModulePath> modules;
    if (onlyContext) {
      modules = statements.stream()
        .filter(statement -> statement.command() != null && statement.command().isImport())
        .map(statement -> new ModulePath(statement.command().module().getPath()))
        .collect(Collectors.toSet());
    } else {
      modules = myServer.getModules().stream().map(ModuleLocation::getModulePath).collect(Collectors.toSet());
    }
    modules.add(Prelude.MODULE_PATH);
    modules.add(REPL_MODULE_LOCATION.getModulePath());
    return new MergeScope(modules.stream().map(modulePath -> getAvailableModuleScopeProvider().forModule(modulePath)).filter(Objects::nonNull).toList());
  }

  protected final @NotNull List<Referable> getInScopeElements() {
    return new ArrayList<>(getServerScope(true).getElements());
  }

  public final void initialize() {
    loadLibraries();
    loadCommands();
    checkErrors();
  }

  public @NotNull PrettyPrinterConfig getPrettyPrinterConfig() {
    return myPpConfig;
  }

  public abstract @NotNull EnumSet<PrettyPrinterFlag> getPrettyPrinterFlags();

  public abstract @Nullable NormalizationMode getNormalizationMode();

  public abstract void setNormalizationMode(@Nullable NormalizationMode mode);

  /**
   * The function executed per main-loop of the REPL.
   *
   * @param currentLine  the current user input
   * @param lineSupplier in case the command requires more user input,
   *                     use this to acquire more lines
   * @return true if the REPL wants to quit
   */
  public final boolean repl(@NotNull String currentLine, @NotNull Supplier<@NotNull String> lineSupplier) {
    boolean quit = false;
    for (var action : myHandlers)
      if (action.isApplicable(currentLine)) {
        try {
          action.invoke(currentLine, this, lineSupplier);
        } catch (QuitReplException e) {
          quit = true;
        }
        checkErrors();
      }
    return quit;
  }

  @Contract(pure = true)
  public final @NotNull ModuleScopeProvider getAvailableModuleScopeProvider() {
    return myServer.getModuleScopeProvider(null, true);
  }

  @Contract(pure = true)
  public final @NotNull ModuleScopeProvider getLibraryModuleScopeProvider(String libraryName) {
    return myServer.getModuleScopeProvider(libraryName, true);
  }

  public @NotNull String prompt() {
    return ">";
  }

  protected abstract @Nullable ConcreteGroup parseStatements(@NotNull String line);

  protected abstract @Nullable Concrete.Expression parseExpr(@NotNull String text);

  protected boolean checkPotentialUnloadedModules(Collection<? extends ConcreteStatement> statements) {
    return true;
  }

  public int typecheckModules(List<ModuleLocation> moduleLocations) {
    ArendChecker arendChecker = myServer.getCheckerFor(moduleLocations);
    return arendChecker.typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  public final void checkStatements(@NotNull String line) {
    var group = parseStatements(line);
    if (group == null) return;
    var moduleScopeProvider = getAvailableModuleScopeProvider();
    if (!checkPotentialUnloadedModules(group.statements())) {
      println("A module or some modules are not loaded for import");
      return;
    }
    var scope = ScopeFactory.forGroup(group, moduleScopeProvider);
    new DefinitionResolveNameVisitor(new SimpleConcreteProvider(Collections.emptyMap()), TypingInfo.EMPTY, errorReporter).resolveGroup(group, scope, new ArendInstances(), null);
    if (!checkErrors()) {
      statements.addAll(group.statements());
      typecheckStatements(group, scope);
    }
  }

  protected void typecheckStatements(@NotNull ConcreteGroup group, @NotNull Scope scope) {
    ConcreteGroup myGroup = myServer.getRawGroup(REPL_MODULE_LOCATION);
    if (myGroup != null) {
      myGroup.statements().addAll(group.statements());
    } else {
      myGroup = group;
    }
    ConcreteGroup finalMyGroup = myGroup;
    myServer.updateModule(System.currentTimeMillis(), REPL_MODULE_LOCATION, () -> finalMyGroup);
    typecheckModules(new SingletonList<>(REPL_MODULE_LOCATION));
  }

  @MustBeInvokedByOverriders
  protected void loadCommands() {
    myHandlers.add(CodeParsingHandler.INSTANCE);
    myHandlers.add(CommandHandler.INSTANCE);
    registerAction("quit", QuitCommand.INSTANCE);
    registerAction("type", ShowTypeCommand.INSTANCE);
    registerAction("print", PrintCommand.INSTANCE);
    registerAction("print_flags", PrettyPrintFlagCommand.INSTANCE);
    registerAction("size", SizeCommand.INSTANCE);
    registerAction("normalize_mode", NormalizeCommand.INSTANCE);
    registerAction("libraries", ShowLoadedLibrariesCommand.INSTANCE);
    registerAction("?", CommandHandler.HELP_COMMAND_INSTANCE);
    registerAction("help", CommandHandler.HELP_COMMAND_INSTANCE);
    registerAction("show_context", ShowContextCommand.INSTANCE);
    registerAction("reset_context", ResetContextCommand.INSTANCE);
  }

  public final @Nullable ReplCommand registerAction(@NotNull String name, @NotNull ReplCommand action) {
    return CommandHandler.INSTANCE.commandMap.put(name, action);
  }

  public final @Nullable ReplCommand registerAction(@NotNull String name, @NotNull AliasableCommand action) {
    action.aliases.add(name);
    return registerAction(name, (ReplCommand) action);
  }

  public final @Nullable ReplCommand unregisterAction(@NotNull String name) {
    return CommandHandler.INSTANCE.commandMap.remove(name);
  }

  public final void clearActions() {
    CommandHandler.INSTANCE.commandMap.clear();
  }

  /**
   * A replacement of {@link System#out#println(Object)} where it uses the
   * output stream of the REPL.
   *
   * @param anything whose {@link Object#toString()} is invoked.
   */
  public void println(Object anything) {
    print(anything);
    print(System.lineSeparator());
  }

  public void println() {
    print(System.lineSeparator());
  }

  public abstract void print(Object anything);

  /**
   * @param toError if true, print to stderr. Otherwise print to stdout
   */
  public void printlnOpt(Object anything, boolean toError) {
    if (toError) eprintln(anything);
    else println(anything);
  }

  /**
   * A replacement of {@link System#err#println(Object)} where it uses the
   * error output stream of the REPL.
   *
   * @param anything whose {@link Object#toString()} is invoked.
   */
  public abstract void eprintln(Object anything);

  public Expression normalize(Expression expr) {
    NormalizationMode mode = getNormalizationMode();
    return mode == null ? expr : expr.normalize(mode);
  }

  @Contract("_, _ -> param1")
  public @NotNull StringBuilder prettyExpr(@NotNull StringBuilder builder, @NotNull Expression expression) {
    var abs = ToAbstractVisitor.convert(expression, myPpConfig);
    abs.accept(new PrettyPrintVisitor(builder, 0), new Precedence(Concrete.Expression.PREC));
    return builder;
  }

  /**
   * @param expr input concrete expression.
   * @see Repl#preprocessExpr(String)
   */
  public void checkExpr(@NotNull Concrete.Expression expr, @Nullable Expression expectedType, @NotNull Consumer<TypecheckingResult> continuation) {
    expr = DesugarVisitor.desugar(expr, errorReporter);
    if (checkErrors()) return;
    var typechecker = new CheckTypeVisitor(errorReporter, null, null);
    var instancePool = new GlobalInstancePool((myServer instanceof ArendServerImpl ? ((ArendServerImpl) myServer).getInstanceScopeProvider() : InstanceScopeProvider.EMPTY).getInstancesFor(myModuleReferable).getInstancesList(), typechecker);
    typechecker.setInstancePool(instancePool);
    var result = typechecker.finalCheckExpr(expr, expectedType);
    if (!checkErrors()) {
      continuation.accept(result);
    }
  }

  /**
   * @see Repl#checkExpr
   */
  public final @Nullable Concrete.Expression preprocessExpr(@NotNull String text) {
    var expr = parseExpr(text);
    if (expr == null || checkErrors()) return null;
    expr = SyntacticDesugarVisitor.desugar(expr.accept(new ExpressionResolveNameVisitor(getServerScope(true), new ArrayList<>(), TypingInfo.EMPTY, errorReporter, null, null), null), errorReporter);
    if (checkErrors()) return null;
    return expr;
  }

  private static final List<GeneralError.Level> ERROR_LEVELS = List.of(
      GeneralError.Level.ERROR,
      GeneralError.Level.WARNING,
      GeneralError.Level.GOAL
  );

  /**
   * Check and print errors.
   *
   * @return true if there is error(s).
   */
  public boolean checkErrors() {
    var errorList = errorReporter.getErrorList();
    boolean hasErrors = false;
    for (GeneralError error : errorList) {
      printlnOpt(error.getDoc(myPpConfig), ERROR_LEVELS.contains(error.level));
      if (error.level == GeneralError.Level.ERROR) hasErrors = true;
    }
    errorList.clear();
    return hasErrors;
  }

  public void resetReplContext() {
    statements.clear();
  }

  public TCDefReferable getModuleReferable() {
    return myModuleReferable;
  }

  private static class ShowLoadedLibrariesCommand implements ReplCommand {
    private static final ShowLoadedLibrariesCommand INSTANCE = new ShowLoadedLibrariesCommand();

    private ShowLoadedLibrariesCommand() {
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
      return "List registered libraries in the REPL";
    }

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
      for (String libraryName : api.myServer.getLibraries()) {
        ArendLibrary library = api.myServer.getLibrary(libraryName);
        api.println(libraryName + (library != null && library.isExternalLibrary() ? " (external)" : ""));
      }
    }
  }
}
