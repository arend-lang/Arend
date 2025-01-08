package org.arend.typechecking.provider;

import org.arend.naming.reference.GlobalReferable;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SimpleConcreteProvider implements ConcreteProvider {
  private final Map<GlobalReferable, Concrete.GeneralDefinition> myMap;

  public SimpleConcreteProvider(Map<GlobalReferable, Concrete.GeneralDefinition> map) {
    myMap = map;
  }

  @Override
  public @Nullable Concrete.GeneralDefinition getConcrete(GlobalReferable referable) {
    return myMap.get(referable);
  }
}
