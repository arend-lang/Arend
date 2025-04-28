package org.arend.naming.reference;

import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.LocalVariablesCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LocalReferable implements Referable {
  private final String myName;

  public LocalReferable(String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String textRepresentation() {
    return myName == null ? "_" : myName;
  }

  public boolean isHidden() {
    return false;
  }

  public static List<Referable> getLocalReferables(DefinitionData definitionData, Object anchor) {
    List<Referable> localReferables = new ArrayList<>();
    if (definitionData != null) {
      LocalVariablesCollector collector = new LocalVariablesCollector(anchor);
      definitionData.definition().accept(collector, null);
      @Nullable List<Referable> collectedResult = collector.getResult();
      if (collectedResult != null) localReferables.addAll(collectedResult);
    }
    return localReferables;
  }
}
