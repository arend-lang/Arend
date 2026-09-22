package org.arend.server.imports;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.ModuleReferable;
import org.arend.naming.reference.ParameterReferable;
import org.arend.naming.reference.RedirectingReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.*;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static org.arend.server.imports.TracedScope.Entry;

/**
 * A stand-in for {@code CachingScope.make(LexicalScope.insideOf(..))} that remembers which
 * namespace command answered each lookup. It has to replace the pair rather than wrap either of
 * them: a {@link CachingScope} around this one would enumerate it once through {@link #find},
 * which charges nothing, and then answer every {@link #resolveName} from its own map, so no
 * lookup would ever reach here.
 *
 * <p>The content of a group is split into the parts {@link LexicalScope} consults, in the order it
 * consults them: what the group itself declares, then its namespace commands in source order, then
 * its external parameters, then the parent scope. Flattening them into one map per
 * {@link ScopeContext}, keeping the first binding of every name, reproduces both the shadowing rule
 * of {@link LexicalScope} and the caching of {@link CachingScope} -- and gives every name in the
 * map the {@link NamespaceCommandUsage.Part} that brought it in. Two identical {@code \open}s
 * therefore behave as they do during a real resolve: the first one binds the name and is charged
 * for it, the second one is never reached.
 *
 * <p>Names that the group declares itself, external parameters and anything coming from Prelude
 * carry no part, so resolving them charges nothing.
 *
 * <p>The order the parts are walked in is the one thing here that is copied rather than reused, so
 * it has to stay in step with {@link LexicalScope#find}. Everything the order is applied to is the
 * real thing: the content of the group is a {@link LexicalScope} over it with no parent and no
 * opens, and the namespace of a command is built by {@link NamespaceCommandNamespace}.
 */
public class TracingLexicalScope implements TracedScope {
  private final Scope myParent;
  private final ConcreteGroup myGroup;
  private final ModulePath myModule;
  private final boolean myDynamicContext;
  private final boolean myWithAdditionalContent;
  private final NamespaceCommandUsage myUsage;

  private final Scope myContent;
  private final List<CommandScope> myCommands;
  private final EnumMap<ScopeContext, Map<String, Entry>> myElements = new EnumMap<>(ScopeContext.class);
  private final Map<String, Entry> myNamespaces = new HashMap<>();
  private Map<TCDefReferable, NamespaceCommandUsage.Part> myPooledInstances;
  private Set<TCDefReferable> myOwnInstances;

  private record CommandScope(@NotNull ConcreteNamespaceCommand command, @NotNull Scope namespace) {}

  public TracingLexicalScope(Scope parent, ConcreteGroup group, ModulePath module, boolean isDynamicContext, boolean withAdditionalContent, NamespaceCommandUsage usage) {
    myParent = parent;
    myGroup = group;
    myModule = module;
    myDynamicContext = isDynamicContext;
    myWithAdditionalContent = withAdditionalContent;
    myUsage = usage;

    myContent = CachingScope.make(new LexicalScope(EmptyScope.INSTANCE, group, null, isDynamicContext, false));
    myCommands = makeCommandScopes();
    for (ScopeContext context : ScopeContext.values()) {
      myElements.put(context, makeElements(context));
    }
  }

  public static @NotNull TracingLexicalScope insideOf(@NotNull ConcreteGroup group, @NotNull Scope parent, boolean isDynamicContext, @NotNull NamespaceCommandUsage usage) {
    ModuleLocation location = group.referable().getLocation();
    return new TracingLexicalScope(parent, group, location == null ? null : location.getModulePath(), isDynamicContext, true, usage);
  }

  /**
   * Resolves the namespace of every command of the group, exactly as {@link LexicalScope} does, but
   * with the names that the module path of a command goes through charged to that command rather
   * than counted as uses of their own.
   */
  private List<CommandScope> makeCommandScopes() {
    List<CommandScope> result = new ArrayList<>();
    Scope openBase = null;
    for (ConcreteStatement statement : myGroup.statements()) {
      ConcreteNamespaceCommand command = statement.command();
      if (command == null || !(myWithAdditionalContent || command.isImport())) {
        continue;
      }

      Scope base;
      if (command.isImport()) {
        if (myModule != null && command.module().getPath().equals(myModule.toList())) {
          continue;
        }
        base = getImportedSubscope();
      } else {
        if (openBase == null) {
          openBase = new TracingLexicalScope(myParent, myGroup, null, true, false, myUsage);
        }
        base = openBase;
      }

      Scope finalBase = base;
      Scope namespace = myUsage.underPathOf(command, () -> NamespaceCommandNamespace.resolveNamespace(finalBase, command));
      result.add(new CommandScope(command, namespace));
    }
    return result;
  }

