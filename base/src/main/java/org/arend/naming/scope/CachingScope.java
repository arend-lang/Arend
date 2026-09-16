package org.arend.naming.scope;

import org.arend.naming.reference.ModuleReferable;
import org.arend.naming.reference.Referable;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class CachingScope implements Scope {
  private final EnumMap<ScopeContext, Map<String, Referable>> myElements = new EnumMap<>(ScopeContext.class);
  private final EnumMap<ScopeContext, Map<String, Object>> myProvenance = new EnumMap<>(ScopeContext.class);
  private final Map<String, Scope> myNamespaces = new HashMap<>();
  private final Scope myScope;
  private final static Object NO_COMMAND = new Object();
  private final static Scope EMPTY_SCOPE = new Scope() {};

  private CachingScope(Scope scope) {
    myScope = scope;
    NamespaceCommandSink sink = new NamespaceCommandSink();
    for (ScopeContext context : ScopeContext.values()) {
      scope.find(ref -> {
        String name = ref instanceof ModuleReferable ? ((ModuleReferable) ref).path.getLastName() : ref.textRepresentation();
        Map<String, Referable> map = myElements.computeIfAbsent(context, k -> new LinkedHashMap<>());
        if (map.putIfAbsent(name, ref) == null && sink.isKnown()) {
          myProvenance.computeIfAbsent(context, k -> new HashMap<>())
            .put(name, sink.getCommand() == null ? NO_COMMAND : sink.getCommand());
        }
        return false;
      }, context, sink);
    }
  }

  private void reportProvenance(String name, ScopeContext context, NamespaceCommandSink sink) {
    Map<String, Object> provenance = myProvenance.get(context);
    Object origin = provenance == null ? null : provenance.get(name);
    if (origin == null) {
      sink.reset();
    } else if (origin == NO_COMMAND) {
      sink.setNoCommand();
    } else {
      sink.setCommand((ConcreteNamespaceCommand) origin);
    }
  }

  public static Scope make(Scope scope) {
    return scope instanceof CachingScope || scope instanceof ImportedScope || scope == EmptyScope.INSTANCE || scope instanceof SimpleScope ? scope : new CachingScope(scope);
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context, @Nullable NamespaceCommandSink sink) {
    if (sink == null) return find(pred, context);
    if (context == null) {
      for (Map.Entry<ScopeContext, Map<String, Referable>> entry : myElements.entrySet()) {
        for (Map.Entry<String, Referable> element : entry.getValue().entrySet()) {
          reportProvenance(element.getKey(), entry.getKey(), sink);
          if (pred.test(element.getValue())) return element.getValue();
        }
      }
    } else {
      Map<String, Referable> map = myElements.get(context);
      if (map != null) {
        for (Map.Entry<String, Referable> element : map.entrySet()) {
          reportProvenance(element.getKey(), context, sink);
          if (pred.test(element.getValue())) return element.getValue();
        }
      }
    }
    sink.reset();
    return null;
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    if (context == null) {
      for (Map<String, Referable> map : myElements.values()) {
        for (Referable ref : map.values()) {
          if (pred.test(ref)) {
            return ref;
          }
        }
      }
    } else {
      Map<String, Referable> map = myElements.get(context);
      if (map != null) {
        for (Referable ref : map.values()) {
          if (pred.test(ref)) {
            return ref;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    if (context == null) {
      return Scope.super.getElements(null);
    }
    Map<String, Referable> map = myElements.get(context);
    return map == null ? Collections.emptyList() : map.values();
  }

  @Nullable
  @Override
  public Referable resolveName(@NotNull String name, @Nullable ScopeContext context, @Nullable NamespaceCommandSink sink) {
    if (sink == null) return resolveName(name, context);
    if (context == null) {
      for (ScopeContext ctx : ScopeContext.values()) {
        Referable ref = resolveName(name, ctx, sink);
        if (ref != null) return ref;
      }
      sink.reset();
      return null;
    }
    Map<String, Referable> map = myElements.get(context);
    Referable ref = map == null ? null : map.get(name);
    if (ref == null) sink.reset(); else reportProvenance(name, context, sink);
    return ref;
  }

  @Nullable
  @Override
  public Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    if (context == null) {
      for (ScopeContext ctx : ScopeContext.values()) {
        Referable ref = resolveName(name, ctx);
        if (ref != null) {
          return ref;
        }
      }
      return null;
    } else {
      var map = myElements.get(context);
      return map == null ? null : map.get(name);
    }
  }

  @Nullable
  @Override
  public Scope resolveNamespace(@NotNull String name) {
    Map<String, Scope> namespaces = myNamespaces;
    Scope namespace = namespaces.get(name);
    if (namespace == null) {
      namespace = myScope.resolveNamespace(name);
      namespace = namespace == null ? EMPTY_SCOPE : CachingScope.make(namespace);
      namespaces.put(name, namespace);
    }

    return namespace == EMPTY_SCOPE ? null : namespace;
  }

  @NotNull
  @Override
  public Scope getGlobalSubscopeWithoutOpens(boolean withImports) {
    Scope result = myScope.getGlobalSubscopeWithoutOpens(withImports);
    return result == myScope ? this : result;
  }

  @Nullable
  @Override
  public ImportedScope getImportedSubscope() {
    return myScope.getImportedSubscope();
  }

  @Nullable
  @Override
  public RecordingScope getRecordingScope() {
    return myScope.getRecordingScope();
  }

  public Scope getInternalScope() {
    return myScope;
  }
}
