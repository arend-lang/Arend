package org.arend.term.group;

import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.ParameterReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public interface Group {
  @NotNull LocatedReferable getReferable();

  @NotNull List<? extends Statement> getStatements();

  @NotNull List<? extends InternalReferable> getInternalReferables();

  default @NotNull List<? extends InternalReferable> getConstructors() {
    return Collections.emptyList();
  }

  default @NotNull List<? extends InternalReferable> getFields() {
    return Collections.emptyList();
  }

  default @NotNull List<? extends Group> getDynamicSubgroups() {
    return Collections.emptyList();
  }

  default @NotNull List<? extends ParameterReferable> getExternalParameters() {
    return Collections.emptyList();
  }

  interface InternalReferable {
    LocatedReferable getReferable();
    boolean isVisible();
  }

  default void traverseGroup(Consumer<Group> consumer) {
    consumer.accept(this);
    for (Statement statement : getStatements()) {
      Group subgroup = statement.getGroup();
      if (subgroup != null) {
        subgroup.traverseGroup(consumer);
      }
    }
    for (Group subgroup : getDynamicSubgroups()) {
      subgroup.traverseGroup(consumer);
    }
  }

  default @Nullable GroupPath.Element getGroupPathElement(Group parent) {
    List<? extends Statement> statements = parent.getStatements();
    for (int i = 0; i < statements.size(); i++) {
      if (equals(statements.get(i).getGroup())) {
        return new GroupPath.Element(false, i);
      }
    }
    List<? extends Group> subgroups = parent.getDynamicSubgroups();
    for (int i = 0; i < subgroups.size(); i++) {
      if (equals(subgroups.get(i))) {
        return new GroupPath.Element(true, i);
      }
    }
    return null;
  }

  default boolean match(Group other, BiFunction<Group, Group, Boolean> function) {
    List<? extends Statement> stats1 = getStatements();
    List<? extends Statement> stats2 = other.getStatements();
    if (stats1.size() != stats2.size()) return false;
    for (int i = 0; i < stats1.size(); i++) {
      Group subgroup1 = stats1.get(i).getGroup();
      Group subgroup2 = stats2.get(i).getGroup();
      if (subgroup1 == null && subgroup2 == null) continue;
      if (subgroup1 == null || subgroup2 == null || !function.apply(subgroup1, subgroup2)) return false;
    }
    return true;
  }
}