  private static String keyOf(Referable referable) {
    return referable instanceof ModuleReferable moduleRef ? moduleRef.path.getLastName() : referable.textRepresentation();
  }

  /**
   * Enumerates the frame in the order {@link LexicalScope#find} walks it and keeps the first
   * binding of every name, which is what {@link CachingScope} does; the result therefore answers
   * exactly as {@code CachingScope.make(LexicalScope..)} would, and additionally knows where each
   * name came from.
   */
  private Map<String, Entry> makeElements(ScopeContext context) {
    Map<String, Entry> result = new LinkedHashMap<>();
    addPlainEntries(myContent, context, result);

    for (CommandScope command : myCommands) {
      command.namespace().find(referable -> {
        result.computeIfAbsent(keyOf(referable), name -> new Entry(referable, null, NamespaceCommandUsage.partOf(command.command(), name)));
        return false;
      }, context);
    }

    if (myWithAdditionalContent && context == ScopeContext.STATIC) {
      for (ParameterReferable referable : myGroup.externalParameters()) {
        result.putIfAbsent(referable.getRefName(), new Entry(referable, null, null));
      }
    }

    addEntries(myParent, context, result);
    return result;
  }

  /**
   * Adds the names a parent scope binds, keeping the part of every name that a traced scope knows.
   * A parent may be a composite -- the scope of a dynamic subgroup is merged with the dynamic scope
   * of its class -- so composites are taken apart rather than enumerated as a whole, which would
   * lose the parts of the traced scope inside them.
   */
  private static void addEntries(Scope scope, ScopeContext context, Map<String, Entry> result) {
    switch (scope) {
      case TracedScope traced -> {
        for (Map.Entry<String, Entry> entry : traced.getEntries(context).entrySet()) {
          result.putIfAbsent(entry.getKey(), entry.getValue());
        }
      }
      case MergeScope merge -> {
        for (Scope subScope : merge.getScopes()) addEntries(subScope, context, result);
      }
      default -> addPlainEntries(scope, context, result);
    }
  }

  private static void addPlainEntries(Scope scope, ScopeContext context, Map<String, Entry> result) {
    scope.find(referable -> {
      result.putIfAbsent(keyOf(referable), new Entry(referable, null, null));
      return false;
    }, context);
  }

  private @Nullable Referable charge(@Nullable Entry entry) {
    if (entry == null) return null;
    if (entry.part() != null) myUsage.charge(entry.part());
    return entry.referable();
  }

