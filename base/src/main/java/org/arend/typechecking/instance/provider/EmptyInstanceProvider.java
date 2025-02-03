package org.arend.typechecking.instance.provider;

import org.arend.naming.reference.TCDefReferable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class EmptyInstanceProvider implements InstanceProvider {
  private static final EmptyInstanceProvider INSTANCE = new EmptyInstanceProvider();

  private EmptyInstanceProvider() {}

  public static EmptyInstanceProvider getInstance() {
    return INSTANCE;
  }

  @Override
  public TCDefReferable findInstance(Predicate<TCDefReferable> pred) {
    return null;
  }

  @Override
  public List<? extends TCDefReferable> getInstances() {
    return Collections.emptyList();
  }
}
