package org.arend.server.imports;

import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.ImportedScope;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * The scope a file is resolved in, mirroring {@link org.arend.naming.scope.ScopeFactory#parentScopeForGroup}.
 *
 * <p>An {@code \import} is used in two unrelated ways, and both are charged here. Its names reach
 * the file through the namespace command itself, which {@link TracingLexicalScope} handles; but the
 * path of the import is also what puts the module into {@link ImportedScope}, which is the only
 * reason a qualified {@code Foo.foo} resolves at all. A reference that walks down the imported tree
 * is charged to the import with the longest path that is a prefix of the walk, so that
 * {@code Bar.Baz.Qux.FinOrd} is charged to {@code \import Bar.Baz.Qux} rather than to a shorter
 * {@code \import Bar.Baz} that happens to be there as well.
 *
 * <p>Prelude is in scope without being imported, so names answered by it carry no part. When the
 * file does import Prelude, {@code parentScopeForGroup} drops the implicit Prelude scope, and the
 * import is charged like any other.
 */
final class TracingImportedScope implements TracingScope {
  private final Scope myPrelude;
  private final ImportedScope myImported;
  private final List<ConcreteNamespaceCommand> myImports;
  private final List<String> myConsumed;
  private final ImportUsageData myUsage;

  private final EnumMap<ScopeContext, Map<String, Binding>> myElements = new EnumMap<>(ScopeContext.class);

  private TracingImportedScope(Scope prelude, ImportedScope imported, List<ConcreteNamespaceCommand> imports, List<String> consumed, ImportUsageData usage) {
    myPrelude = prelude;
    myImported = imported;
    myImports = imports;
    myConsumed = consumed;
    myUsage = usage;
  }

  static @NotNull TracingImportedScope forFile(@NotNull ConcreteGroup group, @NotNull ModuleScopeProvider provider, @NotNull ImportUsageData usage) {
    List<ConcreteNamespaceCommand> imports = new ArrayList<>();
    boolean prelude = true;
    for (ConcreteStatement statement : group.statements()) {
      ConcreteNamespaceCommand command = statement.command();
      if (command != null && command.isImport()) {
        imports.add(command);
        if (command.module().getPath().equals(Prelude.MODULE_PATH.toList())) prelude = false;
      }
    }
    Scope preludeScope = prelude ? provider.forModule(Prelude.MODULE_PATH) : null;
    return new TracingImportedScope(preludeScope, new ImportedScope(group, provider), imports, Collections.emptyList(), usage);
  }

  /**
   * @return the import that makes the module at {@param path} reachable, which is the one with the
   *         longest path that {@param path} starts with.
   */
  private @Nullable ConcreteNamespaceCommand importReaching(List<String> path) {
    ConcreteNamespaceCommand result = null;
    int best = 0;
    for (ConcreteNamespaceCommand command : myImports) {
      List<String> modulePath = command.module().getPath();
      if (modulePath.size() > best && modulePath.size() <= path.size() && path.subList(0, modulePath.size()).equals(modulePath)) {
        result = command;
        best = modulePath.size();
      }
    }
    return result;
  }

  private ImportUsageData.@Nullable Part partFor(String name) {
    List<String> path = new ArrayList<>(myConsumed);
    path.add(name);
    ConcreteNamespaceCommand command = importReaching(path);
    return command == null ? null : ImportUsageData.partOf(command, name);
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    if (context == null) {
      for (ScopeContext ctx : ScopeContext.values()) {
        Referable referable = resolveName(name, ctx);
        if (referable != null) return referable;
      }
      return null;
    }

    if (myPrelude != null) {
      Referable referable = myPrelude.resolveName(name, context);
      if (referable != null) return referable;
    }

    Referable referable = myImported.resolveName(name, context);
    if (referable != null) {
      ImportUsageData.Part part = partFor(name);
      if (part != null) myUsage.charge(part);
    }
    return referable;
  }

  @Override
  public @Nullable Scope resolveNamespace(@NotNull String name) {
    if (myPrelude != null) {
      Scope scope = myPrelude.resolveNamespace(name);
      if (scope != null) return scope;
    }

    Scope scope = myImported.resolveNamespace(name);
    if (scope == null) return null;

    // the walk may still go deeper, so charge only once a name is resolved at the end of it
    List<String> consumed = new ArrayList<>(myConsumed);
    consumed.add(name);
    return new Descent(scope, consumed);
  }

  @Override
  public @NotNull Map<String, Binding> getBindings(@NotNull ScopeContext context) {
    return myElements.computeIfAbsent(context, ctx -> {
      Map<String, Binding> result = new LinkedHashMap<>();
      if (myPrelude != null) {
        myPrelude.find(referable -> {
          result.putIfAbsent(referable.textRepresentation(), new Binding(referable, null, null));
          return false;
        }, ctx);
      }
      myImported.find(referable -> {
        String name = referable.textRepresentation();
        result.computeIfAbsent(name, n -> new Binding(referable, null, partFor(n)));
        return false;
      }, ctx);
      return result;
    });
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    if (context == null) {
      for (ScopeContext ctx : ScopeContext.values()) {
        Referable referable = find(pred, ctx);
        if (referable != null) return referable;
      }
      return null;
    }
    for (Binding entry : getBindings(context).values()) {
      if (entry.referable() != null && pred.test(entry.referable())) return entry.referable();
    }
    return null;
  }

  @Override
  public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    if (context == null) return TracingScope.super.getElements(null);
    List<Referable> result = new ArrayList<>();
    for (Binding entry : getBindings(context).values()) {
      if (entry.referable() != null) result.add(entry.referable());
    }
    return result;
  }

  @Override
  public @NotNull ImportedScope getImportedSubscope() {
    return myImported;
  }

  /**
   * A step down the tree of imported modules. It answers from the real scope of the module and
   * charges the import that the walk so far belongs to.
   */
  private final class Descent implements Scope {
    private final Scope myScope;
    private final List<String> myPath;

    private Descent(Scope scope, List<String> path) {
      myScope = scope;
      myPath = path;
    }

    private ImportUsageData.@Nullable Part partForPath(String name) {
      List<String> path = new ArrayList<>(myPath);
      path.add(name);
      ConcreteNamespaceCommand command = importReaching(path);
      return command == null ? null : ImportUsageData.partOf(command, name);
    }

    @Override
    public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
      Referable referable = myScope.resolveName(name, context);
      if (referable != null) {
        ImportUsageData.Part part = partForPath(name);
        if (part != null) myUsage.charge(part);
      }
      return referable;
    }

    @Override
    public @Nullable Scope resolveNamespace(@NotNull String name) {
      Scope scope = myScope.resolveNamespace(name);
      if (scope == null) return null;
      List<String> path = new ArrayList<>(myPath);
      path.add(name);
      return new Descent(scope, path);
    }

    @Override
    public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
      return myScope.find(pred, context);
    }

    @Override
    public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
      return myScope.getElements(context);
    }

    @Override
    public @Nullable ImportedScope getImportedSubscope() {
      return myScope.getImportedSubscope();
    }
  }
}