  @Nullable
  @Override
  public Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    if (context == null) {
      for (ScopeContext ctx : ScopeContext.values()) {
        Referable referable = resolveName(name, ctx);
        if (referable != null) return referable;
      }
      return null;
    }
    return charge(myElements.get(context).get(name));
  }

  @Nullable
  @Override
  public Scope resolveNamespace(@NotNull String name) {
    Entry entry = myNamespaces.get(name);
    if (entry == null) {
      entry = makeNamespace(name);
      myNamespaces.put(name, entry);
    }
    if (entry.part() != null) myUsage.charge(entry.part());
    return entry.scope();
  }

  private Entry makeNamespace(String name) {
    Scope scope = myContent.resolveNamespace(name);
    if (scope != null) return new Entry(null, scope, null);

    for (CommandScope command : myCommands) {
      scope = command.namespace().resolveNamespace(name);
      if (scope != null) return new Entry(null, scope, NamespaceCommandUsage.partOf(command.command(), name));
    }

    // a traced parent charges itself, so the result carries no part of its own
    return new Entry(null, myParent.resolveNamespace(name), null);
  }

  @Nullable
  @Override
  public Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    if (context == null) {
      for (Map<String, Entry> map : myElements.values()) {
        for (Entry entry : map.values()) {
          if (entry.referable() != null && pred.test(entry.referable())) return entry.referable();
        }
      }
      return null;
    }
    for (Entry entry : myElements.get(context).values()) {
      if (entry.referable() != null && pred.test(entry.referable())) return entry.referable();
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    if (context == null) return TracedScope.super.getElements(null);
    List<Referable> result = new ArrayList<>();
    for (Entry entry : myElements.get(context).values()) {
      if (entry.referable() != null) result.add(entry.referable());
    }
    return result;
  }

  @NotNull
  @Override
  public Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
    return myWithAdditionalContent ? new TracingLexicalScope(myParent, myGroup, null, myDynamicContext, false, myUsage) : this;
  }

  @Override
  public @Nullable ImportedScope getImportedSubscope() {
    return myParent.getImportedSubscope();
  }

  @Override
  public @Nullable Scope forNamespaceCommand(@NotNull ConcreteNamespaceCommand command) {
    Scope base = TracedScope.super.forNamespaceCommand(command);
    return base == null ? null : new PathScope(base, command, myUsage);
  }


  /**
   * Resolving the module path of a command must not count as a use of the names the path goes
   * through; this view redirects those charges to the command instead.
   */
  private record PathScope(@NotNull Scope delegate, @NotNull ConcreteNamespaceCommand command, @NotNull NamespaceCommandUsage usage) implements Scope {
    @Override
    public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
      return usage.underPathOf(command, () -> delegate.resolveName(name, context));
    }

    @Override
    public @Nullable Scope resolveNamespace(@NotNull String name) {
      return usage.underPathOf(command, () -> delegate.resolveNamespace(name));
    }

    @Override
    public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
      return delegate.find(pred, context);
    }

    @Override
    public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
      return delegate.getElements(context);
    }

    @Override
    public @NotNull Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
      return delegate.getGlobalSubscopeWithoutOpens(withImports);
    }

    @Override
    public @Nullable ImportedScope getImportedSubscope() {
      return delegate.getImportedSubscope();
    }

  }

  @Override
  public @NotNull Map<String, Entry> getEntries(@NotNull ScopeContext context) {
    return myElements.get(context);
  }

  /**
   * Charges whatever put {@param instance} into the instance pool this group is typechecked with.
   *
   * <p>The pool is not the scope: {@code DefinitionResolveNameVisitor.resolveGroup} fills it from
   * the elements of each command regardless of whether their names are shadowed, so an instance
   * stays available under a name that resolves to something else. The order is mirrored here --
   * the instances a group declares itself come first, then its commands in reverse source order,
   * then the pool it inherited -- because the typechecker takes the first match, and that is the
   * entry whose command has to be kept.
   *
   * <p>An instance that needs no command charges nothing: one the group declares itself, or one
   * reached from outside the module's own scopes, such as Prelude.
   */
  void chargeInstance(@NotNull TCDefReferable instance) {
    chargeInstanceIn(this, instance);
  }

  private static boolean chargeInstanceIn(Scope scope, TCDefReferable instance) {
    switch (scope) {
      case TracingLexicalScope frame -> {
        if (frame.getOwnInstances().contains(instance)) return true;
        NamespaceCommandUsage.Part part = frame.getPooledInstances().get(instance);
        if (part != null) {
          frame.myUsage.charge(part);
          return true;
        }
        return chargeInstanceIn(frame.myParent, instance);
      }
      case MergeScope merge -> {
        for (Scope subScope : merge.getScopes()) {
          if (chargeInstanceIn(subScope, instance)) return true;
        }
        return false;
      }
      default -> {
        return false;
      }
    }
  }

  private Set<TCDefReferable> getOwnInstances() {
    if (myOwnInstances == null) {
      myOwnInstances = new HashSet<>();
      for (ConcreteStatement statement : myGroup.statements()) {
        if (statement.group() != null) addInstance(statement.group().referable(), myOwnInstances);
      }
      for (ConcreteGroup dynamicGroup : myGroup.dynamicGroups()) {
        addInstance(dynamicGroup.referable(), myOwnInstances);
      }
    }
    return myOwnInstances;
  }

  private static void addInstance(Referable referable, Set<TCDefReferable> result) {
    if (referable instanceof TCDefReferable defRef && defRef.getKind() == GlobalReferable.Kind.INSTANCE) {
      result.add(defRef);
    }
  }

  private Map<TCDefReferable, NamespaceCommandUsage.Part> getPooledInstances() {
    if (myPooledInstances == null) {
      myPooledInstances = new HashMap<>();
      // later commands are prepended to the pool, so they are the ones the search reaches first
      for (int i = myCommands.size() - 1; i >= 0; i--) {
        CommandScope command = myCommands.get(i);
        for (Referable element : command.namespace().getElements(ScopeContext.STATIC)) {
          Referable original = RedirectingReferable.getOriginalReferable(element);
          if (original instanceof TCDefReferable defRef && defRef.getKind() == GlobalReferable.Kind.INSTANCE) {
            myPooledInstances.putIfAbsent(defRef, NamespaceCommandUsage.partOf(command.command(), element.getRefName()));
          }
        }
      }
    }
    return myPooledInstances;
  }
}
