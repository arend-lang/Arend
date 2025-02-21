package org.arend.typechecking.order.dependency;

import org.arend.naming.reference.TCDefReferable;

import java.util.Set;

public interface DependencyListener {
  void dependsOn(TCDefReferable def1, TCDefReferable def2);
  Set<? extends TCDefReferable> update(TCDefReferable definition);
  Set<? extends TCDefReferable> getDependencies(TCDefReferable definition);
}
