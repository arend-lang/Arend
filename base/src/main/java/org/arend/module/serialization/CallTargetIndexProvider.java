package org.arend.module.serialization;

import org.arend.core.definition.Definition;
import org.arend.naming.reference.LocatedReferable;

public interface CallTargetIndexProvider {
  int getDefIndex(Definition definition);
  int getDefIndex(LocatedReferable definition);
}
