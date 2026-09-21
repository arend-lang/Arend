package org.arend.server;

import org.arend.core.definition.Definition;
import org.arend.naming.reference.InternalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class BinaryCacheFilter {
  public static boolean isSerializable(@Nullable Definition def) {
    return def != null
        && !def.status().needsTypeChecking()
        && !def.status().hasErrors()
        && !def.getGoals().contains(def);
  }

  public static int countSerializable(@NotNull ConcreteGroup group) {
    int[] count = { 0 };
    anyDefinition(group, ref -> {
      if (isSerializable(ref.getTypechecked())) count[0]++;
      return false;
    });
    return count[0];
  }

  public static boolean hasTypecheckingErrors(@NotNull ConcreteGroup group) {
    return anyDefinition(group, ref -> {
      Definition def = ref.getTypechecked();
      return def != null && def.status().hasErrors();
    });
  }

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
