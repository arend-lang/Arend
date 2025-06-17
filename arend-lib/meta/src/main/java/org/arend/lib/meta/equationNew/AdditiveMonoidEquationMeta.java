package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class AdditiveMonoidEquationMeta extends BaseMonoidEquationMeta {
  @Dependency                           ArendRef AddMonoidSolverModel;
  @Dependency                           CoreClassDefinition AddMonoid;
  @Dependency(name = "AddPointed.zro")  CoreClassField zro;
  @Dependency(name = "AddMonoid.+")     CoreClassField add;

  public AdditiveMonoidEquationMeta() {
    super(false);
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
  protected @NotNull CoreClassDefinition getClassDef() {
    return AddMonoid;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return AddMonoidSolverModel;
  }
}
