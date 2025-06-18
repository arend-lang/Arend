package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class AbelianGroupEquationMeta extends BaseCommutativeGroupEquationMeta {
  @Dependency                             CoreClassDefinition AbGroup;
  @Dependency(name = "AddPointed.zro")    CoreClassField zro;
  @Dependency(name = "AddMonoid.+")       CoreClassField add;
  @Dependency(name = "AddGroup.negative") CoreClassField negative;
  @Dependency                             ArendRef AbGroupSolverModel;

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return AbGroup;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return AbGroupSolverModel;
  }

  @Override
  protected boolean isMultiplicative() {
    return false;
  }

  @Override
  protected CoreClassField getIde() {
    return zro;
  }

  @Override
  protected CoreClassField getMul() {
    return add;
  }

  @Override
  protected CoreClassField getInverse() {
    return negative;
  }
}
