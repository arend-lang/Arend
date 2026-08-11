package org.arend.lib.meta.equationNew.semigroup;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class CSemigroupEquationMeta extends CommutativeSemigroupEquationMeta {
  @Dependency                       ArendRef            CSemigroupSolverModel;
  @Dependency                       CoreClassDefinition CSemigroup;
  @Dependency(name = "Semigroup.*") CoreClassField      mul;

  @Override
  protected CoreClassField getMul() {
    return mul;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return CSemigroup;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CSemigroupSolverModel;
  }
}
