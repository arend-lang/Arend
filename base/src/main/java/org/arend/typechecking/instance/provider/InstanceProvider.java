package org.arend.typechecking.instance.provider;

import org.arend.naming.reference.TCDefReferable;

import java.util.List;
import java.util.function.Predicate;

// TODO[server2]: Replace with Scope?
public interface InstanceProvider {
  TCDefReferable findInstance(Predicate<TCDefReferable> pred);
  List<? extends TCDefReferable> getInstances();
}
