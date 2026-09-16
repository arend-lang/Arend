package org.arend.naming.scope;

import org.arend.naming.reference.Referable;
import org.arend.naming.resolving.ScopeUsageCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

public class RecordingScope extends DelegateScope {
  private final ScopeUsageCollector myCollector;

  public RecordingScope(Scope parent, ScopeUsageCollector collector) {
    super(parent);
    myCollector = collector;
  }

  public void suppress() {
    myCollector.suppress();
  }

  public void release() {
    myCollector.release();
  }

  @Override
  public @NotNull RecordingScope getRecordingScope() {
    return this;
  }

  private void record(String name, Referable referable, NamespaceCommandSink sink) {
    if (sink.isGuess()) myCollector.recordGuess(name, referable, sink);
    else myCollector.recordLookup(name, referable, sink);
  }

  private void recordUnattributable(String name) {
    myCollector.recordUnknownName(name);
  }

  private static class QualifierScope extends DelegateScope {
    private final ScopeUsageCollector myCollector;

    QualifierScope(Scope parent, ScopeUsageCollector collector) {
      super(parent);
      myCollector = collector;
    }

    private void record(String name, Referable referable) {
      myCollector.recordModuleMember(name, referable);
    }

    @Override
    public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
      Referable ref = parent.resolveName(name, context);
      if (ref != null) record(name, ref);
      return ref;
    }

    @Override
    public @Nullable Scope resolveNamespace(@NotNull String name) {
      Scope result = parent.resolveNamespace(name);
      if (result != null) {
        Referable ref = parent.resolveName(name, ScopeContext.STATIC);
        if (ref != null) record(name, ref);
      }
      return result;
    }

    @Override
    public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
      return parent.find(pred, context);
    }

    @Override
    public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context, @Nullable NamespaceCommandSink sink) {
      return parent.find(pred, context, sink);
    }

    @Override
    public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
      return parent.getElements(context);
    }
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context) {
    return resolveName(name, context, new NamespaceCommandSink());
  }

  @Override
  public @Nullable Referable resolveName(@NotNull String name, @Nullable ScopeContext context, @Nullable NamespaceCommandSink sink) {
    if (sink == null) sink = new NamespaceCommandSink();
    Referable ref = parent.resolveName(name, context, sink);
    if (ref != null) record(name, ref, sink);
    return ref;
  }

  @Override
  public @Nullable Scope resolveNamespace(@NotNull String name) {
    Scope result = parent.resolveNamespace(name);
    if (result == null) return null;
    NamespaceCommandSink sink = new NamespaceCommandSink();
    Referable ref = parent.resolveName(name, ScopeContext.STATIC, sink);
    if (ref == null) {
      recordUnattributable(name);
      return result;
    }
    record(name, ref, sink);
    return sink.isKnown() ? result : new QualifierScope(result, myCollector);
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context) {
    return parent.find(pred, context);
  }

  @Override
  public @Nullable Referable find(Predicate<Referable> pred, @Nullable ScopeContext context, @Nullable NamespaceCommandSink sink) {
    return parent.find(pred, context, sink);
  }

  @Override
  public @NotNull Collection<? extends Referable> getElements(@Nullable ScopeContext context) {
    return parent.getElements(context);
  }
}
