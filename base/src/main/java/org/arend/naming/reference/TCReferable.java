package org.arend.naming.reference;

import org.arend.ext.reference.DataContainer;
import org.jetbrains.annotations.NotNull;

// TODO[server2]: Do we need both TCReferable and TCDefReferable? Maybe replace the former with the latter or with LocatedReferable?
public interface TCReferable extends LocatedReferable, DataContainer {
  @Override
  default @NotNull TCReferable getTypecheckable() {
    return this;
  }

  boolean isTypechecked();

  default boolean isLocalFunction() {
    return false;
  }
}
