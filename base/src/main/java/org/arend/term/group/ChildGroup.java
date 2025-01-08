package org.arend.term.group;

import org.arend.module.ModuleLocation;
import org.arend.module.scopeprovider.EmptyModuleScopeProvider;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.ScopeFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface ChildGroup extends Group {
  @Nullable ChildGroup getParentGroup();
  boolean isDynamicContext();

  default @NotNull Scope getGroupScope() {
    ChildGroup parent = getParentGroup();
    return parent == null ? ScopeFactory.forGroup(this, EmptyModuleScopeProvider.INSTANCE) : LexicalScope.insideOf(this, parent.getGroupScope(), isDynamicContext());
  }

  default ModuleLocation getGroupPath(List<GroupPath.Element> result) {
    ChildGroup parent = getParentGroup();
    if (parent != null) {
      parent.getGroupPath(result);
      GroupPath.Element element = getGroupPathElement(parent);
      if (element == null) return null;
      result.add(element);
    }
    return getReferable().getLocation();
  }

  default @Nullable LocatedGroupPath getGroupPath() {
    List<GroupPath.Element> result = new ArrayList<>();
    ModuleLocation location = getGroupPath(result);
    return location == null ? null : new LocatedGroupPath(location, result);
  }
}
