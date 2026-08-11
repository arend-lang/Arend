package org.arend.naming.reference;

import java.util.List;

public class LevelDefinition {
  private final List<? extends TCLevelReferable> myReferables;
  private final LocatedReferable myParent;

  public LevelDefinition(List<? extends TCLevelReferable> refs, LocatedReferable parent) {
    myReferables = refs;
    myParent = parent;
  }

  public List<? extends TCLevelReferable> getReferables() {
    return myReferables;
  }

  public LocatedReferable getParent() {
    return myParent;
  }
}
