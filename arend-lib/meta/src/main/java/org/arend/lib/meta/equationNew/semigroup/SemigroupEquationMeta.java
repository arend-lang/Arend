package org.arend.lib.meta.equationNew.semigroup;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class SemigroupEquationMeta extends NonCommutativeSemigroupEquationMeta {
  @Dependency                       ArendRef            SemigroupSolverModel;
  @Dependency                       CoreClassDefinition Semigroup;
  @Dependency(name = "Semigroup.*") CoreClassField      mul;

  @Override
  protected CoreClassField getMul() {
    return mul;
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return Semigroup;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return SemigroupSolverModel;
  }
}
