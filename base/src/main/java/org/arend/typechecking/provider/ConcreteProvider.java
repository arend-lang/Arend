package org.arend.typechecking.provider;

import org.arend.naming.reference.*;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.Nullable;

public interface ConcreteProvider {
  @Nullable Concrete.GeneralDefinition getConcrete(GlobalReferable referable);

  ConcreteProvider EMPTY = new ConcreteProvider() {
    @Override
    public @Nullable Concrete.GeneralDefinition getConcrete(GlobalReferable referable) {
      return null;
    }
  };
}
