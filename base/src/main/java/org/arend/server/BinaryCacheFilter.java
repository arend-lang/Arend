package org.arend.server;

import org.arend.core.definition.Definition;
import org.arend.naming.reference.InternalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public final class BinaryCacheFilter {
  public static boolean hasTypecheckingErrors(@NotNull ConcreteGroup group) {
    return anyDefinition(group, ref -> {
      Definition def = ref.getTypechecked();
      return def != null && def.status().hasErrors();
    });
  }

  /**
   * True if any definition in {@code group} holds an unfilled goal.
   *
   * <p>Goals keep a module out of the cache for the same reason errors do, and for one more:
   * the {@code .arc} records that a definition had a goal but not what the goal was, so a
   * module restored from cache could only ever report a contentless placeholder. Not caching
   * it means every goal comes from a real typecheck, with its context intact.
   */
  public static boolean hasGoals(@NotNull ConcreteGroup group) {
    return anyDefinition(group, ref -> {
      Definition def = ref.getTypechecked();
      return def != null && def.getGoals().contains(def);
    });
  }

  public static boolean isCacheable(@NotNull ConcreteGroup group) {
    return !hasTypecheckingErrors(group) && !hasGoals(group);
  }

  private static boolean anyDefinition(ConcreteGroup group, Predicate<TCDefReferable> visit) {
    if (group.referable() instanceof TCDefReferable ref && ref.getKind().isTypecheckable() && visit.test(ref)) return true;
    for (InternalReferable internalRef : group.getInternalReferables()) {
      if (internalRef instanceof TCDefReferable ref && ref.getKind().isTypecheckable() && visit.test(ref)) return true;
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && anyDefinition(statement.group(), visit)) return true;
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      if (anyDefinition(dynGroup, visit)) return true;
    }
    return false;
  }
}
