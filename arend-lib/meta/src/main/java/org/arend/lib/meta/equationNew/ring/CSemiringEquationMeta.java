package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class CSemiringEquationMeta extends BaseSemiringEquationMeta {
  @Dependency CoreClassDefinition CSemiring;
  @Dependency ArendRef CSemiringSolverModel;

  @Override
  protected boolean isCommutative() {
    return true;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return CSemiring;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CSemiringSolverModel;
  }
}
