package org.arend.typechecking.order.dependency;

import org.arend.naming.reference.TCDefReferable;

import java.util.Collections;
import java.util.Set;

public class DummyDependencyListener implements DependencyListener {
  public static final DummyDependencyListener INSTANCE = new DummyDependencyListener();

  private DummyDependencyListener() { }

  @Override
  public void dependsOn(TCDefReferable def1, TCDefReferable def2) {

  }

  @Override
  public Set<? extends TCDefReferable> update(TCDefReferable definition) {
    return Collections.emptySet();
  }

  @Override
  public Set<? extends TCDefReferable> getDependencies(TCDefReferable definition) {
    return Collections.emptySet();
  }
}
